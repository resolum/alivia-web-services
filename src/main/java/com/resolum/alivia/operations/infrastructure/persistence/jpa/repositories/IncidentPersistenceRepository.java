package com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentPersistenceRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByReportedByOrderByCreatedAtDesc(Long reportedBy);

    List<Incident> findAllByOrderByCreatedAtDesc();

    Optional<Incident> findFirstByHouseholdIdAndDeviceIdAndStatusIn(String householdId, Long deviceId,
                                                                     Collection<IncidentStatus> statuses);
}
