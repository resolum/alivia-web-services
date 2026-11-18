package com.resolum.alivia.operations;

import com.resolum.alivia.communications.interfaces.acl.NotificationsContextFacade;
import com.resolum.alivia.hcm.application.acl.HcmContextFacadeImpl;
import com.resolum.alivia.hcm.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import com.resolum.alivia.hcm.interfaces.acl.HcmContextFacade;
import com.resolum.alivia.operations.application.internal.eventhandlers.TechnicalOrderNotificationsHandler;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.events.TechnicalOrderAssignedEvent;
import com.resolum.alivia.operations.domain.model.events.TechnicalOrderAssignedEvent.Change;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TechnicianNotificationsTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

    @Test
    void anOrderTellsWhenItsTechnicianIsNewOrItMoves() {
        var order = installation();
        assertEquals(List.of(Change.ASSIGNED), changesOf(order));

        order.assignTechnician(8L, "Luis Rojas", null, NOW);
        assertEquals(List.of(Change.ASSIGNED, Change.ASSIGNED), changesOf(order));

        order.reassign(new SlotId(DAY.plusDays(1), window(8, 12)), 8L, "Luis Rojas", "lluvia", NOW);
        order.reassign(new SlotId(DAY.plusDays(2), window(8, 12)), 9L, "Rosa Díaz", "descanso", NOW);
        assertEquals(List.of(Change.ASSIGNED, Change.ASSIGNED, Change.RESCHEDULED, Change.ASSIGNED), changesOf(order));
    }

    @Test
    void notifiesTheTechnicianOfANewOrder() {
        var hcm = mock(HcmContextFacade.class);
        var notifications = mock(NotificationsContextFacade.class);
        when(hcm.fetchUserIdOfTechnician(7L)).thenReturn(Optional.of(70L));
        var order = withId(installation(), 15L);

        assertTrue(new ExternalNotificationsService(notifications, new ExternalHcmService(hcm)).notifyTechnician(order, Change.ASSIGNED));

        verify(notifications).notifyUser(70L, "ASSIGNMENT_CREATED", "15", "Nueva orden asignada",
                "Instalación el 05/11 de 08:00 a 12:00.");
    }

    @Test
    void notifiesTheTechnicianOfARescheduledOrder() {
        var hcm = mock(HcmContextFacade.class);
        var notifications = mock(NotificationsContextFacade.class);
        when(hcm.fetchUserIdOfTechnician(7L)).thenReturn(Optional.of(70L));
        var order = withId(new Appointment("draft", ServiceType.MAINTENANCE, 3L, DAY, window(14, 16), 7L, "Ana Pérez",
                null, null, null, NOW), 16L);

        new ExternalNotificationsService(notifications, new ExternalHcmService(hcm)).notifyTechnician(order, Change.RESCHEDULED);

        verify(notifications).notifyUser(70L, "ORDER_RESCHEDULED", "16", "Orden reprogramada",
                "Mantenimiento pasó al 05/11 de 14:00 a 16:00.");
    }

    @Test
    void skipsATechnicianWithoutAccount() {
        var hcm = mock(HcmContextFacade.class);
        var notifications = mock(NotificationsContextFacade.class);
        when(hcm.fetchUserIdOfTechnician(any())).thenReturn(Optional.empty());

        assertFalse(new ExternalNotificationsService(notifications, new ExternalHcmService(hcm)).notifyTechnician(installation(), Change.ASSIGNED));
        verify(notifications, never()).notifyUser(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void theHandlerNeverFailsTheChangeOfTheOrder() {
        var service = mock(ExternalNotificationsService.class);
        var order = installation();
        doThrow(new IllegalStateException("down")).when(service).notifyTechnician(order, Change.ASSIGNED);
        var handler = new TechnicalOrderNotificationsHandler(service);

        handler.on(new TechnicalOrderAssignedEvent(order, Change.ASSIGNED));

        verify(service).notifyTechnician(order, Change.ASSIGNED);
    }

    @Test
    void labelsEveryKindOfOrder() {
        var hcm = mock(HcmContextFacade.class);
        var notifications = mock(NotificationsContextFacade.class);
        when(hcm.fetchUserIdOfTechnician(7L)).thenReturn(Optional.of(70L));
        var evaluation = new Appointment("draft", DAY, window(10, 12), 7L, "Ana Pérez", null, null, null);

        new ExternalNotificationsService(notifications, new ExternalHcmService(hcm)).notifyTechnician(evaluation, Change.ASSIGNED);

        verify(notifications).notifyUser(70L, "ASSIGNMENT_CREATED", "null", "Nueva orden asignada",
                "Evaluación técnica el 05/11 de 10:00 a 12:00.");
    }

    @Test
    void hcmFindsTheAccountOfATechnician() {
        var employees = mock(EmployeeRepository.class);
        var technician = new Employee(1L, 70L, 70L, EmployeeRole.TECH);
        when(employees.findById(7L)).thenReturn(Optional.of(technician));
        when(employees.findById(8L)).thenReturn(Optional.empty());
        var facade = new HcmContextFacadeImpl(employees, mock(ContractPersistenceRepository.class),
                mock(WorkSchedulePersistenceRepository.class), mock(ExternalProfilesService.class), 1L);

        assertEquals(Optional.of(70L), facade.fetchUserIdOfTechnician(7L));
        assertTrue(facade.fetchUserIdOfTechnician(8L).isEmpty());
        assertTrue(facade.fetchUserIdOfTechnician(null).isEmpty());
    }

    private static List<Change> changesOf(Appointment order) {
        Collection<?> events = ReflectionTestUtils.invokeMethod(order, "domainEvents");
        return events.stream().filter(TechnicalOrderAssignedEvent.class::isInstance)
                .map(event -> ((TechnicalOrderAssignedEvent) event).change()).toList();
    }

    private static Appointment withId(Appointment order, long id) {
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private static Appointment installation() {
        return new Appointment("draft", ServiceType.INSTALLATION, DAY, window(8, 12), 7L, "Ana Pérez",
                "María Villanueva", "984123456", null, NOW);
    }

    private static TimeRange window(int from, int to) {
        return new TimeRange(LocalTime.of(from, 0), LocalTime.of(to, 0));
    }
}
