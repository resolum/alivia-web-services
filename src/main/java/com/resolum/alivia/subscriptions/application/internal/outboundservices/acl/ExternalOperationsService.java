package com.resolum.alivia.subscriptions.application.internal.outboundservices.acl;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.ServicedHome;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringStage;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.TechnicalVisit;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
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

    /** Latest evaluation and installation visits of a request, evaluation first. */
    public List<TechnicalVisit> fetchContractingVisits(String draftId) {
        return operationsContextFacade.fetchContractingVisitsOfDraft(draftId).stream()
                .map(visit -> new TechnicalVisit(visit.serviceType(), "COMPLETED".equals(visit.status()), visit.date(),
                        visit.startTime(), visit.endTime(), visit.technicianName(), visit.directions(), visit.result(),
                        visit.notes()))
                .toList();
    }

    /** The family confirmed: the technician who evaluated the home gets the installation order. */
    public void openInstallation(String draftId) {
        operationsContextFacade.openInstallationOfConfirmedRequest(draftId);
    }

    private static HiringStage toStage(OperationsContextFacade.ScheduledVisit visit) {
        if (visit.installationApproved()) return HiringStage.COMPLETED;
        return "INSTALLATION".equals(visit.serviceType()) ? HiringStage.INSTALLATION : HiringStage.TECHNICAL_EVALUATION;
    }

    /** Homes whose installation closed approved, only those the technician served when {@code technicianUserId} is given. */
    public List<ServicedHome> fetchInstalledHomes(Long technicianUserId) {
        return operationsContextFacade.fetchInstalledHomes(technicianUserId).stream()
                .map(home -> new ServicedHome(home.draftId(), null, home.contactName(), home.contactPhone(), home.directions(),
                        home.installedAt(), home.installedBy(),
                        home.devices().stream().map(device -> new ServicedHome.Device(device.alias(), device.macAddress(), device.type())).toList(),
                        home.orders().stream().map(order -> new ServicedHome.Order(order.id(), order.code(), order.serviceType(),
                                order.status(), order.date(), order.startTime(), order.endTime(), order.technicianName(),
                                order.startedAt(), order.completedAt(), order.incidentCode(), order.incidentStatus())).toList()))
                .toList();
    }
}
