package com.resolum.alivia.profiles.domain.exceptions;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Thrown when a user is added twice to the same household.
 */
public class HouseholdMemberAlreadyExistsException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param userId the duplicated user
     */
    public HouseholdMemberAlreadyExistsException(UserId userId) {
        super("User " + userId.userId() + " is already a member of the household.");
    }
}
