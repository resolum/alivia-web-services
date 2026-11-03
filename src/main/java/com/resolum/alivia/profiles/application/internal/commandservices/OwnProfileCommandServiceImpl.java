package com.resolum.alivia.profiles.application.internal.commandservices;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.exceptions.OwnProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.Profile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.commands.UpdateOwnProfileCommand;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.model.valueobjects.ProfilePreferences;
import com.resolum.alivia.profiles.domain.services.OwnProfileCommandService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

/**
 * Implementation of {@link OwnProfileCommandService}.
 *
 * <p>The service resolves the profile from the authenticated user, never from a profile
 * identifier, so a user cannot edit another user's profile.</p>
 */
@Service
public class OwnProfileCommandServiceImpl implements OwnProfileCommandService {

    private final PersonProfileRepository personProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final StorageService storageService;

    public OwnProfileCommandServiceImpl(PersonProfileRepository personProfileRepository,
                                        StaffProfileRepository staffProfileRepository) {
        this(personProfileRepository, staffProfileRepository, null);
    }

    @Autowired
    public OwnProfileCommandServiceImpl(PersonProfileRepository personProfileRepository,
                                        StaffProfileRepository staffProfileRepository,
                                        @Autowired(required = false) StorageService storageService) {
        this.personProfileRepository = personProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.storageService = storageService;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public Long handle(UpdateOwnProfileCommand command) {
        var personProfile = personProfileRepository.findByUserId(command.userId());
        if (personProfile.isPresent()) {
            return update(personProfile.get(), command);
        }
        var staffProfile = staffProfileRepository.findByUserId(command.userId())
                .orElseThrow(() -> new OwnProfileNotFoundException(command.userId()));
        return update(staffProfile, command);
    }

    private Long update(PersonProfile profile, UpdateOwnProfileCommand command) {
        var personName = resolveName(profile.getPersonName(), command);
        var phoneNumber = resolvePhoneNumber(profile.getPhoneNumber(), command);
        var photo = resolvePhoto(command.photoData(), command.photoFileName(), profile.getPhoto());

        profile.updatePersonalInformation(personName, profile.getBirthDate(), phoneNumber, photo);
        updatePreferences(profile, command);
        return personProfileRepository.save(profile).getId();
    }

    private Long update(StaffProfile profile, UpdateOwnProfileCommand command) {
        var personName = resolveName(profile.getPersonName(), command);
        var phoneNumber = resolvePhoneNumber(profile.getPhoneNumber(), command);
        var photo = resolvePhoto(command.photoData(), command.photoFileName(), profile.getPhoto());

        profile.updatePersonalInformation(personName, profile.getBirthDate(), phoneNumber, photo);
        updatePreferences(profile, command);
        return staffProfileRepository.save(profile).getId();
    }

    private static PersonName resolveName(PersonName currentName, UpdateOwnProfileCommand command) {
        var firstName = command.firstName() != null ? command.firstName() : currentName.firstName();
        var lastName = command.lastName() != null ? command.lastName() : currentName.lastName();
        return new PersonName(firstName, lastName);
    }

    private static PhoneNumber resolvePhoneNumber(PhoneNumber currentPhoneNumber,
                                                  UpdateOwnProfileCommand command) {
        return command.phoneNumber() != null ? new PhoneNumber(command.phoneNumber()) : currentPhoneNumber;
    }

    private static void updatePreferences(Profile profile, UpdateOwnProfileCommand command) {
        if (command.preferences() != null) {
            profile.updatePreferences(new ProfilePreferences(command.preferences()));
        }
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
