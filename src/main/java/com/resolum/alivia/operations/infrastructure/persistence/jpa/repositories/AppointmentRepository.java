package com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByScheduledDateAndStatus(LocalDate scheduledDate, AppointmentStatus status);

    boolean existsByDraftIdAndServiceTypeAndStatus(String draftId, ServiceType serviceType, AppointmentStatus status);

    Optional<Appointment> findFirstByDraftIdAndServiceTypeAndStatus(String draftId, ServiceType serviceType,
                                                                    AppointmentStatus status);

    List<Appointment> findByDraftIdInAndStatus(Collection<String> draftIds, AppointmentStatus status);
}
