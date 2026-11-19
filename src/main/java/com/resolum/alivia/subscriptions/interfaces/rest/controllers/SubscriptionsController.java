package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.commands.RequestPlanChangeCommand;
import com.resolum.alivia.subscriptions.domain.model.commands.UpdateSubscriptionStatusCommand;
import com.resolum.alivia.subscriptions.domain.model.queries.GetActiveSubscriptionByUserIdQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.SubscriptionStatus;
import com.resolum.alivia.subscriptions.domain.services.SubscriptionCommandService;
import com.resolum.alivia.subscriptions.domain.services.SubscriptionQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.ChangeSubscriptionStatusResource;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.RequestPlanChangeResource;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.SubscriptionResource;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.SubscriptionResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/subscriptions", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Subscriptions", description = "Endpoints for managing running subscriptions and payments")
public class SubscriptionsController {

    private final SubscriptionQueryService queryService;
    private final SubscriptionCommandService commandService;
    private final PaymentRepository paymentRepository;
    private final IamContextFacade iamContextFacade;
    private final ProfilesContextFacade profilesContextFacade;
    private final ConfigurationDraftRepository configurationDraftRepository;

    public SubscriptionsController(SubscriptionQueryService queryService,
                                   SubscriptionCommandService commandService,
                                   PaymentRepository paymentRepository) {
        this(queryService, commandService, paymentRepository, null, null, null);
    }

    @Autowired
    public SubscriptionsController(SubscriptionQueryService queryService,
                                   SubscriptionCommandService commandService,
                                   PaymentRepository paymentRepository,
                                   IamContextFacade iamContextFacade,
                                   ProfilesContextFacade profilesContextFacade,
                                   ConfigurationDraftRepository configurationDraftRepository) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.paymentRepository = paymentRepository;
        this.iamContextFacade = iamContextFacade;
        this.profilesContextFacade = profilesContextFacade;
        this.configurationDraftRepository = configurationDraftRepository;
    }

    private Long resolveUserId(Principal principal) {
        if (principal != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        return 1L;
    }

    private String resolveHolderName(Long userId) {
        if (userId == null || iamContextFacade == null || profilesContextFacade == null) {
            return null;
        }
        try {
            var profileIds = iamContextFacade.fetchPersonProfileIdsByUserIds(Set.of(userId));
            Long profileId = profileIds.get(userId);
            if (profileId == null) {
                return null;
            }
            return profilesContextFacade.fetchPersonProfileFullNameById(profileId).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer resolveRoomsCount(Subscription subscription) {
        if (subscription == null || subscription.getDraftId() == null || configurationDraftRepository == null) {
            return null;
        }
        return configurationDraftRepository.findById(subscription.getDraftId())
                .map(draft -> draft.getRooms() != null ? draft.getRooms().size() : null)
                .orElse(null);
    }

    private SubscriptionResource buildResource(Subscription subscription, Long userId) {
        var payment = paymentRepository.findFirstBySubscription_IdOrderByIdDesc(subscription.getId()).orElse(null);
        String holderName = resolveHolderName(userId);
        Integer roomsCount = resolveRoomsCount(subscription);
        return SubscriptionResourceAssembler.toResource(subscription, payment, holderName, roomsCount);
    }

    @GetMapping("/current")
    @Operation(summary = "Get the active running subscription of the current user")
    public ResponseEntity<SubscriptionResource> getCurrent(Principal principal) {
        Long userId = resolveUserId(principal);
        var subscriptionOpt = queryService.handle(new GetActiveSubscriptionByUserIdQuery(userId));
        if (subscriptionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildResource(subscriptionOpt.get(), userId));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update subscription status (active, suspended, cancelled)")
    public ResponseEntity<SubscriptionResource> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeSubscriptionStatusResource resource,
            Principal principal) {
        Long userId = resolveUserId(principal);
        SubscriptionStatus targetStatus = switch (resource.status().toLowerCase()) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "suspended" -> SubscriptionStatus.PAST_DUE;
            case "cancelled" -> SubscriptionStatus.CANCELED;
            default -> throw new IllegalArgumentException("Unknown subscription status: " + resource.status());
        };
        var updated = commandService.handle(new UpdateSubscriptionStatusCommand(id, userId, targetStatus));
        return ResponseEntity.ok(buildResource(updated, userId));
    }

    @PostMapping("/{id}/plan-change")
    @Operation(summary = "Request a plan change on next renewal")
    public ResponseEntity<SubscriptionResource> requestPlanChange(
            @PathVariable Long id,
            @Valid @RequestBody RequestPlanChangeResource resource,
            Principal principal) {
        Long userId = resolveUserId(principal);
        var updated = commandService.handle(new RequestPlanChangeCommand(id, userId, resource.planCode()));
        return ResponseEntity.ok(buildResource(updated, userId));
    }

    @GetMapping("/{id}/receipt")
    @Operation(summary = "Get latest payment receipt url")
    public ResponseEntity<Map<String, String>> getReceipt(@PathVariable Long id) {
        var payment = paymentRepository.findFirstBySubscription_IdOrderByIdDesc(id).orElse(null);
        String url = payment != null && payment.getReceiptUrl() != null ? payment.getReceiptUrl() : "";
        return ResponseEntity.ok(Map.of("receiptUrl", url));
    }
}
