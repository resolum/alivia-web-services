package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.interfaces.rest.resources.StaffProfileResource;

public final class StaffProfileResourceFromEntityAssembler {
    private StaffProfileResourceFromEntityAssembler() {
    }

    public static StaffProfileResource toResourceFromEntity(StaffProfile entity) {
        return new StaffProfileResource(
                entity.getId(),
                entity.getEmailAddress() != null ? entity.getEmailAddress().email() : null,
                entity.getPhoto() != null ? entity.getPhoto().photoUrl() : null,
                entity.getPhoto() != null ? entity.getPhoto().photoPublicId() : null,
                entity.getPhoneNumber() != null ? entity.getPhoneNumber().phoneNumber() : null,
                entity.getPersonName() != null ? entity.getPersonName().firstName() : null,
                entity.getPersonName() != null ? entity.getPersonName().lastName() : null,
                entity.getDni() != null ? entity.getDni().dni() : null,
                entity.getBirthDate() != null ? entity.getBirthDate().date() : null
        );
    }
}
