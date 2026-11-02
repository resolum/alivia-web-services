package com.resolum.alivia.profiles.application.internal.commandservices;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.PersonProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.commands.CreatePersonProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdatePersonProfileCommand;
import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.services.PersonProfileCommandService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

@Service
public class PersonProfileCommandServiceImpl implements PersonProfileCommandService {

    private final PersonProfileRepository personProfileRepository;

    public PersonProfileCommandServiceImpl(PersonProfileRepository personProfileRepository) {
        this.personProfileRepository = personProfileRepository;
    }

    @Transactional
    @Override
    public Long handle(CreatePersonProfileCommand command) {
        var emailAddress = new EmailAddress(command.emailAddress());
        if (personProfileRepository.existsByEmailAddress(emailAddress)) {
            throw new EmailAddressAlreadyExistsException(emailAddress);
        }

        var dni = new Dni(command.dni());
        if (personProfileRepository.existsByDni(dni)) {
            throw new DniAlreadyExistsException(dni);
        }

        var photo = new Photo(command.photoUrl(), command.photoPublicId());
        var phoneNumber = new PhoneNumber(command.phoneNumber());
        var personName = new PersonName(command.firstName(), command.lastName());
        var birthDate = new BirthDate(command.birthDate());

        var personProfile = new PersonProfile(
                emailAddress,
                photo,
                phoneNumber,
                personName,
                dni,
                birthDate);

        var savedProfile = personProfileRepository.save(personProfile);
        return savedProfile.getId();
    }

    @Transactional
    @Override
    public Long handle(UpdatePersonProfileCommand command) {
        var personProfile = personProfileRepository.findById(command.profileId())
                .orElseThrow(() -> new PersonProfileNotFoundException(command.profileId()));

        var personName = new PersonName(command.firstName(), command.lastName());
        var birthDate = new BirthDate(command.birthDate());
        var phoneNumber = new PhoneNumber(command.phoneNumber());
        var photo = new Photo(command.photoUrl(), command.photoPublicId());

        personProfile.updatePersonalInformation(personName, birthDate, phoneNumber, photo);

        var updatedProfile = personProfileRepository.save(personProfile);
        return updatedProfile.getId();
    }
}
