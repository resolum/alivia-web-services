package com.resolum.alivia.profiles.domain.services;

import com.resolum.alivia.profiles.domain.model.commands.AddHouseholdMemberCommand;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;

/**
 * Write service for household memberships.
 */
public interface HouseholdMemberCommandService {

    /**
     * Adds a user to a household as an active member.
     *
     * @param command the add command
     * @return the new member
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdMemberAlreadyExistsException
     *         when the user is already a member, whatever the status of its membership
     */
    HouseholdMember handle(AddHouseholdMemberCommand command);
}
