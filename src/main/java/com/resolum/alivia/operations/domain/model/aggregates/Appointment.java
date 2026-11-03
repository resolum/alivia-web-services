package com.resolum.alivia.operations.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotScheduled;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.AssignmentReason;
import com.resolum.alivia.operations.domain.model.valueobjects.AssignmentRecord;
import com.resolum.alivia.operations.domain.model.valueobjects.EvaluationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.MaintenanceReport;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianOccupancy;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Technical order: a visit of a technician to the home of a contracting request, either the technical
 * evaluation or the installation.
 *
 * <p>Unlike the Operations diagram, where the administrator assigns a technician later, a technician is
 * reserved when the order is created, from the ones whose work schedule covers the block, so the block
 * never takes more orders than technicians. The administrator can then choose another eligible technician,
 * and when the responsible one stops being available the backend reassigns the order. Every change is kept
 * in {@code assignments}.</p>
 *
 * <p>A maintenance order attends an incident of a home in service ({@code incidentId}); evaluation and
 * installation orders have none.</p>
 *
 * <p>The unique constraint stops two orders that are confirmed at the same instant from taking the same
 * technician and window; the command service turns the violation into {@code SlotUnavailable}. It covers
 * canceled rows too, which is fine while orders cannot be canceled. A request may have several maintenance
 * orders over time, so the command service checks that it has a single scheduled order of each contracting
 * kind.</p>
 */
@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointments_date_status", columnList = "scheduled_date,status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointments_technician_window",
                columnNames = {"technician_id", "scheduled_date", "start_time"})
})
@Getter
public class Appointment extends AuditableAbstractAggregateRoot<Appointment> implements TechnicianOccupancy {
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{9,15}$");

