package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface RelativeRepository {
    Relative save(Relative relative);
    Optional<Relative> findById(Long id);
    Optional<Relative> findByUserId(UserId userId);
    List<Relative> findAllByBusinessId(BusinessId businessId);
    boolean existsByUserId(UserId userId);
}
