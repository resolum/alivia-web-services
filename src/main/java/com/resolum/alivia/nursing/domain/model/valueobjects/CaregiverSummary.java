package com.resolum.alivia.nursing.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Read model of a caregiver linked to an assisted person of a household.
 *
 * @param userId                  the caregiver user
 * @param personProfileId         the caregiver person profile
 * @param assistedPersonProfileId the assisted person the caregiver looks after
 * @param fullName                the full name read from Profiles; {@code null} when it cannot be resolved
 * @param email                   the email read from IAM; {@code null} when it cannot be resolved or the
 *                                requester may not manage the household
 */
public record CaregiverSummary(UserId userId, PersonProfileId personProfileId,
                               PersonProfileId assistedPersonProfileId, String fullName, String email) {
}
