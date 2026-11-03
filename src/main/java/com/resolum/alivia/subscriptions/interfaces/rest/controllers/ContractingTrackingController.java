package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.domain.model.commands.ConfirmConfigurationCommand;
import com.resolum.alivia.subscriptions.domain.model.queries.GetContractingTrackingQuery;
import com.resolum.alivia.subscriptions.domain.services.ContractingCommandService;
import com.resolum.alivia.subscriptions.domain.services.ContractingTrackingQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.ContractingTrackingResource;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.ContractingTrackingResourceFromValueAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/configuration-drafts/{draftId}", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Contrataciones", description = "Seguimiento de las solicitudes desde el plan hasta la activación del servicio")
public class ContractingTrackingController {
    private final ContractingTrackingQueryService queryService;
    private final ContractingCommandService commandService;

    public ContractingTrackingController(ContractingTrackingQueryService queryService,
                                         ContractingCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/tracking")
    @Operation(summary = "Seguimiento de la contratación para la familia",
            description = "Etapa (EVALUATION, CONFIRMATION, INSTALLATION, ACTIVATION o NOT_VIABLE), la visita de "
                    + "evaluación y de instalación con su técnico, el resultado de la evaluación, el plan y el monto mensual.")
    public ContractingTrackingResource tracking(@PathVariable String draftId) {
        var tracking = queryService.handle(new GetContractingTrackingQuery(draftId));
        return ContractingTrackingResourceFromValueAssembler.toResource(tracking, HiringRequestsController.codeOf(draftId));
    }

    @PostMapping("/confirmation")
    @Operation(summary = "Revisar y confirmar la configuración validada por el técnico",
            description = "Solo cuando la etapa es CONFIRMATION; pasa la contratación a INSTALLATION. Si ya estaba "
                    + "confirmada devuelve el seguimiento actual; antes de la evaluación viable responde 409.")
    public ContractingTrackingResource confirm(@PathVariable String draftId) {
        var tracking = commandService.handle(new ConfirmConfigurationCommand(draftId));
        return ContractingTrackingResourceFromValueAssembler.toResource(tracking, HiringRequestsController.codeOf(draftId));
    }
}
