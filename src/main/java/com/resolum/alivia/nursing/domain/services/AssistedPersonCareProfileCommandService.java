package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.nursing.domain.model.commands.SaveAssistedPersonCareProfileCommand;

/**
 * Command service for assisted person care profiles.
 */
public interface AssistedPersonCareProfileCommandService {

    /**
     * Creates the care profile of the requester, or replaces it when it already exists.
     *
     * @param command the command
     * @return the saved profile
     */
    AssistedPersonCareProfile handle(SaveAssistedPersonCareProfileCommand command);
}
