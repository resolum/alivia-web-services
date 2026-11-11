package com.resolum.alivia.communications.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.PushStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId);
    List<Notification> findByRecipientIdAndStatusOrderByCreatedAtDesc(UserId recipientId, NotificationStatus status);
    List<Notification> findByPushStatusAndPushAttemptsLessThan(PushStatus pushStatus, Integer pushAttempts);
}
