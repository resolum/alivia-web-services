package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelativePersistenceRepository extends JpaRepository<Relative, Long> {
    Optional<Relative> findByUserId(UserId userId);
    List<Relative> findAllByBusinessId(BusinessId businessId);
    boolean existsByUserId(UserId userId);
}
