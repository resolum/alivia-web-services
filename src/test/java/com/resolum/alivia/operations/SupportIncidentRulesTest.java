package com.resolum.alivia.operations;

import com.resolum.alivia.operations.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.operations.application.internal.queryservices.IncidentQueryServiceImpl;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotScheduled;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotScheduled;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentsQuery;
import com.resolum.alivia.operations.domain.model.queries.GetMaintenanceOrdersByIncidentIdsQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.Evidence;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.MaintenanceReport;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.TestResult;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Business rules of incidents, evidences, maintenance reports and installation results. */
class SupportIncidentRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
    private static final String PHOTO = "https://res.cloudinary.com/alivia/image/upload/v1/incident.jpg";

    private static Incident incident() {
        return new Incident("h-1", "draft", 5L, 10L, "El micrófono no responde", IncidentSeverity.HIGH, NOW);
    }

    private static Appointment order(ServiceType type, Long incidentId) {
        return new Appointment("draft", type, incidentId, LocalDate.of(2026, 11, 3),
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 0)), 7L, "Ana Pérez", "María", "984123456", null, NOW);
    }

    @Test
    void anIncidentNeedsItsHomeDeviceReporterDescriptionSeverityAndTime() {
        assertThrows(IllegalArgumentException.class, () -> new Incident(" ", "d", 5L, 10L, "Falla", IncidentSeverity.LOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Incident("h", null, 5L, 10L, "Falla", IncidentSeverity.LOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Incident("h", "d", null, 10L, "Falla", IncidentSeverity.LOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Incident("h", "d", 5L, null, "Falla", IncidentSeverity.LOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Incident("h", "d", 5L, 10L, " ", IncidentSeverity.LOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Incident("h", "d", 5L, 10L, "x".repeat(501), IncidentSeverity.LOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Incident("h", "d", 5L, 10L, "Falla", null, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Incident("h", "d", 5L, 10L, "Falla", IncidentSeverity.LOW, null));

        var incident = new Incident(" h-1 ", "draft", 5L, 10L, "  Falla  ", IncidentSeverity.LOW, NOW);
        assertEquals("h-1", incident.getHouseholdId());
        assertEquals("Falla", incident.getDescription());
        assertTrue(incident.isPending());
        assertTrue(incident.isReportedBy(10L));
        assertFalse(incident.isReportedBy(11L));
    }

    @Test
    void keepsUpToFivePhotosAndNoneAfterTheIncidentIsResolved() {
        var incident = incident();
        assertThrows(IllegalArgumentException.class, () -> incident.attachEvidence(null));
        for (int i = 0; i < Incident.MAX_EVIDENCES; i++) incident.attachEvidence(new Evidence(PHOTO, "p" + i, NOW));
        assertThrows(IllegalArgumentException.class, () -> incident.attachEvidence(new Evidence(PHOTO, "p6", NOW)));

        var resolved = incident();
        resolved.scheduleMaintenance(NOW);
        resolved.applyMaintenanceResult(new MaintenanceReport("d", "m", TestResult.PASSED, "ok"), NOW);
        assertFalse(resolved.isPending());
        assertThrows(IllegalArgumentException.class, () -> resolved.attachEvidence(new Evidence(PHOTO, "p", NOW)));
    }

    @Test
    void onlyAScheduledIncidentStartsItsAttentionOrTakesAVisitResult() {
        var incident = incident();
        var report = new MaintenanceReport("d", "m", TestResult.FAILED, null);
        assertThrows(IncidentNotScheduled.class, () -> incident.startAttention(NOW));
        assertThrows(IllegalArgumentException.class, () -> incident.applyMaintenanceResult(report, NOW));
        assertThrows(IllegalArgumentException.class, () -> incident.scheduleMaintenance(null));

        incident.scheduleMaintenance(NOW);
        incident.startAttention(NOW.plusSeconds(60));
        incident.applyMaintenanceResult(report, NOW.plusSeconds(120));
        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertEquals(4, incident.getStatusHistory().size());
        assertEquals(NOW.plusSeconds(120), incident.getStatusHistory().getLast().changedAt());
    }

    @Test
    void evidenceMustBeASecureReferenceOfTheStoredPhoto() {
        assertThrows(NullPointerException.class, () -> new Evidence(PHOTO, "p", null));
        assertThrows(IllegalArgumentException.class, () -> new Evidence(PHOTO, " ", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Evidence(PHOTO, "p".repeat(201), NOW));
        assertThrows(IllegalArgumentException.class, () -> new Evidence(null, "p", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Evidence("http://res.cloudinary.com/x.jpg", "p", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Evidence("https://", "p", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Evidence("https://bad host/x", "p", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Evidence("https://x.com/" + "a".repeat(500), "p", NOW));
        var evidence = new Evidence(" " + PHOTO + " ", " p ", NOW);
        assertEquals(PHOTO, evidence.url());
        assertEquals("p", evidence.publicId());
    }

    @Test
    void aMaintenanceReportNeedsDiagnosisWorkAndAShortResolution() {
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceReport(null, "m", TestResult.FAILED, null));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceReport("d", " ", TestResult.FAILED, null));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceReport("x".repeat(501), "m", TestResult.FAILED, null));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceReport("d", "m", TestResult.FAILED, "x".repeat(501)));
        assertNull(new MaintenanceReport("d", "m", TestResult.FAILED, " ").resolution());
    }

    @Test
    void onlyAScheduledInstallationOrderRecordsItsResult() {
        var evaluation = order(ServiceType.EVALUATION, null);
        assertThrows(IllegalArgumentException.class,
                () -> evaluation.recordInstallationResult(InstallationResult.APPROVED, null));

        var installation = order(ServiceType.INSTALLATION, null);
        assertThrows(IllegalArgumentException.class, () -> installation.recordInstallationResult(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> installation.recordInstallationResult(InstallationResult.APPROVED, "x".repeat(301)));
        installation.recordInstallationResult(InstallationResult.REJECTED, " ");
        assertNull(installation.getCompletionNotes());
        assertEquals(InstallationResult.REJECTED, installation.getInstallationResult());
        assertThrows(TechnicalOrderNotScheduled.class,
                () -> installation.recordInstallationResult(InstallationResult.APPROVED, null));

        var maintenance = order(ServiceType.MAINTENANCE, 3L);
        assertThrows(IllegalArgumentException.class, () -> maintenance.recordInstallationResult(InstallationResult.APPROVED, null));
        assertThrows(IllegalArgumentException.class, () -> maintenance.completeMaintenance(null));
        assertThrows(IllegalArgumentException.class, () -> order(ServiceType.INSTALLATION, null).completeMaintenance(
                new MaintenanceReport("d", "m", TestResult.FAILED, null)));
        assertThrows(IllegalArgumentException.class, () -> order(ServiceType.MAINTENANCE, null));
        assertThrows(IllegalArgumentException.class, () -> order(ServiceType.INSTALLATION, 3L));
    }

    @Test
    void listsTheIncidentsOfTheReporterOrAllOfThemFilteredByStatus() {
        var repository = mock(IncidentPersistenceRepository.class);
        var queries = new IncidentQueryServiceImpl(repository);
        var open = incident();
        var scheduled = incident();
        scheduled.scheduleMaintenance(NOW);
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(open, scheduled));
        when(repository.findByReportedByOrderByCreatedAtDesc(10L)).thenReturn(List.of(open, scheduled));
        when(repository.findById(3L)).thenReturn(Optional.of(open));

        assertEquals(2, queries.handle(new GetIncidentsQuery(null, null)).size());
        assertEquals(List.of(scheduled), queries.handle(new GetIncidentsQuery(null, IncidentStatus.SCHEDULED)));
        assertEquals(List.of(open), queries.handle(new GetIncidentsQuery(10L, IncidentStatus.OPEN)));
        assertEquals(Optional.of(open), queries.handle(new GetIncidentByIdQuery(3L)));
    }

    @Test
    void findsTheMaintenanceVisitsOfTheIncidents() {
        var repository = mock(AppointmentRepository.class);
        var queries = new AppointmentQueryServiceImpl(repository, mock(ExternalHcmService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var visit = order(ServiceType.MAINTENANCE, 3L);
        when(repository.findByIncidentIdInOrderByScheduledDateAscStartTimeAsc(List.of(3L))).thenReturn(List.of(visit));

        assertEquals(List.of(visit), queries.handle(new GetMaintenanceOrdersByIncidentIdsQuery(List.of(3L))));
        assertTrue(queries.handle(new GetMaintenanceOrdersByIncidentIdsQuery(List.of())).isEmpty());
        assertTrue(queries.handle(new GetMaintenanceOrdersByIncidentIdsQuery(null)).isEmpty());
    }
}
