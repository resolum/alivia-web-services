package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.exceptions.EdgeTableVersionAhead;
import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import com.resolum.alivia.assets.domain.model.commands.RecordEdgeSeenCommand;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeTableVersionByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeTableVersion;
import com.resolum.alivia.assets.domain.services.EdgeSyncCommandService;
import com.resolum.alivia.assets.domain.services.EdgeTableWaitService;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryQueryService;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeDeviceTableResource;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeDeviceTableResourceFromViewAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeIdFromAuthenticationAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.TableEntityTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * What an edge service asks for when it starts and whenever it reconnects: the table of devices of its household.
 *
 * <p>The edge never says which installation it is: the credential it authenticated with names the edge, and the table
 * is the one of the installation where that edge is assigned. The table is a complete snapshot and its {@code ETag} is
 * {@code "<installationId>.<version>"} ({@link TableEntityTag}); an edge that sends the tag it already has gets an empty
 * {@code 304}. A tag of another installation never matches, so an edge that was moved to another household gets the table
 * of the new one. A tag of this installation with a higher version means the database went back in time: the answer is
 * {@code 409 table_version_ahead} and the edge asks again without a tag and takes whatever table it receives.</p>
 *
 * <p>With {@code ?wait=n} an edge that is up to date does not get the {@code 304} at once: the request waits, holding no
 * thread of the server, until the table passes the version the edge sent or {@code n} seconds (at most
 * {@value #MAX_WAIT_SECONDS}) go by, and then the answer is the new table or the {@code 304}. A request that is not up to
 * date is answered at once, so the wait is only ever an acceleration of the same read.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/edge/device-table", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Edge: tabla de dispositivos", description = "Tabla de dispositivos del hogar para los edge services (autenticados con su credencial)")
// IAM gives the authenticated edge services these roles; no user has them.
@PreAuthorize("hasAnyRole('EDGE_MICROPHONE', 'EDGE_ACTUATOR')")
public class EdgeDeviceTableController {
    private static final Logger log = LoggerFactory.getLogger(EdgeDeviceTableController.class);

    /** Longest a request waits. Proxies and balancers in front of the API must allow a little more than this. */
    static final int MAX_WAIT_SECONDS = 25;

    private final HouseholdDeviceRegistryQueryService queryService;
    private final EdgeSyncCommandService syncService;
    private final EdgeTableWaitService waitService;

    public EdgeDeviceTableController(HouseholdDeviceRegistryQueryService queryService,
                                     EdgeSyncCommandService syncService, EdgeTableWaitService waitService) {
        this.queryService = queryService;
        this.syncService = syncService;
        this.waitService = waitService;
    }

    @GetMapping
    @Operation(summary = "Consultar la tabla de dispositivos del hogar",
            description = "Devuelve la tabla completa de dispositivos de la instalación donde está asignado el edge que llama, "
                    + "con su ETag (\"<instalación>.<versión>\"). Si el edge envía If-None-Match con el ETag vigente, responde 304 sin "
                    + "cuerpo. El edge reemplaza su copia si la instalación es distinta o la versión es mayor. Si envía un ETag de esta "
                    + "instalación con una versión mayor que la vigente (la base de datos retrocedió, por ejemplo al restaurar una "
                    + "copia), responde 409 table_version_ahead: el edge debe pedir la tabla sin If-None-Match y quedarse con la que "
                    + "reciba, aunque su versión sea menor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tabla completa, con ETag de su versión"),
            @ApiResponse(responseCode = "304", description = "El edge ya tiene esta versión"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida, desconocida o revocada"),
            @ApiResponse(responseCode = "403", description = "El llamante no es un edge service"),
            @ApiResponse(responseCode = "404", description = "El edge no está asignado a una instalación activa"),
            @ApiResponse(responseCode = "409", description = "El edge dice tener una versión mayor que la vigente (table_version_ahead)")
    })
    public ResponseEntity<EdgeDeviceTableResource> getDeviceTable(
            @Parameter(description = "ETag de la tabla que el edge ya tiene, entre comillas") @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @Parameter(hidden = true) Authentication authentication) {
        var edgeId = EdgeIdFromAuthenticationAssembler.toEdgeId(authentication);

        // The version alone settles an edge that is up to date, without reading any entry of the table.
        var current = queryService.handle(new GetEdgeTableVersionByEdgeIdQuery(edgeId))
                .orElseThrow(() -> new EdgeWithoutInstallation(edgeId));
        recordSeen(edgeId, current.installationId());
        return switch (relationOf(ifNoneMatch, current)) {
            case UP_TO_DATE -> notModified(current);
            case DIFFERENT -> tableOf(edgeId);
            case AHEAD -> throw new EdgeTableVersionAhead(edgeId, current.version());
        };
    }

    @GetMapping(params = "wait")
    @Operation(summary = "Esperar a que cambie la tabla de dispositivos del hogar",
            description = "Igual que la consulta normal, pero si el edge ya tiene la versión vigente (If-None-Match) la petición "
                    + "espera, sin ocupar un hilo del servidor, hasta que exista una versión mayor o pasen los segundos pedidos "
                    + "(máximo 25; un valor mayor se recorta). Responde 200 con la tabla nueva y su ETag en cuanto cambia, o 304 "
                    + "si se agota el tiempo. Si el edge no tiene el ETag vigente, responde al instante con la tabla; si tiene uno "
                    + "mayor de esta instalación, 409 table_version_ahead. Es una aceleración de la misma lectura: si el aviso se "
                    + "perdiera, el cambio llegaría en la siguiente petición. Una nueva espera del mismo edge, o revocar o "
                    + "reemplazar su credencial, termina la espera anterior con 304.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tabla completa, con ETag de su versión"),
            @ApiResponse(responseCode = "304", description = "Se agotó el tiempo sin cambios, o el edge ya tiene esta versión"),
            @ApiResponse(responseCode = "400", description = "wait no es un número entero o es negativo"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida, desconocida o revocada"),
            @ApiResponse(responseCode = "403", description = "El llamante no es un edge service"),
            @ApiResponse(responseCode = "404", description = "El edge no está (o dejó de estar) asignado a una instalación activa"),
            @ApiResponse(responseCode = "409", description = "El edge dice tener una versión mayor que la vigente (table_version_ahead)")
    })
    public CompletableFuture<ResponseEntity<EdgeDeviceTableResource>> awaitDeviceTable(
            @Parameter(description = "ETag de la tabla que el edge ya tiene, entre comillas") @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @Parameter(description = "Segundos que puede esperar la petición (0 a 25)") @RequestParam("wait") int waitSeconds,
            @Parameter(hidden = true) Authentication authentication) {
        if (waitSeconds < 0) {
            throw new IllegalArgumentException("The wait cannot be negative.");
        }
        var edgeId = EdgeIdFromAuthenticationAssembler.toEdgeId(authentication);

        var current = queryService.handle(new GetEdgeTableVersionByEdgeIdQuery(edgeId))
                .orElseThrow(() -> new EdgeWithoutInstallation(edgeId));
        recordSeen(edgeId, current.installationId());
        switch (relationOf(ifNoneMatch, current)) {
            case AHEAD -> throw new EdgeTableVersionAhead(edgeId, current.version());
            case DIFFERENT -> {
                return CompletableFuture.completedFuture(tableOf(edgeId));
            }
            case UP_TO_DATE -> {
                // falls through to the wait
            }
        }
        var wait = Duration.ofSeconds(Math.min(waitSeconds, MAX_WAIT_SECONDS));
        if (wait.isZero()) {
            return CompletableFuture.completedFuture(notModified(current));
        }
        return waitService.awaitNewerTable(edgeId, current.installationId(), current.version(), wait)
                .thenApply(newer -> newer.map(EdgeDeviceTableController::ok)
                        .orElseGet(() -> notModified(current)));
    }

    private static TableEntityTag.Relation relationOf(String ifNoneMatch, EdgeTableVersion current) {
        return TableEntityTag.relation(ifNoneMatch, current.installationId(), current.version());
    }

    /** The table carries its own version, which may be newer than the one checked before: it is what the ETag says. */
    private ResponseEntity<EdgeDeviceTableResource> tableOf(Long edgeId) {
        var table = queryService.handle(new GetEdgeAccessTableByEdgeIdQuery(edgeId))
                .orElseThrow(() -> new EdgeWithoutInstallation(edgeId));
        return ok(table);
    }

    private static ResponseEntity<EdgeDeviceTableResource> ok(EdgeAccessTable table) {
        return ResponseEntity.ok().eTag(TableEntityTag.of(table.installationId(), table.version()))
                .cacheControl(CacheControl.noStore())
                .body(EdgeDeviceTableResourceFromViewAssembler.toResourceFromView(table));
    }

    private static ResponseEntity<EdgeDeviceTableResource> notModified(EdgeTableVersion current) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(TableEntityTag.of(current.installationId(), current.version()))
                .cacheControl(CacheControl.noStore()).build();
    }

    /**
     * That the edge asked is bookkeeping for the business: failing to write it must never keep the edge from its table.
     */
    private void recordSeen(Long edgeId, Long installationId) {
        try {
            syncService.handle(new RecordEdgeSeenCommand(edgeId, installationId));
        } catch (RuntimeException exception) {
            log.warn("Could not record that edge {} asked for its table: {}", edgeId, exception.toString());
        }
    }
}
