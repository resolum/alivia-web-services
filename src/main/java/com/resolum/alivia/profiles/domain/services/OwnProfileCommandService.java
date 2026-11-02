package com.resolum.alivia.profiles.domain.services;

import com.resolum.alivia.profiles.domain.model.commands.UpdateOwnProfileCommand;

/**
 * Domain command service for the profile owned by an authenticated user.
 */
public interface OwnProfileCommandService {

    /**
     * Updates the person or staff profile linked to the user.
     *
     * @param command the update own profile command
     * @return the identifier of the updated profile
     * @throws com.resolum.alivia.profiles.domain.exceptions.OwnProfileNotFoundException
     *         when the user has no linked profile
     */
    Long handle(UpdateOwnProfileCommand command);
}
