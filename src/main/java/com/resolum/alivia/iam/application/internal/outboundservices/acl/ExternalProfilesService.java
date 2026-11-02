package com.resolum.alivia.iam.application.internal.outboundservices.acl;

import com.resolum.alivia.iam.domain.exceptions.DniAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.ProfileData;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.net.URI;

/**
 * Outbound service that creates the profile of a newly registered user in the
 * Profiles bounded context through {@link ProfilesContextFacade}.
 *
 * <p>
 * Technicians get a staff profile; caregivers and assisted persons get a person
 * profile. Administrators and subscription managers get no profile. The photo sent
 * at sign-up is uploaded to the storage service; without one the profile gets the
 * configured default photo.
 * </p>
 */
@Slf4j
@Service
public class ExternalProfilesService {

    private final ProfilesContextFacade profilesContextFacade;
    private final StorageService storageService;
    private final String defaultPhotoUrl;
    private final String defaultPhotoPublicId;

    public ExternalProfilesService(ProfilesContextFacade profilesContextFacade, StorageService storageService,
                                   @Value("${alivia.iam.profiles.default-photo-url}") String defaultPhotoUrl) {
        this.profilesContextFacade = profilesContextFacade;
        this.storageService = storageService;
        this.defaultPhotoUrl = defaultPhotoUrl;
        this.defaultPhotoPublicId = publicIdOf(defaultPhotoUrl);
    }

    /**
     * Creates the profile associated with a newly registered user.
     *
     * @param userId      the identifier of the new user
     * @param email       the email address of the new user
     * @param role        the access role of the new user
     * @param profileData the profile data provided during registration
     * @return the created profile identifier, or {@code null} when the role has no profile
     * @throws IllegalArgumentException        if the user identifier, the email, the role
     *                                         or the profile data is missing
     * @throws SignUpValidationException       if the role needs a profile and the DNI or the
     *                                         birth date is missing, or the phone number is not
     *                                         a valid international number
     * @throws EmailAlreadyRegisteredException if a profile of the same kind already has the email
     * @throws DniAlreadyRegisteredException   if a person or staff profile already has the DNI
     */
    public Long createProfile(Long userId, String email, AccessRole role, ProfileData profileData) {
        if (userId == null) {
            throw new IllegalArgumentException("User identifier is required.");
        }
        if (email == null || role == null) {
            throw new IllegalArgumentException("Email and role are required.");
        }
        if (profileData == null) {
            throw new IllegalArgumentException("Profile data is required.");
        }
        if (role != AccessRole.TECHNICIAN && role != AccessRole.CAREGIVER && role != AccessRole.ASSISTED_PERSON) {
            log.info("No profile required for account id {} with role {}", userId, role);
            return null;
        }
        validate(profileData);
        var phoneNumber = toProfilesPhoneNumber(profileData.phoneNumber());
        boolean staff = role == AccessRole.TECHNICIAN;
        // Checked here so duplicates answer 409; Profiles would only throw its own internal exceptions.
        if (staff ? profilesContextFacade.existsStaffProfileByEmailAddress(email)
                : profilesContextFacade.existsPersonProfileByEmailAddress(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        // A DNI identifies one person, so it is unique across person and staff profiles.
        if (profilesContextFacade.existsPersonProfileByDni(profileData.dni())
                || profilesContextFacade.existsStaffProfileByDni(profileData.dni())) {
            throw new DniAlreadyRegisteredException();
        }

        var photoUrl = defaultPhotoUrl;
        var photoPublicId = defaultPhotoPublicId;
        if (profileData.photo() != null) {
            var uploaded = storageService.upload(profileData.photo(), profileData.photoFileName());
            photoUrl = uploaded.get("url");
            photoPublicId = uploaded.get("publicId");
            deletePhotoOnRollback(photoPublicId);
        }

        Long profileId = staff
                ? profilesContextFacade.createStaffProfile(email, photoUrl, photoPublicId, phoneNumber,
                        profileData.firstName(), profileData.lastName(), profileData.dni(), profileData.birthDate())
                : profilesContextFacade.createPersonProfile(email, photoUrl, photoPublicId, phoneNumber,
                        profileData.firstName(), profileData.lastName(), profileData.dni(), profileData.birthDate());
        log.info("Created profile id {} for account id {} with role {}", profileId, userId, role);
        return profileId;
    }

    // The upload is not part of the database transaction, so a rolled back sign-up removes its photo.
    private void deletePhotoOnRollback(String photoPublicId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    storageService.delete(photoPublicId);
                } catch (RuntimeException exception) {
                    log.warn("Could not delete photo {} of a rolled back sign-up", photoPublicId);
                }
            }
        });
    }

    private static void validate(ProfileData profileData) {
        if (isBlank(profileData.dni())) {
            throw new SignUpValidationException("dni", SignUpValidationException.REQUIRED);
        }
        if (profileData.birthDate() == null) {
            throw new SignUpValidationException("birthDate", SignUpValidationException.REQUIRED);
        }
    }

    // Profiles stores E.164 digits without the plus sign, e.g. 51987654321.
    private static String toProfilesPhoneNumber(String phoneNumber) {
        var digits = phoneNumber.replaceAll("[+\\s-]", "");
        try {
            return new PhoneNumber(digits).phoneNumber();
        } catch (IllegalArgumentException exception) {
            throw new SignUpValidationException("phoneNumber", "invalid_phone_number");
        }
    }

    // Cloudinary public id of the default photo: the path after /upload/ without the version and the extension.
    private static String publicIdOf(String photoUrl) {
        var path = URI.create(photoUrl).getPath();
        int upload = path.indexOf("/upload/");
        var id = upload < 0 ? path.substring(path.lastIndexOf('/') + 1)
                : path.substring(upload + "/upload/".length()).replaceFirst("^v\\d+/", "");
        int dot = id.lastIndexOf('.');
        id = dot > 0 ? id.substring(0, dot) : id;
        if (id.isBlank()) {
            throw new IllegalStateException("alivia.iam.profiles.default-photo-url has no public id: " + photoUrl);
        }
        return id;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Full name of a staff profile, shown to an invited person before they activate the account. */
    public Optional<String> fetchStaffFullName(Long profileId) {
        return profileId == null ? Optional.empty() : profilesContextFacade.fetchStaffProfileFullNameById(profileId);
    }
}
