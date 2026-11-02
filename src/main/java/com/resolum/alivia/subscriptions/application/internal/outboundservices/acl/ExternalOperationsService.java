package com.resolum.alivia.subscriptions.application.internal.outboundservices.acl;

import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringStage;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Subscriptions' view of Operations: which stage of the hiring pipeline the technical orders put a request in. */
@Service("subscriptionsExternalOperationsService")
public class ExternalOperationsService {
    private final OperationsContextFacade operationsContextFacade;

    public ExternalOperationsService(OperationsContextFacade operationsContextFacade) {
        this.operationsContextFacade = operationsContextFacade;
    }

    /**
     * Stage of each request with a technical order; an approved installation completes the hiring. The others
     * stay in request and payment.
     */
    public Map<String, HiringStage> fetchTechnicalStages(Collection<String> draftIds) {
        return operationsContextFacade.fetchScheduledVisitsOfDrafts(draftIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toStage(entry.getValue())));
    }

    private static HiringStage toStage(OperationsContextFacade.ScheduledVisit visit) {
        if (visit.installationApproved()) return HiringStage.COMPLETED;
        return "INSTALLATION".equals(visit.serviceType()) ? HiringStage.INSTALLATION : HiringStage.TECHNICAL_EVALUATION;
    }
}
