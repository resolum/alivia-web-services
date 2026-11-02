package com.resolum.alivia.nursing.domain.model.aggregates;

import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.MembershipStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.util.Objects;

/**
 * Link between a caregiver or relative and an assisted person.
 *
 * <p>The member keeps both identifiers: its IAM user, used to resolve access from the
 * authenticated user, and its person profile, used to read its data from Profiles. A new
 * membership starts {@link MembershipStatus#ACTIVE}.</p>
 */
@Entity
@Table(name = "care_memberships", uniqueConstraints = @UniqueConstraint(
        name = "uk_care_memberships_user_person_role",
        columnNames = {"user_id", "assisted_person_profile_id", "role"}))
@Getter
public class CareMembership extends AuditableAbstractAggregateRoot<CareMembership> {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "member_person_profile_id", nullable = false)
    private Long memberPersonProfileId;

    @Column(name = "assisted_person_profile_id", nullable = false)
    private Long assistedPersonProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private CareRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    protected CareMembership() {
    }

    /**
     * Creates an active membership.
     *
     * @param userId                  the member user
     * @param memberPersonProfileId   the member person profile
     * @param assistedPersonProfileId the assisted person profile
     * @param role                    the role of the member
     */
    public CareMembership(UserId userId, PersonProfileId memberPersonProfileId,
                          PersonProfileId assistedPersonProfileId, CareRole role) {
        this.userId = Objects.requireNonNull(userId, "User cannot be null.").userId();
        this.memberPersonProfileId = Objects.requireNonNull(memberPersonProfileId,
                "Member profile cannot be null.").personProfileId();
        this.assistedPersonProfileId = Objects.requireNonNull(assistedPersonProfileId,
                "Assisted person cannot be null.").personProfileId();
        this.role = Objects.requireNonNull(role, "Role cannot be null.");
        this.status = MembershipStatus.ACTIVE;
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
     * Returns the member person profile.
     *
     * @return the member person profile identifier
     */
    public PersonProfileId getMemberPersonProfileId() {
        return new PersonProfileId(memberPersonProfileId);
    }

    /**
     * Returns the assisted person profile.
     *
     * @return the assisted person profile identifier
     */
    public PersonProfileId getAssistedPersonProfileId() {
        return new PersonProfileId(assistedPersonProfileId);
    }
}
