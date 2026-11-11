package com.resolum.alivia.communications.domain.repositories;

import com.resolum.alivia.communications.domain.model.aggregates.Alert;
import com.resolum.alivia.communications.domain.model.valueobjects.AlertStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface AlertRepository {
    Alert save(Alert alert);
    Optional<Alert> findById(Long id);
    List<Alert> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId);
    List<Alert> findByStatusAndAttemptsLessThan(AlertStatus status, Integer attempts);
}
