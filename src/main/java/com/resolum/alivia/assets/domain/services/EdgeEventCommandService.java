package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.ReportEdgeEventCommand;

/** Turns what an edge service reports into notifications for the relatives of the home. */
public interface EdgeEventCommandService {

    /**
     * Notifies the relatives in charge of the edge's household.
     *
     * @return how many relatives were notified now (0 when the event had already been reported)
     * @throws com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation when the edge is not in an active home
     */
    int handle(ReportEdgeEventCommand command);
}
