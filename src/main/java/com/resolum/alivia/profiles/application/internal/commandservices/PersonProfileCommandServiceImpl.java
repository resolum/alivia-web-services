package com.resolum.alivia.profiles.application.internal.commandservices;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

@Service
public class PersonProfileCommandServiceImpl implements PersonProfileCommandService {

    private final PersonProfileRepository personProfileRepository;
    private final StorageService storageService;

    public PersonProfileCommandServiceImpl(PersonProfileRepository personProfileRepository) {
        this(personProfileRepository, null);
    }

    @Autowired
    public PersonProfileCommandServiceImpl(PersonProfileRepository personProfileRepository,
                                          @Autowired(required = false) StorageService storageService) {
        this.personProfileRepository = personProfileRepository;
        this.storageService = storageService;
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

        Photo photo = resolvePhoto(command.photoData(), command.photoFileName(), null);
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
        Photo photo = resolvePhoto(command.photoData(), command.photoFileName(), personProfile.getPhoto());

        personProfile.updatePersonalInformation(personName, birthDate, phoneNumber, photo);

        var updatedProfile = personProfileRepository.save(personProfile);
        return updatedProfile.getId();
    }

    private Photo resolvePhoto(byte[] photoData, String photoFileName, Photo existingPhoto) {
        if (photoData != null && photoData.length > 0) {
            if (storageService != null) {
                Map<String, String> uploaded = storageService.upload(photoData, photoFileName);
                if (uploaded != null) {
                    String url = uploaded.get("url") != null ? uploaded.get("url") : uploaded.get("secure_url");
                    String publicId = uploaded.get("publicId") != null ? uploaded.get("publicId") : uploaded.get("public_id");
                    if (url != null && publicId != null) {
                        return new Photo(url, publicId);
                    }
                }
            }
            return new Photo("https://res.cloudinary.com/placeholder", "placeholder");
        }
        return existingPhoto != null ? existingPhoto : new Photo("https://res.cloudinary.com/placeholder", "placeholder");
    }
}
