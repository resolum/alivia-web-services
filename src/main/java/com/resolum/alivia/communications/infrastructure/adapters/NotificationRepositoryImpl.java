package com.resolum.alivia.communications.infrastructure.adapters;

import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.PushStatus;
import com.resolum.alivia.communications.domain.repositories.NotificationRepository;
import com.resolum.alivia.communications.infrastructure.persistence.jpa.repositories.NotificationJpaRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    public NotificationRepositoryImpl(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Notification> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId) {
        return jpaRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }

    @Override
    public List<Notification> findByRecipientIdAndStatusOrderByCreatedAtDesc(UserId recipientId, NotificationStatus status) {
        return jpaRepository.findByRecipientIdAndStatusOrderByCreatedAtDesc(recipientId, status);
    }

    @Override
    public List<Notification> findByPushStatusAndPushAttemptsLessThan(PushStatus pushStatus, Integer pushAttempts) {
        return jpaRepository.findByPushStatusAndPushAttemptsLessThan(pushStatus, pushAttempts);
    }
}
