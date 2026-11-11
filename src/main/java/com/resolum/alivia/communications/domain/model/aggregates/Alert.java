package com.resolum.alivia.communications.domain.model.aggregates;

import com.resolum.alivia.communications.domain.exceptions.InvalidAlertStatusTransition;
import com.resolum.alivia.communications.domain.exceptions.InvalidMessageContent;
import com.resolum.alivia.communications.domain.model.events.AlertCreatedEvent;
import com.resolum.alivia.communications.domain.model.valueobjects.AlertStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "alerts")
public class Alert extends AuditableAbstractAggregateRoot<Alert> {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "recipient_user_id", nullable = false))
    private UserId recipientId;

    @Column(name = "help_request_id", nullable = false, length = 64)
    private String helpRequestId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AlertStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    protected Alert() {
        // JPA required
    }

    public Alert(UserId recipientId, String helpRequestId, String title, String body) {
        if (recipientId == null) {
            throw new IllegalArgumentException("Recipient user id cannot be null");
        }
        if (helpRequestId == null || helpRequestId.isBlank()) {
            throw new IllegalArgumentException("Help request id cannot be blank");
        }
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new InvalidMessageContent();
        }
        this.recipientId = recipientId;
        this.helpRequestId = helpRequestId.trim();
        this.title = title.trim();
        this.body = body.trim();
        this.status = AlertStatus.PENDING;
        this.attempts = 0;
        this.addDomainEvent(new AlertCreatedEvent(this));
    }

    public void markSent(LocalDateTime now) {
        if (this.status != AlertStatus.PENDING && this.status != AlertStatus.UNDELIVERED) {
            throw new InvalidAlertStatusTransition(this.status, AlertStatus.SENT);
        }
        this.status = AlertStatus.SENT;
        this.sentAt = now != null ? now : LocalDateTime.now();
    }

    public void markUndelivered() {
        if (this.status != AlertStatus.PENDING && this.status != AlertStatus.UNDELIVERED) {
            throw new InvalidAlertStatusTransition(this.status, AlertStatus.UNDELIVERED);
        }
        this.status = AlertStatus.UNDELIVERED;
        this.attempts = (this.attempts == null ? 0 : this.attempts) + 1;
    }

    public boolean canRetry(Integer maxAttempts) {
        return this.status == AlertStatus.UNDELIVERED && (this.attempts != null && this.attempts < maxAttempts);
    }
}
