package com.resolum.alivia.business.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.business.domain.model.aggregates.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessPersistenceRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByAdministratorId(Long administratorId);

    boolean existsByAdministratorId(Long administratorId);
}
