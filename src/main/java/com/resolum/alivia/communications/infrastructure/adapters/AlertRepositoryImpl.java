package com.resolum.alivia.communications.infrastructure.adapters;

import com.resolum.alivia.communications.domain.model.aggregates.Alert;
import com.resolum.alivia.communications.domain.model.valueobjects.AlertStatus;
import com.resolum.alivia.communications.domain.repositories.AlertRepository;
import com.resolum.alivia.communications.infrastructure.persistence.jpa.repositories.AlertJpaRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AlertRepositoryImpl implements AlertRepository {

    private final AlertJpaRepository jpaRepository;

    public AlertRepositoryImpl(AlertJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Alert save(Alert alert) {
        return jpaRepository.save(alert);
    }

    @Override
    public Optional<Alert> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Alert> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId) {
        return jpaRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }

    @Override
    public List<Alert> findByStatusAndAttemptsLessThan(AlertStatus status, Integer attempts) {
        return jpaRepository.findByStatusAndAttemptsLessThan(status, attempts);
    }
}
