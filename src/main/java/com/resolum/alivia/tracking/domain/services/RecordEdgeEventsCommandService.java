package com.resolum.alivia.tracking.domain.services;

import com.resolum.alivia.tracking.domain.model.commands.ReportEventBatchCommand;
import com.resolum.alivia.tracking.domain.model.commands.ReportUrgentEventCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOutcome;

import java.util.List;

/** Keeps what the edges report about their homes and tells the family what cannot wait. */
public interface RecordEdgeEventsCommandService {

    /**
     * Tells the relatives in charge of the home of the edge and keeps the event. An urgent report is never refused
     * for what it says: an unknown device or a wrong clock only make it a little less precise. The relatives are told
     * before the event is kept, and a failure to keep it does not stop them from being told (RNF27).
     *
     * @return how many relatives were told now; 0 when the event had already been reported
     * @throws com.resolum.alivia.tracking.domain.exceptions.EdgeNotInInstallation when the edge has no active home
     */
    int handle(ReportUrgentEventCommand command);

    /**
     * Keeps the events of a batch, in order, and answers what became of each one. An event that is not valid is
     * rejected with its reason without stopping the others; one already kept is a duplicate. The urgent types are told
     * to the family as in {@link #handle(ReportUrgentEventCommand)} and are never rejected for what they say about a
     * device or the clock.
     *
     * <p>Unlike a single urgent report, a batch is answered only for what is really kept: if the events cannot be kept
     * the edge is not told that they were, so it keeps them and sends them again.</p>
     *
     * @throws com.resolum.alivia.tracking.domain.exceptions.BatchTooLarge when it has more events than allowed
     * @throws com.resolum.alivia.tracking.domain.exceptions.EdgeNotInInstallation when the edge has no active home
     * @throws com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable when they cannot be kept
     */
    List<EventOutcome> handle(ReportEventBatchCommand command);
}
