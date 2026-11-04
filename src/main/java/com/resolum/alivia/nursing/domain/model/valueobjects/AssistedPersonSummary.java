package com.resolum.alivia.nursing.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;

/**
 * Read model of an assisted person of a household.
 *
 * @param personProfileId the person profile of the assisted person
 * @param fullName        the full name read from Profiles; {@code null} when it cannot be resolved
 */
public record AssistedPersonSummary(PersonProfileId personProfileId, String fullName) {
}
