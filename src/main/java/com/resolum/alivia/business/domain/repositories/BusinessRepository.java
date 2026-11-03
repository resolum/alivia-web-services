package com.resolum.alivia.business.domain.repositories;

import com.resolum.alivia.business.domain.model.aggregates.Business;

import java.util.Optional;

public interface BusinessRepository {
    Optional<Business> findById(Long id);
    Optional<Business> findByAdministratorId(Long administratorId);
    boolean existsByAdministratorId(Long administratorId);
    Business save(Business business);
}
