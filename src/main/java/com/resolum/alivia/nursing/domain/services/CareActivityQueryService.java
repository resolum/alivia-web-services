package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.queries.GetCareAgendaQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetPendingRemindersQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AgendaItem;

import java.util.List;

/**
 * Read service for care activities: the care agenda and the reminders derived from it.
 */
public interface CareActivityQueryService {

    /**
     * Computes the agenda items visible to the user within the query range.
     *
     * @param query the agenda query
     * @return the items sorted by actual start
     * @throws com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException
     *         when the user is not linked to the requested assisted person
     */
    List<AgendaItem> handle(GetCareAgendaQuery query);

    /**
     * Computes the pending reminders of the user, from yesterday until the configured horizon.
     *
     * @param query the reminders query
     * @return the pending items sorted by actual start and then by priority
     * @throws com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException
     *         when the user is not linked to the requested assisted person
     */
    List<AgendaItem> handle(GetPendingRemindersQuery query);
}
