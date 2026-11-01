package com.resolum.alivia.profiles.domain.model.aggregates;

import com.resolum.alivia.profiles.domain.model.valueobjects.ProfilePreferences;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@MappedSuperclass
@Getter
@Setter
public class Profile extends AuditableAbstractAggregateRoot<Profile> {

    @Embedded
    private EmailAddress emailAddress;
    @Embedded
    private Photo photo;
    @Embedded
    private PhoneNumber phoneNumber;

    @Setter(AccessLevel.NONE)
    @Column(name = "user_id")
    private Long userId;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences")
    private ProfilePreferences preferences;

    /**
     * Required by JPA.
     */
    protected Profile() {
    }

    Profile(EmailAddress emailAddress, Photo photo, PhoneNumber phoneNumber) {
        if (emailAddress == null) {
            throw new IllegalArgumentException("Email address cannot be null");
        }
        if (photo == null) {
            throw new IllegalArgumentException("Photo cannot be null");
        }
        if (phoneNumber == null) {
            throw new IllegalArgumentException("Phone number cannot be null");
        }
        this.emailAddress = emailAddress;
        this.photo = photo;
        this.phoneNumber = phoneNumber;
    }

    public void updateContactInformation(PhoneNumber phoneNumber, Photo photo) {
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (photo != null) {
            this.photo = photo;
        }
    }

    /**
     * Links the profile to the IAM user that owns it.
     *
     * @param userId the owning user
     * @throws IllegalArgumentException when the user is null
     * @throws IllegalStateException    when the profile is already linked to another user
     */
    public void linkToUser(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id cannot be null.");
        }
        if (this.userId != null && !this.userId.equals(userId.userId())) {
            throw new IllegalStateException("Profile is already linked to another user.");
        }
        this.userId = userId.userId();
    }

    /**
     * Replaces the communication preferences.
     *
     * @param preferences the new preferences
     * @throws IllegalArgumentException when the preferences are null
     */
    public void updatePreferences(ProfilePreferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("Preferences cannot be null.");
        }
        this.preferences = preferences;
    }

}
