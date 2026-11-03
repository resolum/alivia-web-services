package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.domain.model.queries.GetHiringRequestsQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringRequest;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringStage;
import com.resolum.alivia.subscriptions.domain.services.HiringRequestQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.HiringRequestResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/hiring-requests", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Contrataciones", description = "Seguimiento de las solicitudes desde el plan hasta la activación del servicio")
@PreAuthorize("hasAnyRole('SUBSCRIPTION_MANAGER', 'ADMINISTRATOR')")
public class HiringRequestsController {
    private final HiringRequestQueryService queryService;

    public HiringRequestsController(HiringRequestQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Listar las contrataciones en curso, opcionalmente de una etapa")
    public List<HiringRequestResource> list(@RequestParam(name = "stage", required = false) HiringStage stage) {
        return queryService.handle(new GetHiringRequestsQuery(stage)).stream()
                .map(HiringRequestsController::toResource).toList();
    }

    /** Readable code for the cards: "SOL-" and the last eight characters of the draft id. */
    static String codeOf(String draftId) {
        var hex = draftId.replace("-", "");
        return "SOL-" + hex.substring(hex.length() - 8).toUpperCase(Locale.ROOT);
    }

    private static HiringRequestResource toResource(HiringRequest request) {
        return new HiringRequestResource(request.draftId(), codeOf(request.draftId()), request.planCode().name(),
                request.planName(), request.billingCycle(), request.rooms(), request.devices(), request.stage().name(),
                request.paymentStatus() == null ? null : request.paymentStatus().name(), request.heldAmount(),
                request.currency(), request.preauthorizedAt(), request.captureBefore(), request.updatedAt());
    }
}
