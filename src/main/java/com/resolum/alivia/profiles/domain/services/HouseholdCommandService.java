package com.resolum.alivia.profiles.domain.services;

import com.resolum.alivia.profiles.domain.model.commands.CreateHouseholdCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateHouseholdCommand;

/**
 * Domain command service for managing households.
 */
public interface HouseholdCommandService {

    /**
     * Handles the creation of a new household.
     *
     * @param command the create household command
     * @return the identifier of the created household
     */
    Long handle(CreateHouseholdCommand command);

    /**
     * Handles updating an existing household's general information.
     *
     * @param command the update household command
     * @return the identifier of the updated household
     */
    Long handle(UpdateHouseholdCommand command);
}
