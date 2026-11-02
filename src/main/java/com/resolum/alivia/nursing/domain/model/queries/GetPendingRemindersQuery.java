package com.resolum.alivia.nursing.domain.model.queries;

import com.resolum.alivia.nursing.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Query for the pending reminders of the authenticated user.
 *
 * @param userId                  the authenticated user
 * @param assistedPersonProfileId optional assisted person filter
 * @param priority                optional priority filter
 */
public record GetPendingRemindersQuery(UserId userId, PersonProfileId assistedPersonProfileId,
                                       ReminderPriority priority) {

    /**
     * Validates the query.
     */
    public GetPendingRemindersQuery {
        Objects.requireNonNull(userId, "User cannot be null.");
    }
}
