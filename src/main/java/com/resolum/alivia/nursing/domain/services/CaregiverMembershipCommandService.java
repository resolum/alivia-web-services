package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.commands.LinkCaregiverCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.CaregiverSummary;

/**
 * Write service for the care memberships of caregivers.
 */
public interface CaregiverMembershipCommandService {

    /**
     * Links a user as caregiver of an assisted person and adds it to the household when it is not
     * a member yet.
     *
     * @param command the link command
     * @return the linked caregiver
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException
     *         when the requester cannot manage the household
     * @throws com.resolum.alivia.nursing.domain.exceptions.AssistedPersonNotInHouseholdException
     *         when the assisted person does not belong to the household
     * @throws com.resolum.alivia.nursing.domain.exceptions.CareMemberProfileNotFoundException
     *         when the caregiver has no person profile
     * @throws com.resolum.alivia.nursing.domain.exceptions.CaregiverAlreadyLinkedException
     *         when the caregiver is already linked to the assisted person
     * @throws com.resolum.alivia.nursing.domain.exceptions.CaregiverHouseholdMembershipRevokedException
     *         when the caregiver has a revoked membership in the household
     * @throws IllegalArgumentException when the caregiver is the assisted person
     */
    CaregiverSummary handle(LinkCaregiverCommand command);
}
