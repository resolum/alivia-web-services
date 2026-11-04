package com.resolum.alivia.subscriptions.application.internal.queryservices;

import com.resolum.alivia.subscriptions.application.internal.outboundservices.acl.ExternalOperationsService;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.queries.GetContractingTrackingQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetMyContractingQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.MyContracting;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingStage;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingTracking;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.TechnicalVisit;
import com.resolum.alivia.subscriptions.domain.services.ContractingTrackingQueryService;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Builds the family's view of its contracting request from the draft and the technical visits that Operations
 * keeps for it.
 */
@Service
public class ContractingTrackingQueryServiceImpl implements ContractingTrackingQueryService {
    private static final String NOT_VIABLE = "NOT_VIABLE";
    private static final String APPROVED = "APPROVED";

    private final ConfigurationDraftRepository drafts;
    private final PlanQueryService plans;
    private final ExternalOperationsService operations;

    public ContractingTrackingQueryServiceImpl(ConfigurationDraftRepository drafts, PlanQueryService plans,
                                               ExternalOperationsService operations) {
        this.drafts = drafts;
        this.plans = plans;
        this.operations = operations;
    }

    @Override
    @Transactional(readOnly = true)
    public ContractingTracking handle(GetContractingTrackingQuery query) {
        var draft = drafts.findById(query.draftId()).orElseThrow(() -> new ConfigurationDraftNotFound(query.draftId()));

        var visits = operations.fetchContractingVisits(draft.getId());
        var evaluation = visitOf(visits, TechnicalVisit.EVALUATION);
        var installation = visitOf(visits, TechnicalVisit.INSTALLATION);
        var plan = plans.handle(new GetPlanByCodeQuery(draft.getPlanCode()));
        var quote = quoteOf(draft);
        var devices = draft.getRooms().stream().mapToInt(room -> room.actuatorCount() + room.getMicrophones()).sum();
        return new ContractingTracking(draft.getId(), stageOf(draft, evaluation, installation), draft.getPlanCode(),
                plan.getName(), draft.getRooms().size(), devices, quote.map(PlanQuote::totalMonthlyAmount).orElse(null),
                quote.map(PlanQuote::currency).orElse("PEN"), evaluation, installation, draft.getConfirmedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MyContracting> handle(GetMyContractingQuery query) {
        if (query.userId() == null) return Optional.empty();
        // A family may have started another request after paying (choosing a plan again before signing in):
        // the most advanced one decides, so an active service is never hidden behind a newer, unpaid draft.
        var requests = drafts.findByOwnerUserIdOrderByUpdatedAtDesc(query.userId());
        var paid = requests.stream().filter(ConfigurationDraft::isPaid).map(this::myContractingOf).toList();
        return paid.stream().filter(MyContracting::isActive).findFirst()
                .or(() -> paid.stream().filter(mine -> !ContractingStage.NOT_VIABLE.name().equals(mine.stage())).findFirst())
                .or(() -> requests.stream().filter(draft -> !draft.isPaid()).findFirst().map(this::myContractingOf))
                .or(() -> paid.stream().findFirst());
    }

    /** A request still in configuration until its payment is held; afterwards, the stage of its tracking. */
    MyContracting myContractingOf(ConfigurationDraft draft) {
        var planCode = draft.getPlanCode().name();
        if (!draft.isPaid()) {
            return new MyContracting(draft.getId(), MyContracting.CONFIGURATION, planCode, draft.getBillingCycle(), false);
        }
        var visits = operations.fetchContractingVisits(draft.getId());
        var stage = stageOf(draft, visitOf(visits, TechnicalVisit.EVALUATION), visitOf(visits, TechnicalVisit.INSTALLATION));
        return new MyContracting(draft.getId(), stage.name(), planCode, draft.getBillingCycle(),
                draft.getPaymentStatus() == com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus.SUCCEEDED);
    }

    /**
     * The technician moves the request from evaluation to confirmation when he records a viable home; the family
     * moves it to installation by confirming, and an approved installation leaves only the activation.
     */
    static ContractingStage stageOf(ConfigurationDraft draft, TechnicalVisit evaluation, TechnicalVisit installation) {
        if (installation != null && installation.completed() && APPROVED.equals(installation.result())) {
            return ContractingStage.ACTIVATION;
        }
        if (evaluation == null || !evaluation.completed()) return ContractingStage.EVALUATION;
        if (NOT_VIABLE.equals(evaluation.result())) return ContractingStage.NOT_VIABLE;
        return draft.isConfirmed() ? ContractingStage.INSTALLATION : ContractingStage.CONFIRMATION;
    }

    private static TechnicalVisit visitOf(List<TechnicalVisit> visits, String serviceType) {
        return visits.stream().filter(visit -> serviceType.equals(visit.serviceType())).findFirst().orElse(null);
    }

    /** The quote fails while the configuration breaks the plan limits; the tracking then shows no amount. */
    private Optional<PlanQuote> quoteOf(ConfigurationDraft draft) {
        try {
            return Optional.of(plans.handle(new QuotePlanQuery(draft.getPlanCode(), draft.getRooms().size(),
                    draft.actuatorQuantities())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
