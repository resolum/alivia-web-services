package com.resolum.alivia.assistance.application.internal.outboundservices.acl;

import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Outbound ACL service for communicating with Profiles bounded context.
 */
@Slf4j
@Service("assistanceExternalProfilesService")
public class ExternalProfilesService {

    private final ProfilesContextFacade profilesContextFacade;

    public ExternalProfilesService(ProfilesContextFacade profilesContextFacade) {
        this.profilesContextFacade = profilesContextFacade;
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
        return profilesContextFacade.createPersonProfileWithPhoto(emailAddress, photoData, photoFileName,
                phoneNumber, firstName, lastName, dni, birthDate);
    }

    public void updatePersonProfile(Long profileId, byte[] photoData, String photoFileName,
                                    String phoneNumber, String firstName, String lastName,
                                    LocalDate birthDate) {
        profilesContextFacade.updatePersonProfileWithPhoto(profileId, photoData, photoFileName,
                phoneNumber, firstName, lastName, birthDate);
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
