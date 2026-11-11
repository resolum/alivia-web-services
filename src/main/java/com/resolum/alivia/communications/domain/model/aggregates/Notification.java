package com.resolum.alivia.communications.domain.model.aggregates;

import com.resolum.alivia.communications.domain.exceptions.InvalidMessageContent;
import com.resolum.alivia.communications.domain.exceptions.NotificationNotForUser;
import com.resolum.alivia.communications.domain.model.events.NotificationCreatedEvent;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;
import com.resolum.alivia.communications.domain.model.valueobjects.PushStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notifications")
public class Notification extends AuditableAbstractAggregateRoot<Notification> {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "recipient_user_id", nullable = false))
    private UserId recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 25)
    private NotificationType type;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false, length = 12)
    private PushStatus pushStatus;

    @Column(name = "push_attempts", nullable = false)
    private Integer pushAttempts;

    protected Notification() {
        // JPA required
    }

    public Notification(UserId recipientId, NotificationType type, String sourceId, String title, String body) {
        if (recipientId == null) {
            throw new IllegalArgumentException("Recipient user id cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Notification type cannot be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("Source id cannot be blank");
        }
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new InvalidMessageContent();
        }
        this.recipientId = recipientId;
        this.type = type;
        this.sourceId = sourceId.trim();
        this.title = title.trim();
        this.body = body.trim();
        this.status = NotificationStatus.UNREAD;
        this.pushStatus = PushStatus.PENDING;
        this.pushAttempts = 0;
        this.addDomainEvent(new NotificationCreatedEvent(this));
    }

    public void markAsRead(UserId userId, LocalDateTime now) {
        if (userId == null || !this.recipientId.equals(userId)) {
            throw new NotificationNotForUser(this.getId());
        }
        if (this.status == NotificationStatus.READ) {
            return;
        }
        this.status = NotificationStatus.READ;
        this.readAt = now != null ? now : LocalDateTime.now();
    }

    public void markPushSent() {
        this.pushStatus = PushStatus.SENT;
    }

    public void markPushUndelivered() {
        this.pushStatus = PushStatus.UNDELIVERED;
        this.pushAttempts = (this.pushAttempts == null ? 0 : this.pushAttempts) + 1;
    }

    public boolean canRetryPush(Integer maxAttempts) {
        return this.pushStatus == PushStatus.UNDELIVERED && (this.pushAttempts != null && this.pushAttempts < maxAttempts);
    }
}
