package com.resolum.alivia.operations.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Pattern;

/**
 * Technical evaluation visit that the family books after the card is preauthorized.
 *
 * <p>Unlike the Operations diagram, where the administrator assigns a technician later, the technician is
 * chosen at booking time from the ones whose work schedule covers the window, so the family sees who
 * will visit them.</p>
 *
 * <p>The unique constraints stop two families that confirm at the same instant from taking the same
 * technician and window, and a draft from booking twice; the command service turns the violation into
 * {@code AppointmentSlotTaken}. They cover canceled rows too, which is fine while appointments cannot be
 * canceled.</p>
 */
@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointments_date_status", columnList = "scheduled_date,status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointments_technician_window",
                columnNames = {"technician_id", "scheduled_date", "start_time"}),
        @UniqueConstraint(name = "uk_appointments_draft", columnNames = "draft_id")
})
@Getter
public class Appointment extends AuditableAbstractAggregateRoot<Appointment> {
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{9,15}$");

    @Column(name = "draft_id", nullable = false, length = 36)
    private String draftId;
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
    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;
    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;
    @Column(length = 300)
    private String directions;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppointmentStatus status;

    protected Appointment() {
    }

    public Appointment(String draftId, LocalDate scheduledDate, TimeRange window, Long technicianId,
                       String technicianName, String contactName, String contactPhone, String directions) {
        if (draftId == null || draftId.isBlank()) throw new IllegalArgumentException("The configuration draft is required.");
        if (scheduledDate == null || window == null || window.crossesMidnight()) {
            throw new IllegalArgumentException("A date and a window within the day are required.");
        }
        if (technicianId == null || technicianName == null || technicianName.isBlank()) {
            throw new IllegalArgumentException("The visit needs a technician.");
        }
        if (contactName == null || contactName.isBlank() || contactName.trim().length() > 100) {
            throw new IllegalArgumentException("The name of the person who receives the visit is required.");
        }
        var phone = contactPhone == null ? "" : contactPhone.replaceAll("[\\s-]", "");
        if (!PHONE.matcher(phone).matches()) throw new IllegalArgumentException("The contact phone is not valid.");
        if (directions != null && directions.length() > 300) {
            throw new IllegalArgumentException("Directions cannot exceed 300 characters.");
        }
        this.draftId = draftId;
        this.scheduledDate = scheduledDate;
        this.startTime = window.start();
        this.endTime = window.end();
        this.technicianId = technicianId;
        this.technicianName = technicianName.trim();
        this.contactName = contactName.trim();
        this.contactPhone = phone;
        this.directions = directions == null || directions.isBlank() ? null : directions.trim();
        this.status = AppointmentStatus.SCHEDULED;
    }

    public TimeRange window() {
        return new TimeRange(startTime, endTime);
    }

    public boolean isAttendedBy(Long candidateTechnicianId) {
        return status == AppointmentStatus.SCHEDULED && technicianId.equals(candidateTechnicianId);
    }
}
