package com.resolum.alivia.communications.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushDeviceJpaRepository extends JpaRepository<PushDevice, Long> {
    Optional<PushDevice> findByToken(String token);
    List<PushDevice> findByUserId(UserId userId);
}
