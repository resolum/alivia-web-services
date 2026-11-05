package com.resolum.alivia.operations.domain.model.aggregates;

import com.resolum.alivia.operations.domain.exceptions.IncidentNotOpen;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotScheduled;
import com.resolum.alivia.operations.domain.model.valueobjects.Evidence;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.ReportedDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentProblemType;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatusChange;
import com.resolum.alivia.operations.domain.model.valueobjects.MaintenanceReport;
import com.resolum.alivia.operations.domain.model.valueobjects.TestResult;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A failure that a family reports on a device of a home in service.
 *
 * <p>Only the account that holds the active subscription of the household can report it, and only once its
 * installation was approved; the command service checks both before creating the incident. The draft id
 * links the incident to the technical orders of that contracting request.</p>
 */
@Entity
@Table(name = "support_incidents", indexes = {
        @Index(name = "idx_support_incidents_household_device_status", columnList = "household_id,device_id,status")
})
@Getter
public class Incident extends AuditableAbstractAggregateRoot<Incident> {
    public static final int MAX_EVIDENCES = 5;

    @Column(name = "household_id", nullable = false, length = 36)
    private String householdId;
    @Column(name = "draft_id", nullable = false, length = 36)
    private String draftId;
    @Column(name = "device_id", nullable = false)
    private Long deviceId;
    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;
    @Column(nullable = false, length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentSeverity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status;
    @Column(length = 500)
    private String resolution;
    @Enumerated(EnumType.STRING)
    @Column(name = "problem_type", length = 20)
    private IncidentProblemType problemType;
    @Column(name = "occurred_at")
    private Instant occurredAt;
    @Embedded
    private ReportedDevice device;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "support_incident_evidences", joinColumns = @JoinColumn(name = "incident_id"))
    @OrderColumn(name = "evidence_order")
    private List<Evidence> evidences = new ArrayList<>();
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "support_incident_status_history", joinColumns = @JoinColumn(name = "incident_id"))
    @OrderColumn(name = "change_order")
    private List<IncidentStatusChange> statusHistory = new ArrayList<>();

    protected Incident() {
    }

    public Incident(String householdId, String draftId, Long deviceId, Long reportedBy, String description,
                    IncidentSeverity severity, Instant reportedAt) {
        if (householdId == null || householdId.isBlank()) throw new IllegalArgumentException("The household is required.");
        if (draftId == null || draftId.isBlank()) throw new IllegalArgumentException("The contracting request is required.");
        if (deviceId == null) throw new IllegalArgumentException("The device is required.");
        if (reportedBy == null) throw new IllegalArgumentException("The reporting account is required.");
        if (description == null || description.isBlank() || description.trim().length() > 500) {
            throw new IllegalArgumentException("Describe the failure in up to 500 characters.");
        }
        if (severity == null) throw new IllegalArgumentException("The severity is required.");
        this.householdId = householdId.trim();
        this.draftId = draftId;
        this.deviceId = deviceId;
        this.reportedBy = reportedBy;
        this.description = description.trim();
        this.severity = severity;
        moveTo(IncidentStatus.OPEN, reportedAt);
    }

    /** Keeps the secure reference of a photo; a resolved incident takes no more evidence. */
    public void attachEvidence(Evidence evidence) {
        if (evidence == null) throw new IllegalArgumentException("The evidence is required.");
        if (status == IncidentStatus.RESOLVED) throw new IllegalArgumentException("A resolved incident takes no more evidence.");
        if (evidences.size() >= MAX_EVIDENCES) {
            throw new IllegalArgumentException("An incident takes up to %d photos.".formatted(MAX_EVIDENCES));
        }
        evidences.add(evidence);
    }

    /**
     * Keeps what the family chose in the report form: the kind of problem, when it happened (not in the future)
     * and the installed device it is about.
     */
    public void describeReport(IncidentProblemType problemType, Instant occurredAt, ReportedDevice device, Instant now) {
        if (problemType == null) throw new IllegalArgumentException("The problem type is required.");
        if (device == null) throw new IllegalArgumentException("The device is required.");
        if (occurredAt != null && now != null && occurredAt.isAfter(now)) {
            throw new IllegalArgumentException("The incident cannot have happened in the future.");
        }
        this.problemType = problemType;
        this.occurredAt = occurredAt;
        this.device = device;
    }

    /** A technician took the report to evaluate it and plan the visit (En evaluación). */
    public void startEvaluation(Instant startedAt) {
        if (status != IncidentStatus.OPEN) throw new IncidentNotOpen();
        moveTo(IncidentStatus.IN_EVALUATION, startedAt);
    }

    /** A maintenance visit was booked for it, after the report or its evaluation. */
    public void scheduleMaintenance(Instant scheduledAt) {
        if (status != IncidentStatus.OPEN && status != IncidentStatus.IN_EVALUATION) throw new IncidentNotOpen();
        moveTo(IncidentStatus.SCHEDULED, scheduledAt);
    }

    /** The technician evaluated the report and confirms the visit the family chose (Visita programada). */
    public void confirmVisit(Instant confirmedAt) {
        if (status != IncidentStatus.IN_EVALUATION) throw new com.resolum.alivia.operations.domain.exceptions.IncidentNotInEvaluation();
        moveTo(IncidentStatus.SCHEDULED, confirmedAt);
    }

    /** "INC-2026-0084": the year it was reported and its number, as the family and the technician see it. */
    public String code() {
        var reported = statusHistory.isEmpty() ? null : statusHistory.getFirst().changedAt();
        var year = reported == null ? java.time.Year.now(LIMA).getValue() : reported.atZone(LIMA).getYear();
        return "INC-%d-%04d".formatted(year, getId() == null ? 0 : getId());
    }

    private static final java.time.ZoneId LIMA = java.time.ZoneId.of("America/Lima");

    /** The technician arrived for the scheduled visit and started attending it. */
    public void startAttention(Instant startedAt) {
        if (status != IncidentStatus.SCHEDULED) throw new IncidentNotScheduled();
        moveTo(IncidentStatus.IN_PROGRESS, startedAt);
    }

    /**
     * Applies the result of a maintenance visit: a passed test resolves the incident; a failed one opens it
     * again so another visit can be booked.
     */
    public void applyMaintenanceResult(MaintenanceReport report, Instant recordedAt) {
        if (status != IncidentStatus.SCHEDULED && status != IncidentStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Only an incident with a scheduled visit takes its result.");
        }
        if (report.testResult() == TestResult.PASSED) {
            this.resolution = report.resolution();
            moveTo(IncidentStatus.RESOLVED, recordedAt);
        } else {
            moveTo(IncidentStatus.OPEN, recordedAt);
        }
    }

    private void moveTo(IncidentStatus next, Instant changedAt) {
        if (changedAt == null) throw new IllegalArgumentException("The time of the change is required.");
        this.status = next;
        statusHistory.add(new IncidentStatusChange(next, changedAt));
    }

    /** Still waiting for attention: a new report of the same device would duplicate it. */
    public boolean isPending() {
        return status != IncidentStatus.RESOLVED;
    }

    public boolean isReportedBy(Long userId) {
        return reportedBy.equals(userId);
    }

    public List<Evidence> getEvidences() {
        return List.copyOf(evidences);
    }

    public List<IncidentStatusChange> getStatusHistory() {
        return List.copyOf(statusHistory);
    }
}
