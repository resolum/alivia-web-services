package com.resolum.alivia.operations.application.acl;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OperationsContextFacadeImpl implements OperationsContextFacade {
    private final AppointmentRepository appointments;

    public OperationsContextFacadeImpl(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, ScheduledVisit> fetchScheduledVisitsOfDrafts(Collection<String> draftIds) {
        if (draftIds == null || draftIds.isEmpty()) return Map.of();
        return appointments.findByDraftIdInAndStatus(draftIds, AppointmentStatus.SCHEDULED).stream()
                .collect(Collectors.groupingBy(Appointment::getDraftId, Collectors.collectingAndThen(
                        // INSTALLATION is declared after EVALUATION, so the highest ordinal is the furthest stage.
                        Collectors.maxBy(Comparator.comparing(Appointment::getServiceType)),
                        order -> order.map(visit -> new ScheduledVisit(visit.getServiceType().name(),
                                visit.getScheduledDate())).orElseThrow())));
    }
}
