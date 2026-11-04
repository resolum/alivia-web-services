package com.resolum.alivia.profiles.domain.model.entities;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdMemberStatus;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.shared.domain.model.entities.AuditableModel;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Membership of a user in a household.
 *
 * <p>It lives inside the {@link Household} aggregate and is only reached through its root,
 * which keeps the one-member-per-household invariant. A new membership starts
 * {@link HouseholdMemberStatus#ACTIVE}.</p>
 */
@Entity
@Table(name = "household_members", uniqueConstraints = @UniqueConstraint(
        name = "uk_household_members_household_user",
        columnNames = {"household_id", "user_id"}))
@Getter
public class HouseholdMember extends AuditableModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_household_members_household"))
    private Household household;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HouseholdResponsibility responsibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HouseholdMemberStatus status;

    /**
     * Required by JPA.
     */
    protected HouseholdMember() {
    }

    /**
     * Creates an active membership. Use {@link Household#addMember} so the household
     * validates that the user is not already a member.
     *
     * @param household      the owning household
     * @param userId         the member user
     * @param responsibility the member responsibility
     */
    public HouseholdMember(Household household, UserId userId, HouseholdResponsibility responsibility) {
        if (household == null) {
            throw new IllegalArgumentException("Household cannot be null.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }
        if (responsibility == null) {
            throw new IllegalArgumentException("Responsibility cannot be null.");
        }
        this.household = household;
        this.userId = userId.userId();
        this.responsibility = responsibility;
        this.status = HouseholdMemberStatus.ACTIVE;
    }

    /**
     * Returns the member user.
     *
     * @return the user identifier
     */
    public UserId getUserId() {
        return new UserId(userId);
    }

    /**
     * Indicates whether the membership grants access.
     *
     * @return {@code true} when the membership is active
     */
    public boolean isActive() {
        return status == HouseholdMemberStatus.ACTIVE;
    }

    /**
     * Revokes the membership.
     *
     * @throws IllegalStateException when it is already revoked
     */
    public void revoke() {
        if (status == HouseholdMemberStatus.REVOKED) {
            throw new IllegalStateException("Household membership is already revoked.");
        }
        this.status = HouseholdMemberStatus.REVOKED;
    }

    /**
     * Reactivates a revoked membership.
     *
     * @throws IllegalStateException when it is already active
     */
    public void reactivate() {
        if (status == HouseholdMemberStatus.ACTIVE) {
            throw new IllegalStateException("Household membership is already active.");
        }
        this.status = HouseholdMemberStatus.ACTIVE;
    }
}
