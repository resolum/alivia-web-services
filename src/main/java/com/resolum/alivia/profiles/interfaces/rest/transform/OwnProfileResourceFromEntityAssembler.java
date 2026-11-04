package com.resolum.alivia.profiles.interfaces.rest.transform;

import java.time.LocalDate;
import java.util.Map;

import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.Profile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.interfaces.rest.resources.OwnProfileResource;

/**
 * Builds the own profile resource from the person or staff profile of a user.
 */
public final class OwnProfileResourceFromEntityAssembler {

    private OwnProfileResourceFromEntityAssembler() {
    }

    /**
     * Maps the profile to its REST representation.
     *
     * @param entity the person or staff profile
     * @return the own profile resource
     * @throws IllegalArgumentException when the profile is neither a person nor a staff profile
     */
    public static OwnProfileResource toResourceFromEntity(Profile entity) {
        String type;
        String firstName = null;
        String lastName = null;
        String dni = null;
        LocalDate birthDate = null;

        if (entity instanceof PersonProfile personProfile) {
            type = "PERSON";
            if (personProfile.getPersonName() != null) {
                firstName = personProfile.getPersonName().firstName();
                lastName = personProfile.getPersonName().lastName();
            }
            dni = personProfile.getDni() != null ? personProfile.getDni().dni() : null;
            birthDate = personProfile.getBirthDate() != null ? personProfile.getBirthDate().date() : null;
        } else if (entity instanceof StaffProfile staffProfile) {
            type = "STAFF";
            if (staffProfile.getPersonName() != null) {
                firstName = staffProfile.getPersonName().firstName();
                lastName = staffProfile.getPersonName().lastName();
            }
            dni = staffProfile.getDni() != null ? staffProfile.getDni().dni() : null;
            birthDate = staffProfile.getBirthDate() != null ? staffProfile.getBirthDate().date() : null;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported own profile type: " + entity.getClass().getSimpleName());
        }

        Map<String, Object> preferences = entity.getPreferences() != null
                ? entity.getPreferences().values()
                : null;

        return new OwnProfileResource(
                entity.getId(),
                type,
                entity.getEmailAddress() != null ? entity.getEmailAddress().email() : null,
                entity.getPhoto() != null ? entity.getPhoto().photoUrl() : null,
                entity.getPhoto() != null ? entity.getPhoto().photoPublicId() : null,
                entity.getPhoneNumber() != null ? entity.getPhoneNumber().phoneNumber() : null,
                firstName,
                lastName,
                dni,
                birthDate,
                preferences,
                entity.getUpdatedAt());
    }
}
