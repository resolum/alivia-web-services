package com.resolum.alivia.profiles.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

import jakarta.persistence.Embedded;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;

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

}
