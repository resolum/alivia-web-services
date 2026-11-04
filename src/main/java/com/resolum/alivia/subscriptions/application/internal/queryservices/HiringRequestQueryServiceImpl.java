package com.resolum.alivia.subscriptions.application.internal.queryservices;

import com.resolum.alivia.subscriptions.application.internal.outboundservices.acl.ExternalOperationsService;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.queries.GetHiringRequestsQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringRequest;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringStage;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;
import com.resolum.alivia.subscriptions.domain.services.HiringRequestQueryService;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the hiring pipeline from the configuration drafts.
 *
 * <p>A draft enters the pipeline once the family has configured at least one room. It stays in
 * "request and payment" until Operations schedules its technical evaluation, and moves to installation
 * when the installation is scheduled; both are read from Operations through its anti-corruption layer.</p>
 */
@Service
public class HiringRequestQueryServiceImpl implements HiringRequestQueryService {
    private final ConfigurationDraftRepository drafts;
    private final PlanQueryService plans;
    private final ExternalOperationsService operations;

    public HiringRequestQueryServiceImpl(ConfigurationDraftRepository drafts, PlanQueryService plans,
                                         ExternalOperationsService operations) {
        this.drafts = drafts;
        this.plans = plans;
        this.operations = operations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HiringRequest> handle(GetHiringRequestsQuery query) {
        var configured = drafts.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(draft -> !draft.getRooms().isEmpty())
                .toList();
        var stages = operations.fetchTechnicalStages(configured.stream().map(ConfigurationDraft::getId).toList());
        return configured.stream()
                .map(draft -> toRequest(draft, stages))
                .filter(request -> query.stage() == null || request.stage() == query.stage())
                .toList();
    }

    private HiringRequest toRequest(ConfigurationDraft draft, Map<String, HiringStage> stages) {
        var plan = plans.handle(new GetPlanByCodeQuery(draft.getPlanCode()));
        var quote = quoteOf(draft);
        var devices = draft.getRooms().stream()
                .mapToInt(room -> room.actuatorCount() + room.getMicrophones()).sum();
        return new HiringRequest(draft.getId(), draft.getPlanCode(), plan.getName(), draft.getBillingCycle(),
                draft.getRooms().size(), devices, stages.getOrDefault(draft.getId(), HiringStage.REQUEST_AND_PAYMENT),
                draft.getPaymentStatus(),
                quote.map(PlanQuote::totalMonthlyAmount).orElse(null),
                quote.map(PlanQuote::currency).orElse("PEN"), draft.getPreauthorizedAt(), draft.getCaptureBefore(),
                draft.getUpdatedAt());
    }

    /** The quote fails while the configuration breaks the plan limits; the request then shows no amount. */
    private Optional<PlanQuote> quoteOf(ConfigurationDraft draft) {
        try {
            return Optional.of(plans.handle(new QuotePlanQuery(draft.getPlanCode(), draft.getRooms().size(),
                    draft.actuatorQuantities())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
