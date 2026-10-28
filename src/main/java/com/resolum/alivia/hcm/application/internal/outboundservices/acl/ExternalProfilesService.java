package com.resolum.alivia.hcm.application.internal.outboundservices.acl;

import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade.StaffProfileData;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/** HCM's view of Profiles: the staff profile that holds each employee's personal data. */
@Service("hcmExternalProfilesService")
public class ExternalProfilesService {
    private final ProfilesContextFacade profilesContextFacade;

    public ExternalProfilesService(ProfilesContextFacade profilesContextFacade) {
        this.profilesContextFacade = profilesContextFacade;
    }

    public Long createStaffProfile(String email, String photoUrl, String photoPublicId, String phoneNumber,
                                   String firstName, String lastName, String dni, LocalDate birthDate) {
        return profilesContextFacade.createStaffProfile(email, photoUrl, photoPublicId, phoneNumber,
                firstName, lastName, dni, birthDate);
    }

    public Optional<StaffProfileData> fetchStaffProfile(Long staffProfileId) {
        return profilesContextFacade.fetchStaffProfileById(staffProfileId);
    }

    public void updateStaffProfile(Long staffProfileId, String firstName, String lastName, LocalDate birthDate,
                                   String phoneNumber, String photoUrl, String photoPublicId) {
        profilesContextFacade.updateStaffProfile(staffProfileId, firstName, lastName, birthDate, phoneNumber,
                photoUrl, photoPublicId);
    }
}
