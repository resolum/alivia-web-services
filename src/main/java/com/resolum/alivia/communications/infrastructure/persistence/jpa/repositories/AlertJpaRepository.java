package com.resolum.alivia.communications.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.communications.domain.model.aggregates.Alert;
import com.resolum.alivia.communications.domain.model.valueobjects.AlertStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertJpaRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId);
    List<Alert> findByStatusAndAttemptsLessThan(AlertStatus status, Integer attempts);
}
