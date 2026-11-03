package com.resolum.alivia.operations.application.acl;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OperationsContextFacadeImpl implements OperationsContextFacade {
    /** Visits of the contracting flow; maintenance visits belong to the incidents of a home in service. */
    private static final Set<ServiceType> CONTRACTING_VISITS = Set.of(ServiceType.EVALUATION, ServiceType.INSTALLATION);

    private final AppointmentRepository appointments;

    public OperationsContextFacadeImpl(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, ScheduledVisit> fetchScheduledVisitsOfDrafts(Collection<String> draftIds) {
        if (draftIds == null || draftIds.isEmpty()) return Map.of();
        return appointments.findByDraftIdInAndStatusIn(draftIds,
                        List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED)).stream()
                .filter(order -> CONTRACTING_VISITS.contains(order.getServiceType()))
                .collect(Collectors.groupingBy(Appointment::getDraftId, Collectors.collectingAndThen(
                        // INSTALLATION is declared after EVALUATION, so the highest ordinal is the furthest stage.
                        Collectors.maxBy(Comparator.comparing(Appointment::getServiceType)),
                        order -> order.map(visit -> new ScheduledVisit(visit.getServiceType().name(),
                                visit.getScheduledDate(), visit.getInstallationResult() == InstallationResult.APPROVED)).orElseThrow())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractingVisit> fetchContractingVisitsOfDraft(String draftId) {
        if (draftId == null || draftId.isBlank()) return List.of();
        return appointments.findByDraftIdInAndStatusIn(List.of(draftId),
                        List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED)).stream()
                .filter(order -> CONTRACTING_VISITS.contains(order.getServiceType()))
                .collect(Collectors.toMap(Appointment::getServiceType, order -> order,
                        (first, second) -> second.getId() != null && first.getId() != null
                                && second.getId() > first.getId() ? second : first))
                .values().stream()
                .sorted(Comparator.comparing(Appointment::getServiceType))
                .map(OperationsContextFacadeImpl::toVisit)
                .toList();
    }

    private static ContractingVisit toVisit(Appointment order) {
        var result = order.getServiceType() == ServiceType.EVALUATION ? order.getEvaluationResult() : order.getInstallationResult();
        return new ContractingVisit(order.getServiceType().name(), order.getStatus().name(), order.getScheduledDate(),
                order.getStartTime(), order.getEndTime(), order.getTechnicianName(), order.getDirections(),
                result == null ? null : result.name(), order.getCompletionNotes());
    }
}
