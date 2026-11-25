package com.resolum.alivia.tracking.domain.services;

import com.resolum.alivia.tracking.domain.model.commands.ReportUrgentEventCommand;

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
}
