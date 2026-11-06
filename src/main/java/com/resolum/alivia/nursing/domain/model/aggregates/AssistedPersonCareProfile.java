package com.resolum.alivia.nursing.domain.model.aggregates;

import com.resolum.alivia.nursing.domain.model.commands.SaveAssistedPersonCareProfileCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.IdentityDocumentType;
import com.resolum.alivia.nursing.domain.model.valueobjects.KinshipToCaregiver;
import com.resolum.alivia.nursing.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Care profile of the assisted person, filled in by the responsible client while contracting.
 *
 * <p>Unlike the assisted person registered in a household (which gets its own account), this
 * profile needs no email or password: it records who will use Alivia and how to adapt the
 * service to them. A responsible client keeps one profile; saving again replaces its data.</p>
 */
@Entity
@Table(name = "assisted_person_care_profiles", uniqueConstraints = @UniqueConstraint(
        name = "uk_assisted_person_care_profiles_registered_by", columnNames = "registered_by_user_id"))
@Getter
public class AssistedPersonCareProfile extends AuditableAbstractAggregateRoot<AssistedPersonCareProfile> {

    @Column(name = "registered_by_user_id", nullable = false, updatable = false)
    private Long registeredByUserId;

    @Column(name = "household_id")
    private Long householdId;

    @Column(name = "first_name", nullable = false, length = 75)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 75)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 10)
    private IdentityDocumentType documentType;

    @Column(name = "document_number", length = 12)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kinship", nullable = false, length = 20)
    private KinshipToCaregiver kinship;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "mobility_level", nullable = false, length = 20)
    private MobilityLevel mobilityLevel;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 15)
    private String emergencyContactPhone;

    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    protected AssistedPersonCareProfile() {
    }

    /**
     * Creates the profile of the responsible client in the command.
     *
     * @param command the validated command
     * @param now     when the consent was given
     */
    public AssistedPersonCareProfile(SaveAssistedPersonCareProfileCommand command, Instant now) {
        this.registeredByUserId = Objects.requireNonNull(command, "Command cannot be null.")
                .requesterUserId().userId();
        update(command, now);
    }

    /**
     * Replaces the data with the command. The consent date moves to {@code now}, because the
     * client confirms it again each time they save.
     *
     * @param command the validated command
     * @param now     when the consent was given
     */
    public void update(SaveAssistedPersonCareProfileCommand command, Instant now) {
        Objects.requireNonNull(command, "Command cannot be null.");
        if (!command.requesterUserId().userId().equals(registeredByUserId)) {
            throw new IllegalArgumentException("Only the client who registered the profile can change it.");
        }
        this.householdId = command.householdId();
        this.firstName = command.firstName();
        this.lastName = command.lastName();
        this.birthDate = command.birthDate();
        this.documentType = command.documentType();
        this.documentNumber = command.documentNumber();
        this.kinship = command.kinship();
        this.phoneNumber = command.phoneNumber();
        this.mobilityLevel = command.mobilityLevel();
        this.emergencyContactName = command.emergencyContactName();
        this.emergencyContactPhone = command.emergencyContactPhone();
        this.consentGivenAt = Objects.requireNonNull(now, "Consent time cannot be null.");
    }

    /**
     * Returns the responsible client who registered the profile.
     *
     * @return the user identifier
     */
    public UserId getRegisteredByUserId() {
        return new UserId(registeredByUserId);
    }
}
