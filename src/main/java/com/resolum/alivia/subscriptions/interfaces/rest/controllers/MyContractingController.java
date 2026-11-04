package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.domain.model.commands.ClaimConfigurationDraftCommand;
import com.resolum.alivia.subscriptions.domain.model.queries.GetMyContractingQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.MyContracting;
import com.resolum.alivia.subscriptions.domain.services.ContractingCommandService;
import com.resolum.alivia.subscriptions.domain.services.ContractingTrackingQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.MyContractingResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** The request of the signed-in family, which decides whether the web opens the platform or the contracting flow. */
@RestController
@RequestMapping(value = "/api/v1/configuration-drafts", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Contrataciones", description = "Seguimiento de las solicitudes desde el plan hasta la activación del servicio")
public class MyContractingController {
    private final ContractingTrackingQueryService queryService;
    private final ContractingCommandService commandService;

    public MyContractingController(ContractingTrackingQueryService queryService, ContractingCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/mine")
    @Operation(summary = "Solicitud de contratación de la cuenta",
            description = "La última solicitud vinculada a la cuenta con su etapa: CONFIGURATION hasta retener el pago, "
                    + "luego la del seguimiento. 404 cuando la cuenta no tiene solicitud.")
    public ResponseEntity<MyContractingResource> mine(Authentication authentication) {
        return queryService.handle(new GetMyContractingQuery(userIdOf(authentication)))
                .map(MyContractingController::toResource)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{draftId}/owner")
    @Operation(summary = "Vincular la solicitud a la cuenta",
            description = "La solicitud se arma antes de crear la cuenta; al iniciar sesión se vincula a ella. "
                    + "Repetirlo es inocuo; si ya pertenece a otra cuenta responde 409.")
    public MyContractingResource claim(@PathVariable String draftId, Authentication authentication) {
        return toResource(commandService.handle(new ClaimConfigurationDraftCommand(draftId, userIdOf(authentication))));
    }

    private static MyContractingResource toResource(MyContracting mine) {
        return new MyContractingResource(mine.draftId(), HiringRequestsController.codeOf(mine.draftId()), mine.stage(),
                mine.isActive(), mine.planCode(), mine.billingCycle());
    }

    static Long userIdOf(Authentication authentication) {
        if (authentication == null) throw new IllegalArgumentException("Authenticated user is required.");
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Authenticated user identifier is invalid.", exception);
        }
    }
}
