package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssistedPersonResource;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;

import java.time.LocalDate;

public class AssistedPersonResourceAssembler {

    public static AssistedPersonResource toResourceFromEntity(AssistedPerson entity) {
        return toResourceFromEntity(entity, (ProfilesContextFacade.PersonProfileData) null);
    }

    public static AssistedPersonResource toResourceFromEntity(AssistedPerson entity, ProfilesContextFacade.PersonProfileData profile) {
        String firstName = profile != null ? profile.firstName() : null;
        String lastName = profile != null ? profile.lastName() : null;
        String dni = profile != null ? profile.dni() : null;
        LocalDate birthDate = profile != null ? profile.birthDate() : null;
        String phoneNumber = profile != null ? profile.phoneNumber() : null;
        String photoUrl = profile != null ? profile.photoUrl() : null;

        return new AssistedPersonResource(
                entity.getId(),
                entity.getPersonProfileId() != null ? entity.getPersonProfileId().personProfileId() : null,
                entity.getRelative() != null ? entity.getRelative().getId() : null,
                entity.getHouseholdId(),
                firstName,
                lastName,
                dni,
                birthDate,
                phoneNumber,
                photoUrl,
                entity.getKinship() != null ? entity.getKinship().name() : null,
                entity.getMobilityLevel() != null ? entity.getMobilityLevel().name() : null,
                entity.getEmergencyContact() != null ? entity.getEmergencyContact().name() : null,
                entity.getEmergencyContact() != null ? entity.getEmergencyContact().phone() : null,
                entity.getConsentGivenAt()
        );
    }

    public static AssistedPersonResource toResourceFromEntity(AssistedPerson entity, String fullName) {
        String firstName = null;
        String lastName = null;
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }
        return new AssistedPersonResource(
                entity.getId(),
                entity.getPersonProfileId() != null ? entity.getPersonProfileId().personProfileId() : null,
                entity.getRelative() != null ? entity.getRelative().getId() : null,
                entity.getHouseholdId(),
                firstName,
                lastName,
                null,
                null,
                null,
                null,
                entity.getKinship() != null ? entity.getKinship().name() : null,
                entity.getMobilityLevel() != null ? entity.getMobilityLevel().name() : null,
                entity.getEmergencyContact() != null ? entity.getEmergencyContact().name() : null,
                entity.getEmergencyContact() != null ? entity.getEmergencyContact().phone() : null,
                entity.getConsentGivenAt()
        );
    }
}
