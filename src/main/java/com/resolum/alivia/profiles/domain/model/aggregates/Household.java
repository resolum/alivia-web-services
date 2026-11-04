package com.resolum.alivia.profiles.domain.model.aggregates;

import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdMembers;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

/**
 * Household account: alias, validated location and contact data.
 *
 * <p>The members live inside the aggregate so the one-member-per-household invariant is
 * enforced by {@link #addMember}. US-42 requires alias, contact phone, contact email and
 * location when the household profile is created.</p>
 */
@Entity
@Getter
public class Household extends AuditableAbstractAggregateRoot<Household> {

    @Column(nullable = false, length = 120)
    private String alias;

    @Embedded
    private Address address;

    @Embedded
    @AttributeOverride(name = "phoneNumber", column = @Column(name = "contact_phone"))
    private PhoneNumber contactPhone;

    @Embedded
    @AttributeOverride(name = "email", column = @Column(name = "contact_email"))
    private EmailAddress contactEmail;

    @Embedded
    private HouseholdMembers householdMembers;

    /**
     * Required by JPA.
     */
    protected Household() {
        this.householdMembers = new HouseholdMembers();
    }

    /**
     * Creates a household.
     *
     * @param alias        the household alias
     * @param address      the validated location
     * @param contactPhone the contact phone
     * @param contactEmail the contact email
     */
    public Household(String alias, Address address, PhoneNumber contactPhone, EmailAddress contactEmail) {
        this();
        updateGeneralInformation(alias, contactPhone, contactEmail);
        updateLocation(address);
    }

    /**
     * Updates the alias and contact data.
     *
     * @param alias        the new alias
     * @param contactPhone the new contact phone
     * @param contactEmail the new contact email
     */
    public void updateGeneralInformation(String alias, PhoneNumber contactPhone, EmailAddress contactEmail) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Household alias cannot be null or blank.");
        }
        var normalizedAlias = alias.strip();
        if (normalizedAlias.length() > 120) {
            throw new IllegalArgumentException("Household alias cannot exceed 120 characters.");
        }
        if (contactPhone == null) {
            throw new IllegalArgumentException("Contact phone cannot be null.");
        }
        if (contactEmail == null) {
            throw new IllegalArgumentException("Contact email cannot be null.");
        }
        this.alias = normalizedAlias;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
    }

    /**
     * Updates the household location.
     *
     * @param address the new validated location
     */
    public void updateLocation(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("Household address cannot be null.");
        }
        this.address = address;
    }

    /**
     * Adds an active member to the household.
     *
     * @param userId         the member user
     * @param responsibility the member responsibility
     * @return the new member
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdMemberAlreadyExistsException
     *         when the user is already a member
     */
    public HouseholdMember addMember(UserId userId, HouseholdResponsibility responsibility) {
        return householdMembers.addMember(this, userId, responsibility);
    }

    /**
     * Finds a member by user.
     *
     * @param userId the member user
     * @return the member, or empty when the user does not belong to the household
     */
    public Optional<HouseholdMember> findMember(UserId userId) {
        return householdMembers.findMember(userId);
    }

    /**
     * Returns every member.
     *
     * @return an unmodifiable copy of the members
     */
    public List<HouseholdMember> getMembers() {
        return householdMembers.getMembers();
    }
}
