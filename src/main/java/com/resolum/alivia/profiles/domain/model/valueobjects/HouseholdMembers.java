package com.resolum.alivia.profiles.domain.model.valueobjects;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdMemberAlreadyExistsException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Component embedded in {@link Household} that groups its members.
 *
 * <p>It is not a strict value object: it is mutable and holds entities with identity. It
 * enforces that a user appears only once per household.</p>
 */
@Embeddable
public class HouseholdMembers {

    @OneToMany(mappedBy = "household", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HouseholdMember> members;

    /**
     * Creates an empty component.
     */
    public HouseholdMembers() {
        this.members = new ArrayList<>();
    }

    /**
     * Returns every member.
     *
     * @return an unmodifiable copy of the members
     */
    public List<HouseholdMember> getMembers() {
        return List.copyOf(members);
    }

    /**
     * Finds a member by user.
     *
     * @param userId the member user
     * @return the member, or empty when the user does not belong to the household
     */
    public Optional<HouseholdMember> findMember(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }
        return members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst();
    }

    /**
     * Adds an active member.
     *
     * @param household      the owning household
     * @param userId         the member user
     * @param responsibility the member responsibility
     * @return the new member
     * @throws HouseholdMemberAlreadyExistsException when the user is already a member
     */
    public HouseholdMember addMember(Household household, UserId userId, HouseholdResponsibility responsibility) {
        if (household == null) {
            throw new IllegalArgumentException("Household cannot be null.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }
        if (responsibility == null) {
            throw new IllegalArgumentException("Responsibility cannot be null.");
        }
        if (findMember(userId).isPresent()) {
            throw new HouseholdMemberAlreadyExistsException(userId);
        }
        var member = new HouseholdMember(household, userId, responsibility);
        members.add(member);
        return member;
    }
}
