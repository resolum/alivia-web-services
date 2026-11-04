package com.resolum.alivia.nursing.domain.exceptions;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Thrown when a user that must join the care network has no person profile, which a care
 * membership needs to read the member data.
 */
public class CareMemberProfileNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param userId the user without a person profile
     */
    public CareMemberProfileNotFoundException(UserId userId) {
        super("User " + userId.userId() + " has no person profile.");
    }
}
