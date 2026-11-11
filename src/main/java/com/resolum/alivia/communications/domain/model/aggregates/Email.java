package com.resolum.alivia.communications.domain.model.aggregates;

import com.resolum.alivia.communications.domain.exceptions.InvalidEmailStatusTransition;
import com.resolum.alivia.communications.domain.exceptions.MissingTemplateVariable;
import com.resolum.alivia.communications.domain.model.valueobjects.EmailStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.EmailTemplate;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Entity
@Table(name = "emails")
public class Email extends AuditableAbstractAggregateRoot<Email> {

    @Embedded
    @AttributeOverride(name = "email", column = @Column(name = "email_address", nullable = false, length = 254))
    private EmailAddress emailAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "template", nullable = false, length = 30)
    private EmailTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EmailStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected Email() {
        // JPA required
    }

    public Email(EmailAddress emailAddress, EmailTemplate template, Map<String, String> variables) {
        if (emailAddress == null) {
            throw new IllegalArgumentException("Email address cannot be null");
        }
        if (template == null) {
            throw new IllegalArgumentException("Email template cannot be null");
        }
        for (String var : template.requiredVariables()) {
            if (variables == null || !variables.containsKey(var) || variables.get(var) == null || variables.get(var).isBlank()) {
                throw new MissingTemplateVariable(template, var);
            }
        }
        this.emailAddress = emailAddress;
        this.template = template;
        this.status = EmailStatus.PENDING;
    }

    public void markSent(LocalDateTime now) {
        if (this.status != EmailStatus.PENDING) {
            throw new InvalidEmailStatusTransition(this.status, EmailStatus.SENT);
        }
        this.status = EmailStatus.SENT;
        this.sentAt = now != null ? now : LocalDateTime.now();
    }

    public void markFailed(String failureReason) {
        if (this.status != EmailStatus.PENDING) {
            throw new InvalidEmailStatusTransition(this.status, EmailStatus.FAILED);
        }
        this.status = EmailStatus.FAILED;
        if (failureReason == null || failureReason.isBlank()) {
            failureReason = "Unknown delivery error";
        }
        this.failureReason = failureReason.length() > 500 ? failureReason.substring(0, 500) : failureReason;
    }
}
