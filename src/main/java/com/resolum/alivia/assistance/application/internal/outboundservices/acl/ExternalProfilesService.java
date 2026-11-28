package com.resolum.alivia.assistance.application.internal.outboundservices.acl;

import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound ACL service for communicating with Profiles bounded context.
 */
@Slf4j
@Service("assistanceExternalProfilesService")
public class ExternalProfilesService {

    private final ProfilesContextFacade profilesContextFacade;
    private final String defaultPhotoUrl;

    public ExternalProfilesService(ProfilesContextFacade profilesContextFacade,
                                   @Value("${alivia.iam.profiles.default-photo-url:}") String defaultPhotoUrl) {
        this.profilesContextFacade = profilesContextFacade;
        this.defaultPhotoUrl = defaultPhotoUrl;
    }

    public boolean existsPersonProfileByDni(String dni) {
        if (dni == null || dni.isBlank()) {
            return false;
        }
        return profilesContextFacade.existsPersonProfileByDni(dni);
    }

    public boolean existsPersonProfileByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return profilesContextFacade.existsPersonProfileByEmailAddress(email);
    }

    public Long createPersonProfile(String emailAddress, byte[] photoData, String photoFileName,
                                    String phoneNumber, String firstName, String lastName,
                                    String dni, LocalDate birthDate) {
        if (photoData == null || photoData.length == 0) {
            // No upload: the person starts with the shared default photo, as staff and relatives do.
            return profilesContextFacade.createPersonProfile(emailAddress, defaultPhotoUrl, publicIdOf(defaultPhotoUrl),
                    phoneNumber, firstName, lastName, dni, birthDate);
        }
        return profilesContextFacade.createPersonProfileWithPhoto(emailAddress, photoData, photoFileName,
                phoneNumber, firstName, lastName, dni, birthDate);
    }

    // Cloudinary public id of a photo url: the path after /upload/ without the version and the extension.
    static String publicIdOf(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return "default-photo";
        }
        var path = URI.create(photoUrl).getPath();
        int upload = path.indexOf("/upload/");
        var id = upload < 0 ? path.substring(path.lastIndexOf('/') + 1)
                : path.substring(upload + "/upload/".length()).replaceFirst("^v\\d+/", "");
        int dot = id.lastIndexOf('.');
        id = dot > 0 ? id.substring(0, dot) : id;
        return id.isBlank() ? "default-photo" : id;
    }

    public void updatePersonProfile(Long profileId, byte[] photoData, String photoFileName,
                                    String phoneNumber, String firstName, String lastName,
                                    LocalDate birthDate) {
        profilesContextFacade.updatePersonProfileWithPhoto(profileId, photoData, photoFileName,
                phoneNumber, firstName, lastName, birthDate);
    }

    public Optional<ProfilesContextFacade.PersonProfileData> fetchPersonProfile(PersonProfileId profileId) {
        if (profileId == null || profileId.personProfileId() == null) {
            return Optional.empty();
        }
        return profilesContextFacade.fetchPersonProfileById(profileId.personProfileId());
    }

    public Map<PersonProfileId, String> fetchPersonNames(Set<PersonProfileId> personProfileIds) {
        log.debug("Fetching person names from Profiles for {} profiles", personProfileIds.size());
        var names = new HashMap<PersonProfileId, String>();
        for (PersonProfileId profileId : personProfileIds) {
            profilesContextFacade.fetchPersonProfileFullNameById(profileId.personProfileId())
                    .ifPresent(name -> names.put(profileId, name));
        }
        return names;
    }
}
