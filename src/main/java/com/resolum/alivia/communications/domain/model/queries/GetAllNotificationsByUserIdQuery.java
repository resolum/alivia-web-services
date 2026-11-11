package com.resolum.alivia.communications.domain.model.queries;

import com.resolum.alivia.communications.domain.model.valueobjects.NotificationStatus;

public record GetAllNotificationsByUserIdQuery(Long userId, NotificationStatus status) {
}
