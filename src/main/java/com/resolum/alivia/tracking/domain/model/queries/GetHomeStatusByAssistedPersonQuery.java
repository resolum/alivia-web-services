package com.resolum.alivia.tracking.domain.model.queries;

/**
 * The state of the devices of the home of an assisted person, asked on behalf of a user.
 *
 * @param userId           the authenticated user, who must be able to read the person
 * @param assistedPersonId the person whose home is asked for
 */
public record GetHomeStatusByAssistedPersonQuery(Long userId, Long assistedPersonId) {
}
