package com.resolum.alivia.profiles.domain.model.aggregates;

import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class StaffProfile extends Profile {
    @Embedded
    private PersonName personName;
    @Embedded
    private Dni dni;
    @Embedded
    private BirthDate birthDate;

    public StaffProfile(EmailAddress emailAddress, Photo photo, PhoneNumber phoneNumber, PersonName personName, Dni dni,
            BirthDate birthDate) {
        super(emailAddress, photo, phoneNumber);
        this.personName = personName;
        this.dni = dni;
        this.birthDate = birthDate;
    }

    public void updatePersonalInformation(PersonName personName, BirthDate birthDate, PhoneNumber phoneNumber,
            Photo photo) {
        this.personName = personName;
        this.birthDate = birthDate;
        super.setPhoneNumber(phoneNumber);
        super.setPhoto(photo);
    }

}
