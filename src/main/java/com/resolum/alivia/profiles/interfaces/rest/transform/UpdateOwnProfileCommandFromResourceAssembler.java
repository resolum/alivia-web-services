package com.resolum.alivia.profiles.interfaces.rest.transform;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.profiles.domain.model.commands.UpdateOwnProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateOwnProfileResource;

/**
 * Builds the update own profile command from a multipart resource.
 */
public final class UpdateOwnProfileCommandFromResourceAssembler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UpdateOwnProfileCommandFromResourceAssembler() {
    }

    /**
     * Maps the multipart resource to the command of the authenticated user.
     *
     * @param userId   the authenticated user identifier
     * @param resource the multipart resource
     * @return the update own profile command
     * @throws IllegalArgumentException when the photo cannot be read or the preferences are not a JSON object
     */
    public static UpdateOwnProfileCommand toCommandFromResource(Long userId, UpdateOwnProfileResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Update resource is required.");
        }
        byte[] photoData = null;
        String photoFileName = null;
        if (resource.photo() != null && !resource.photo().isEmpty()) {
            try {
                photoData = resource.photo().getBytes();
                photoFileName = resource.photo().getOriginalFilename();
            } catch (IOException exception) {
                throw new IllegalArgumentException("Invalid photo file", exception);
            }
        }
        return new UpdateOwnProfileCommand(
                userId,
                resource.firstName(),
                resource.lastName(),
                resource.phoneNumber(),
                photoData,
                photoFileName,
                parsePreferences(resource.preferences()));
    }

    private static Map<String, Object> parsePreferences(String preferences) {
        if (preferences == null || preferences.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(preferences, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Preferences must be a JSON object.", exception);
        }
    }
}
