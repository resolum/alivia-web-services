package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.commands.RegisterAssistedPersonCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;

/**
 * Write service for the assisted persons of a household.
 */
public interface AssistedPersonCommandService {

    /**
     * Registers an assisted person: creates its account in IAM, adds it to the household and links
     * the requester to it as relative.
     *
     * @param command the register command
     * @return the registered assisted person
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException
     *         when the requester cannot manage the household
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareMemberProfileNotFoundException
     *         when the requester has no person profile
     */
    AssistedPersonSummary handle(RegisterAssistedPersonCommand command);
}
