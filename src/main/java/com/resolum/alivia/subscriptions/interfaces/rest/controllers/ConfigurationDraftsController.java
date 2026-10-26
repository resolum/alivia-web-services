package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.application.internal.commandservices.ConfigurationDraftService;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.ConfigurationDraftResource;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.PlanQuoteResource;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.PlanQuoteResourceFromResultsAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/configuration-drafts", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Configuración de contratación",
        description = "Gestiona el borrador de habitaciones y dispositivos del paso 4 del flujo de contratación")
public class ConfigurationDraftsController {
    private final ConfigurationDraftService drafts;
    private final PlanQueryService plans;

    public ConfigurationDraftsController(ConfigurationDraftService drafts, PlanQueryService plans) {
        this.drafts = drafts;
        this.plans = plans;
    }

    @Schema(name = "CrearBorradorConfiguracion",
            description = "Datos iniciales para crear un borrador asociado a un plan vigente")
    public record CreateDraftRequest(
            @Schema(description = "Código del plan seleccionado", example = "CUSTOM",
                    allowableValues = {"MINIMUM", "CUSTOM"})
            String planCode,
            @Schema(description = "Periodicidad seleccionada para la contratación", example = "MONTHLY",
                    allowableValues = {"MONTHLY", "ANNUAL"})
            String billingCycle) {
    }

    @Schema(name = "HabitacionConfiguradaSolicitud",
            description = "Habitación y cantidades de dispositivos que se guardarán en el borrador")
    public record RoomRequest(
            @Schema(description = "Identificador estable de la habitación", example = "main-bedroom")
            String key,
            @Schema(description = "Nombre visible y personalizable de la habitación", example = "Dormitorio principal")
            String name,
            @Schema(description = "Cantidad de actuadores de iluminación", example = "2", minimum = "0")
            int lights,
            @Schema(description = "Cantidad de actuadores de puerta", example = "1", minimum = "0")
            int doors,
            @Schema(description = "Cantidad de actuadores de ventana", example = "0", minimum = "0")
            int windows,
            @Schema(description = "Cantidad de actuadores de cortina", example = "0", minimum = "0")
            int curtains,
            @Schema(description = "Micrófono asistencial incluido; debe ser uno por habitación", example = "1",
                    allowableValues = {"1"})
            int microphones) {
        ConfiguredRoom toDomain() {
            return new ConfiguredRoom(key, name, lights, doors, windows, curtains, microphones);
        }
    }

    @Schema(name = "ActualizarHabitacionesConfiguracion",
            description = "Lista completa de habitaciones que reemplazará la configuración actual del borrador")
    public record ReplaceRoomsRequest(
            @Schema(description = "Habitaciones configuradas; se admite un máximo de diez")
            List<RoomRequest> rooms) {
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un borrador de configuración",
            description = "Inicia el paso de configuración para el Plan Base o Personalizado. "
                    + "El borrador no activa una suscripción ni realiza cobros.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Borrador creado",
                    content = @Content(schema = @Schema(implementation = ConfigurationDraftResource.class))),
            @ApiResponse(responseCode = "400", description = "Código de plan o periodicidad inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "El plan solicitado no está disponible",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ConfigurationDraftResource create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    description = "Plan y periodicidad que originan el borrador",
                    content = @Content(schema = @Schema(implementation = CreateDraftRequest.class)))
            @RequestBody CreateDraftRequest request) {
        var code = PlanCode.from(request.planCode());
        plans.handle(new GetPlanByCodeQuery(code));
        return toResource(drafts.create(code, request.billingCycle()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un borrador de configuración",
            description = "Recupera las habitaciones, dispositivos y la cotización mensual calculada. "
                    + "La cotización es nula mientras la selección aún no cumpla las reglas del plan.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador encontrado",
                    content = @Content(schema = @Schema(implementation = ConfigurationDraftResource.class))),
            @ApiResponse(responseCode = "404", description = "No existe el borrador indicado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ConfigurationDraftResource get(
            @Parameter(description = "UUID del borrador", example = "5cf5d80a-4f88-44ae-9ccb-642c68c065ab")
            @PathVariable String id) {
        return toResource(drafts.find(id));
    }

    @PutMapping(value = "/{id}/rooms", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Guardar habitaciones y dispositivos",
            description = "Reemplaza de forma atómica la distribución del borrador. Valida habitaciones duplicadas, "
                    + "micrófono incluido y el límite de actuadores del plan; luego recalcula la cotización.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración guardada y cotización actualizada",
                    content = @Content(schema = @Schema(implementation = ConfigurationDraftResource.class))),
            @ApiResponse(responseCode = "400", description = "Habitaciones o cantidades incompatibles con el plan",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe el borrador indicado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ConfigurationDraftResource replaceRooms(
            @Parameter(description = "UUID del borrador", example = "5cf5d80a-4f88-44ae-9ccb-642c68c065ab")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    description = "Distribución completa de habitaciones y dispositivos",
                    content = @Content(schema = @Schema(implementation = ReplaceRoomsRequest.class)))
            @RequestBody ReplaceRoomsRequest request) {
        if (request.rooms() == null) throw new IllegalArgumentException("Las habitaciones son obligatorias.");
        return toResource(drafts.replaceRooms(id, request.rooms().stream().map(RoomRequest::toDomain).toList()));
    }

    private ConfigurationDraftResource toResource(ConfigurationDraft draft) {
        PlanQuoteResource quote = null;
        if (!draft.getRooms().isEmpty()) {
            var plan = plans.handle(new GetPlanByCodeQuery(draft.getPlanCode()));
            var actuatorTotal = draft.actuatorQuantities().toSelection().total();
            if (draft.getRooms().size() >= plan.getMinimumRooms()
                    && actuatorTotal >= plan.getMinActuators() && actuatorTotal <= plan.getMaxActuators()) {
                quote = PlanQuoteResourceFromResultsAssembler.toResourceFromResult(
                        plans.handle(new QuotePlanQuery(draft.getPlanCode(), draft.getRooms().size(),
                                draft.actuatorQuantities())));
            }
        }
        return new ConfigurationDraftResource(
                draft.getId(), draft.getPlanCode(), draft.getBillingCycle(),
                draft.getRooms().stream().map(room -> new ConfigurationDraftResource.ConfiguredRoomResource(
                        room.getKey(), room.getName(), room.getLights(), room.getDoors(), room.getWindows(),
                        room.getCurtains(), room.getMicrophones())).toList(),
                quote, draft.getUpdatedAt());
    }
}
