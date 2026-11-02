package com.resolum.alivia.operations;

import com.resolum.alivia.operations.application.internal.commandservices.IncidentCommandServiceImpl;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.domain.exceptions.IncidentAccessDenied;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotAllowed;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.AttachEvidenceCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateIncidentCommand;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade.ActiveSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IncidentTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC);
    private static final String HOUSEHOLD = "h-1";
    private static final String PHOTO = "https://res.cloudinary.com/alivia/image/upload/v1/incidents/p1.jpg";

    private IncidentPersistenceRepository incidents;
    private AppointmentRepository appointments;
    private SubscriptionsContextFacade subscriptionsFacade;
    private IncidentCommandServiceImpl commands;

    @BeforeEach
    void setUp() {
        incidents = mock(IncidentPersistenceRepository.class);
        appointments = mock(AppointmentRepository.class);
        subscriptionsFacade = mock(SubscriptionsContextFacade.class);
        commands = new IncidentCommandServiceImpl(incidents, appointments,
                new ExternalSubscriptionsService(subscriptionsFacade), CLOCK);
        when(incidents.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionsFacade.fetchActiveSubscriptionOfHousehold(HOUSEHOLD))
                .thenReturn(Optional.of(new ActiveSubscription(1L, 10L, "draft")));
    }

    @Test
    void opensIncidentsForTheSubscriberOfAHomeWithApprovedInstallation() {
        when(appointments.existsByDraftIdAndServiceTypeAndInstallationResult("draft", ServiceType.INSTALLATION,
                InstallationResult.APPROVED)).thenReturn(true);

        var incident = commands.handle(create(10L));

        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertEquals("draft", incident.getDraftId());
        assertEquals(IncidentSeverity.HIGH, incident.getSeverity());
    }

    @Test
    void rejectsAccountsWithoutActiveSubscriptionOrApprovedInstallation() {
        assertThrows(IncidentNotAllowed.class, () -> commands.handle(create(10L)));
        when(appointments.existsByDraftIdAndServiceTypeAndInstallationResult(any(), any(), any())).thenReturn(true);
        assertThrows(IncidentNotAllowed.class, () -> commands.handle(create(11L)));
        assertThrows(IncidentNotAllowed.class, () -> commands.handle(create(null)));
        when(subscriptionsFacade.fetchActiveSubscriptionOfHousehold(HOUSEHOLD)).thenReturn(Optional.empty());
        assertThrows(IncidentNotAllowed.class, () -> commands.handle(create(10L)));
        verify(incidents, never()).save(any());
    }

    @Test
    void answersWithTheExistingIncidentWhenTheDeviceAlreadyHasOne() {
        when(appointments.existsByDraftIdAndServiceTypeAndInstallationResult(any(), any(), any())).thenReturn(true);
        var existing = new Incident(HOUSEHOLD, "draft", 5L, 10L, "Sin respuesta", IncidentSeverity.LOW, Instant.now(CLOCK));
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", 42L);
        when(incidents.findFirstByHouseholdIdAndDeviceIdAndStatusIn(HOUSEHOLD, 5L,
                java.util.List.of(IncidentStatus.OPEN, IncidentStatus.SCHEDULED, IncidentStatus.IN_PROGRESS))).thenReturn(Optional.of(existing));

        var conflict = assertThrows(com.resolum.alivia.operations.domain.exceptions.IncidentAlreadyOpen.class,
                () -> commands.handle(create(10L)));

        assertEquals(42L, conflict.getIncidentId());
        verify(incidents, never()).save(any());
    }

    @Test
    void keepsOnlySecureEvidenceOfTheReporter() {
        var incident = new Incident(HOUSEHOLD, "draft", 5L, 10L, "El micrófono no responde", IncidentSeverity.HIGH, Instant.now(CLOCK));
        when(incidents.findById(3L)).thenReturn(Optional.of(incident));

        commands.handle(new AttachEvidenceCommand(3L, 10L, PHOTO, "incidents/p1"));

        assertEquals(1, incident.getEvidences().size());
        assertEquals("incidents/p1", incident.getEvidences().getFirst().publicId());
        assertThrows(IncidentAccessDenied.class, () -> commands.handle(new AttachEvidenceCommand(3L, 11L, PHOTO, "x")));
        assertThrows(IllegalArgumentException.class,
                () -> commands.handle(new AttachEvidenceCommand(3L, 10L, "http://insecure/p.jpg", "x")));
        assertThrows(IllegalArgumentException.class,
                () -> commands.handle(new AttachEvidenceCommand(3L, 10L, PHOTO, " ")));
        for (int index = 1; index < Incident.MAX_EVIDENCES; index++) {
            commands.handle(new AttachEvidenceCommand(3L, 10L, PHOTO, "incidents/p" + index));
        }
        assertThrows(IllegalArgumentException.class, () -> commands.handle(new AttachEvidenceCommand(3L, 10L, PHOTO, "y")));
    }

    @Test
    void closesInstallationOrdersWithTheirResult() {
        var installation = new Appointment("draft", ServiceType.INSTALLATION, LocalDate.of(2026, 11, 3),
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), 7L, "Ana Pérez", "María", "984123456", null,
                Instant.now(CLOCK));

        installation.recordInstallationResult(InstallationResult.APPROVED, " Todo operativo ");

        assertEquals(AppointmentStatus.COMPLETED, installation.getStatus());
        assertEquals("Todo operativo", installation.getCompletionNotes());
        assertFalse(installation.isAttendedBy(7L));
        assertThrows(RuntimeException.class,
                () -> installation.recordInstallationResult(InstallationResult.REJECTED, null));
    }

    private static CreateIncidentCommand create(Long userId) {
        return new CreateIncidentCommand(userId, HOUSEHOLD, 5L, " El micrófono no responde ", IncidentSeverity.HIGH);
    }
}
