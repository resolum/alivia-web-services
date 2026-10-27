package com.resolum.alivia.nursing.application.internal.outboundservices.acl;

import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Outbound service acting as an Anti-Corruption Layer (ACL) adapter to read display names
 * from the Profiles bounded context.
 *
 * <p>Nursing aggregates keep only person profile identifiers, for assisted persons and for
 * caregivers and relatives alike, so every name is resolved by {@link PersonProfileId}.
 * Technicians have a staff profile instead, whose names are read with {@link #fetchStaffNames}.</p>
 */
@Slf4j
@Service("nursingExternalProfilesService")
public class ExternalProfilesService {

    private final ProfilesContextFacade profilesContextFacade;

    public ExternalProfilesService(ProfilesContextFacade profilesContextFacade) {
        this.profilesContextFacade = profilesContextFacade;
    }

    /**
     * Resolves the full names of the given person profiles.
     *
     * @param personProfileIds the person profiles
     * @return the names by profile; profiles without a resolved name are absent
     */
    public Map<PersonProfileId, String> fetchPersonNames(Set<PersonProfileId> personProfileIds) {
        log.debug("Fetching person names from Profiles for {} profiles", personProfileIds.size());
        // shortcut: one facade call per profile, add a batch method to ProfilesContextFacade if agendas get large
        var names = new HashMap<PersonProfileId, String>();
        for (PersonProfileId profileId : personProfileIds) {
            profilesContextFacade.fetchPersonProfileFullNameById(profileId.personProfileId())
                    .ifPresent(name -> names.put(profileId, name));
        }
        return names;
    }

    /**
     * Resolves the full names of the given staff profiles.
     *
     * @param staffProfileIds the staff profile identifiers
     * @return the names by staff profile; profiles without a resolved name are absent
     */
    public Map<Long, String> fetchStaffNames(Set<Long> staffProfileIds) {
        log.debug("Fetching staff names from Profiles for {} profiles", staffProfileIds.size());
        // shortcut: one facade call per profile, add a batch method to ProfilesContextFacade if agendas get large
        var names = new HashMap<Long, String>();
        for (Long profileId : staffProfileIds) {
            profilesContextFacade.fetchStaffProfileFullNameById(profileId).ifPresent(name -> names.put(profileId, name));
        }
        return names;
    }
}
