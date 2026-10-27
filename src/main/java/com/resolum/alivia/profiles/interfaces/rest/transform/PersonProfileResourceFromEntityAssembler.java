package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.interfaces.rest.resources.PersonProfileResource;

public final class PersonProfileResourceFromEntityAssembler {
    private PersonProfileResourceFromEntityAssembler() {
    }

    public static PersonProfileResource toResourceFromEntity(PersonProfile entity) {
        return new PersonProfileResource(
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
