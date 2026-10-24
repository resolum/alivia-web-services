package com.resolum.alivia.profiles.domain.model.aggregates;

import java.sql.Date;

import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

public class PersonProfile extends Profile {
    private PersonName name;
    private Dni dni;
    private BirthDate birthDate;

    public PersonProfile(EmailAddress emailAddress, Photo photo, PhoneNumber phoneNumber, String gender,
            PersonName name,
            Dni dni, BirthDate birthDate) {
        super(emailAddress, photo, phoneNumber);
        this.name = name;
        this.dni = dni;
        this.birthDate = birthDate;
    }

    public void updatePersonalInformation(PersonName personName, BirthDate birthDate, PhoneNumber phoneNumber,
            Photo photo) {
        this.name = personName;
        this.birthDate = birthDate;
        super.setPhoneNumber(phoneNumber);
        super.setPhoto(photo);
    }
}
