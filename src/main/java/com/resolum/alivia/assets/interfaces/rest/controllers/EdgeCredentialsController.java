package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeCredentialStatusQuery;
import com.resolum.alivia.assets.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.assets.domain.services.EdgeCredentialQueryService;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeCredentialStatusResource;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeCredentialStatusResourceFromViewAssembler;
import com.resolum.alivia.assets.interfaces.rest.resources.IssuedEdgeCredentialResource;
import com.resolum.alivia.assets.interfaces.rest.transform.IssuedEdgeCredentialResourceFromViewAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/edges/{edgeId}/credential", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Credenciales de edge", description = "Credencial con la que cada edge service se identifica ante la API")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class EdgeCredentialsController {
    private final EdgeCredentialCommandService credentialCommandService;
    private final EdgeCredentialQueryService credentialQueryService;

    public EdgeCredentialsController(EdgeCredentialCommandService credentialCommandService,
                                     EdgeCredentialQueryService credentialQueryService) {
        this.credentialCommandService = credentialCommandService;
        this.credentialQueryService = credentialQueryService;
    }

    @PostMapping
    @Operation(summary = "Emitir o rotar la credencial de un edge",
            description = "Genera un secreto nuevo para el edge (el de micrófono y el de actuadores tienen cada uno el suyo). "
                    + "Si ya tenía credencial, el secreto anterior deja de valer de inmediato. El secreto solo se muestra en esta respuesta.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Credencial emitida"),
            @ApiResponse(responseCode = "404", description = "El edge no existe"),
            @ApiResponse(responseCode = "409", description = "El edge está retirado, o otra petición emitió su primera credencial al mismo tiempo")
    })
    public ResponseEntity<IssuedEdgeCredentialResource> issueCredential(@PathVariable Long edgeId,
                                                                        @Parameter(hidden = true) Authentication authentication) {
        var issued = credentialCommandService.handle(
                new IssueEdgeCredentialCommand(edgeId, Long.valueOf(authentication.getName())));
        // The response carries a secret: no cache or proxy may keep it.
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(IssuedEdgeCredentialResourceFromViewAssembler.toResourceFromView(issued));
    }

    @DeleteMapping
    @Operation(summary = "Revocar la credencial de un edge",
            description = "El edge deja de poder identificarse. Es idempotente: revocar un edge sin credencial, o ya revocada, también responde 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Credencial revocada"),
            @ApiResponse(responseCode = "404", description = "El edge no existe")
    })
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeCredential(@PathVariable Long edgeId, @Parameter(hidden = true) Authentication authentication) {
        credentialCommandService.handle(new RevokeEdgeCredentialCommand(edgeId, Long.valueOf(authentication.getName())));
    }

    @GetMapping
    @Operation(summary = "Consultar el estado de la credencial de un edge",
            description = "Quién emitió la credencial vigente y cuándo, su antigüedad en días, si conviene rotarla (activa y más antigua "
                    + "que la edad configurada, un año por defecto), cuándo se usó por última vez y, si se revocó, quién y por qué. "
                    + "Nunca devuelve el secreto. Un edge sin credencial responde 200 con estado NOT_ISSUED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de la credencial"),
            @ApiResponse(responseCode = "404", description = "El edge no existe")
    })
    public EdgeCredentialStatusResource getCredentialStatus(@PathVariable Long edgeId) {
        return EdgeCredentialStatusResourceFromViewAssembler.toResourceFromView(
                credentialQueryService.handle(new GetEdgeCredentialStatusQuery(edgeId)));
    }
}
