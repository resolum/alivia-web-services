package com.resolum.alivia.communications.domain.repositories;

import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.PushStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findById(Long id);
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId);
    List<Notification> findByRecipientIdAndStatusOrderByCreatedAtDesc(UserId recipientId, NotificationStatus status);
    List<Notification> findByPushStatusAndPushAttemptsLessThan(PushStatus pushStatus, Integer pushAttempts);
}