    @Column(name = "draft_id", nullable = false, length = 36)
    private String draftId;
    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 16)
    private ServiceType serviceType;
    @Column(name = "incident_id")
    private Long incidentId;
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "technician_id", nullable = false)
    private Long technicianId;
    @Column(name = "technician_name", nullable = false, length = 120)
    private String technicianName;
    @Column(name = "contact_name", length = 100)
    private String contactName;
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;
    @Column(length = 300)
    private String directions;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppointmentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "installation_result", length = 16)
    private InstallationResult installationResult;
    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_result", length = 24)
    private EvaluationResult evaluationResult;
    @Column(name = "completion_notes", length = 300)
    private String completionNotes;
    @Embedded
    private MaintenanceReport maintenanceReport;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "appointment_assignments", joinColumns = @JoinColumn(name = "appointment_id"))
    @OrderColumn(name = "assignment_order")
    private List<AssignmentRecord> assignments = new ArrayList<>();

    protected Appointment() {
    }

    /** Technical evaluation booked by the family, with the technician of the window they chose. */
    public Appointment(String draftId, LocalDate scheduledDate, TimeRange window, Long technicianId,
                       String technicianName, String contactName, String contactPhone, String directions) {
        this(draftId, ServiceType.EVALUATION, scheduledDate, window, technicianId, technicianName, contactName,
                contactPhone, directions, Instant.now());
    }

    public Appointment(String draftId, ServiceType serviceType, LocalDate scheduledDate, TimeRange window,
                       Long technicianId, String technicianName, String contactName, String contactPhone,
                       String directions, Instant bookedAt) {
        this(draftId, serviceType, null, scheduledDate, window, technicianId, technicianName, contactName,
                contactPhone, directions, bookedAt);
    }

    public Appointment(String draftId, ServiceType serviceType, Long incidentId, LocalDate scheduledDate,
                       TimeRange window, Long technicianId, String technicianName, String contactName,
                       String contactPhone, String directions, Instant bookedAt) {
        if (draftId == null || draftId.isBlank()) throw new IllegalArgumentException("The configuration draft is required.");
        if (serviceType == null) throw new IllegalArgumentException("The service type is required.");
        if ((serviceType == ServiceType.MAINTENANCE) != (incidentId != null)) {
            throw new IllegalArgumentException("Only maintenance orders attend an incident, and they always do.");
        }
        if (scheduledDate == null || window == null || window.crossesMidnight()) {
            throw new IllegalArgumentException("A date and a window within the day are required.");
        }
        requireTechnician(technicianId, technicianName);
        // The family books the evaluation without naming who receives the technician; when a contact is given,
        // it needs both the name and a valid phone.
        var name = contactName == null || contactName.isBlank() ? null : contactName.trim();
        var phone = contactPhone == null || contactPhone.isBlank() ? null : contactPhone.replaceAll("[\\s-]", "");
        if ((name == null) != (phone == null)) {
            throw new IllegalArgumentException("The contact needs both the name and the phone of who receives the visit.");
        }
        if (name != null && name.length() > 100) {
            throw new IllegalArgumentException("The name of the person who receives the visit is too long.");
        }
        if (phone != null && !PHONE.matcher(phone).matches()) throw new IllegalArgumentException("The contact phone is not valid.");
        if (directions != null && directions.length() > 300) {
            throw new IllegalArgumentException("Directions cannot exceed 300 characters.");
        }
        this.draftId = draftId;
        this.serviceType = serviceType;
        this.incidentId = incidentId;
        this.scheduledDate = scheduledDate;
        this.startTime = window.start();
        this.endTime = window.end();
        this.contactName = name;
        this.contactPhone = phone;
        this.directions = directions == null || directions.isBlank() ? null : directions.trim();
        this.status = AppointmentStatus.SCHEDULED;
        record(technicianId, technicianName, AssignmentReason.BOOKED, null, bookedAt);
    }

    /**
     * Makes another technician responsible for the order, keeping its block. The caller checks first that
     * the technician works the whole window and has no other visit in it.
     */
    public void assignTechnician(Long newTechnicianId, String newTechnicianName, String note, Instant at) {
        requireScheduled();
        requireTechnician(newTechnicianId, newTechnicianName);
        if (newTechnicianId.equals(technicianId)) {
            throw new IllegalArgumentException("The technician is already responsible for this order.");
        }
        record(newTechnicianId, newTechnicianName, AssignmentReason.ASSIGNED, note, at);
    }

    /**
     * Replaces a technician who stopped being available. The order keeps its block when {@code slot} is the
     * current one, or moves to {@code slot} when nobody else could take the current one.
     */
    public void reassign(SlotId slot, Long newTechnicianId, String newTechnicianName, String reason, Instant at) {
        requireScheduled();
        requireTechnician(newTechnicianId, newTechnicianName);
        if (slot == null || slot.window().duration().compareTo(serviceType.window()) != 0) {
            throw new IllegalArgumentException("The new block must have the length of the order's window.");
        }
        if (newTechnicianId.equals(technicianId) && slot.equals(slotId())) {
            throw new IllegalArgumentException("The reassignment must change the technician or the block.");
        }
        this.scheduledDate = slot.date();
        this.startTime = slot.window().start();
        this.endTime = slot.window().end();
        record(newTechnicianId, newTechnicianName, AssignmentReason.REASSIGNED, reason, at);
    }

    /**
     * Closes an evaluation visit with the technician's decision at the home. A viable home moves the family's
     * request to the confirmation of its configuration; an adaptation needs the notes that describe it.
     */
    public void recordEvaluationResult(EvaluationResult result, String notes) {
        requireScheduled();
        if (serviceType != ServiceType.EVALUATION) {
            throw new IllegalArgumentException("Only evaluation orders record an evaluation result.");
        }
        if (result == null) throw new IllegalArgumentException("The evaluation result is required.");
        var trimmed = notes == null || notes.isBlank() ? null : notes.trim();
        if (trimmed != null && trimmed.length() > 300) throw new IllegalArgumentException("Notes cannot exceed 300 characters.");
        if (result == EvaluationResult.VIABLE_WITH_ADAPTATION && trimmed == null) {
            throw new IllegalArgumentException("Describe the adaptation the home needs.");
        }
        this.evaluationResult = result;
        this.completionNotes = trimmed;
        this.status = AppointmentStatus.COMPLETED;
    }

    /**
     * Closes an installation visit with its result. An approved installation puts the home in service, which
     * is the condition for the family to report incidents.
     */
    public void recordInstallationResult(InstallationResult result, String notes) {
        requireScheduled();
        if (serviceType != ServiceType.INSTALLATION) {
            throw new IllegalArgumentException("Only installation orders record an installation result.");
        }
        if (result == null) throw new IllegalArgumentException("The installation result is required.");
        if (notes != null && notes.length() > 300) throw new IllegalArgumentException("Notes cannot exceed 300 characters.");
        this.installationResult = result;
        this.completionNotes = notes == null || notes.isBlank() ? null : notes.trim();
        this.status = AppointmentStatus.COMPLETED;
    }

    /**
     * Closes a maintenance visit with the technician's report. The report is validated before anything
     * changes, so a report without its test result leaves the order scheduled.
     */
    public void completeMaintenance(MaintenanceReport report) {
        requireScheduled();
        if (serviceType != ServiceType.MAINTENANCE) {
            throw new IllegalArgumentException("Only maintenance orders record a maintenance report.");
        }
        if (report == null) throw new IllegalArgumentException("The maintenance report is required.");
        this.maintenanceReport = report;
        this.status = AppointmentStatus.COMPLETED;
    }

    @Override
    public TimeRange window() {
        return new TimeRange(startTime, endTime);
    }

    public SlotId slotId() {
        return new SlotId(scheduledDate, window());
    }

    @Override
    public boolean isAttendedBy(Long candidateTechnicianId) {
        return status == AppointmentStatus.SCHEDULED && technicianId.equals(candidateTechnicianId);
    }

    public List<AssignmentRecord> getAssignments() {
        return List.copyOf(assignments);
    }

    private void record(Long newTechnicianId, String newTechnicianName, AssignmentReason reason, String note,
                        Instant at) {
        var name = newTechnicianName.trim();
        var cleanNote = note == null || note.isBlank() ? null : note.trim();
        assignments.add(new AssignmentRecord(newTechnicianId, name, scheduledDate, startTime, endTime, reason,
                cleanNote, at));
        this.technicianId = newTechnicianId;
        this.technicianName = name;
    }

    private void requireScheduled() {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new TechnicalOrderNotScheduled();
        }
    }

    private static void requireTechnician(Long id, String name) {
        if (id == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("The visit needs a technician.");
        }
    }
}
