package com.resolum.alivia.operations;

import com.resolum.alivia.operations.application.acl.OperationsContextFacadeImpl;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade.ScheduledVisit;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationsContextFacadeTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);

    @Test
    void reportsTheFurthestScheduledVisitOfEachRequest() {
        var appointments = mock(AppointmentRepository.class);
        var ids = List.of("a", "b", "c");
        when(appointments.findByDraftIdInAndStatusIn(ids, List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED))).thenReturn(List.of(
                order("a", ServiceType.EVALUATION, DAY, 8), order("b", ServiceType.INSTALLATION, DAY.plusDays(2), 8),
                order("b", ServiceType.EVALUATION, DAY, 10)));
        var facade = new OperationsContextFacadeImpl(appointments);

        var visits = facade.fetchScheduledVisitsOfDrafts(ids);

        assertEquals(Map.of("a", new ScheduledVisit("EVALUATION", DAY, false),
                "b", new ScheduledVisit("INSTALLATION", DAY.plusDays(2), false)), visits);
        assertTrue(facade.fetchScheduledVisitsOfDrafts(List.of()).isEmpty());
    }

    private static Appointment order(String draftId, ServiceType type, LocalDate date, int hour) {
        var hours = (int) type.window().toHours();
        return new Appointment(draftId, type, date, new TimeRange(LocalTime.of(hour, 0), LocalTime.of(hour + hours, 0)),
                7L, "Ana Pérez", "María Villanueva", "984123456", null, Instant.parse("2026-11-02T15:00:00Z"));
    }
}
