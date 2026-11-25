package com.resolum.alivia.tracking.domain.model.queries;

/**
 * The recent events of the home of an assisted person, asked on behalf of a user.
 *
 * @param userId           the authenticated user, who must be able to read the person
 * @param assistedPersonId the person whose home is asked for
 * @param limit            how many events; absent means the default
 */
public record GetRecentEventsByAssistedPersonQuery(Long userId, Long assistedPersonId, Integer limit) {
}
