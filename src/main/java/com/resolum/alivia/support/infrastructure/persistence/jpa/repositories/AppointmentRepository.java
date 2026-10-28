package com.resolum.alivia.support.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.support.domain.model.aggregates.Appointment;
import com.resolum.alivia.support.domain.model.valueobjects.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByScheduledDateAndStatus(LocalDate scheduledDate, AppointmentStatus status);

    boolean existsByDraftIdAndStatus(String draftId, AppointmentStatus status);
}
