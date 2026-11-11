package com.resolum.alivia.communications.application.internal.queryservices;

import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.domain.model.queries.GetAllNotificationsByUserIdQuery;
import com.resolum.alivia.communications.domain.model.queries.GetNotificationByIdQuery;
import com.resolum.alivia.communications.domain.repositories.NotificationRepository;
import com.resolum.alivia.communications.domain.services.NotificationQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public NotificationQueryServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Optional<Notification> handle(GetNotificationByIdQuery query) {
        return notificationRepository.findById(query.notificationId());
    }

    @Override
    public List<Notification> handle(GetAllNotificationsByUserIdQuery query) {
        UserId userId = new UserId(query.userId());
        if (query.status() != null) {
            return notificationRepository.findByRecipientIdAndStatusOrderByCreatedAtDesc(userId, query.status());
        }
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }
}
