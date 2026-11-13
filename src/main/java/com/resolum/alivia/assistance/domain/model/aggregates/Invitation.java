package com.resolum.alivia.assistance.domain.model.aggregates;

import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverAccountNotAssignedException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationExpiredException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotForUserException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotPendingException;
import com.resolum.alivia.assistance.domain.model.events.InvitationAcceptedEvent;
import com.resolum.alivia.assistance.domain.model.events.InvitationAccountCreatedEvent;
import com.resolum.alivia.assistance.domain.model.events.InvitationCreatedEvent;
import com.resolum.alivia.assistance.domain.model.events.InvitationNotificationRequestedEvent;
import com.resolum.alivia.assistance.domain.model.events.InvitationResentEvent;
import com.resolum.alivia.assistance.domain.model.valueobjects.InvitationStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Invitation aggregate: la invitación de un Relative a un cuidador por correo.
 */
@Entity
@Table(name = "assistance_invitations")
@Getter
public class Invitation extends AuditableAbstractAggregateRoot<Invitation> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assisted_person_id", nullable = false)
    private AssistedPerson assistedPerson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private Relative inviter;

    @Embedded
    @AttributeOverride(name = "email", column = @Column(name = "invitee_email", nullable = false))
    private EmailAddress inviteeEmail;

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "caregiver_user_id"))
    private UserId caregiverUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    protected Invitation() {
    }

    public Invitation(AssistedPerson assistedPerson, Relative inviter, EmailAddress inviteeEmail, LocalDateTime expiresAt) {
        this.assistedPerson = Objects.requireNonNull(assistedPerson, "Assisted person cannot be null.");
        this.inviter = Objects.requireNonNull(inviter, "Inviter cannot be null.");
        this.inviteeEmail = Objects.requireNonNull(inviteeEmail, "Invitee email cannot be null.");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expires at cannot be null.");

        if (!assistedPerson.isManagedBy(inviter)) {
            throw new AssistedPersonNotManagedByRelativeException(assistedPerson.getId(), inviter.getId());
        }

        this.status = InvitationStatus.PENDING;
        addDomainEvent(new InvitationCreatedEvent(this));
    }

    public void assignCaregiverAccount(UserId userId, String passwordSetupUrl) {
        ensurePending();
        if (this.caregiverUserId != null && !this.caregiverUserId.equals(userId)) {
            throw new InvitationNotForUserException();
        }
        this.caregiverUserId = userId;
        addDomainEvent(new InvitationAccountCreatedEvent(getId(), inviteeEmail.email(), passwordSetupUrl));
    }

    public void assignExistingCaregiverAccount(UserId userId) {
        ensurePending();
        if (this.caregiverUserId != null && !this.caregiverUserId.equals(userId)) {
            throw new InvitationNotForUserException();
        }
        this.caregiverUserId = userId;
        addDomainEvent(new InvitationNotificationRequestedEvent(getId(), userId.userId()));
    }

    public void accept(UserId caregiverUserId, LocalDateTime now) {
        ensurePending();
        if (isExpired(now)) {
            throw new InvitationExpiredException();
        }
        if (this.caregiverUserId == null) {
            this.caregiverUserId = caregiverUserId;
        } else if (!this.caregiverUserId.equals(caregiverUserId)) {
            throw new InvitationNotForUserException();
        }
        this.status = InvitationStatus.ACCEPTED;
        addDomainEvent(new InvitationAcceptedEvent(getId(), assistedPerson.getId(), caregiverUserId.userId()));
    }

    public void renew(LocalDateTime newExpiresAt) {
        ensurePending();
        this.expiresAt = Objects.requireNonNull(newExpiresAt, "New expires at cannot be null.");
        addDomainEvent(new InvitationResentEvent(getId()));
    }

    public void revoke() {
        ensurePending();
        this.status = InvitationStatus.REVOKED;
    }

    public boolean isExpired(LocalDateTime now) {
        return now != null && expiresAt != null && now.isAfter(expiresAt);
    }

    private void ensurePending() {
        if (this.status != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException();
        }
    }
}
