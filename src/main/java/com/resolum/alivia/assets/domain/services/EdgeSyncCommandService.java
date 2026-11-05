package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.AcknowledgeEdgeVersionCommand;
import com.resolum.alivia.assets.domain.model.commands.RecordEdgeSeenCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AppliedEdgeVersion;

/** What the server remembers of each edge service: the version it confirmed and when it was last heard. */
public interface EdgeSyncCommandService {

    /**
     * The edge reports the highest version of its table that it applied. The confirmed version only grows: a repeated
     * or lower report changes nothing and answers with what is stored.
     *
     * @return the confirmed version after this report, and when it was confirmed
     * @throws com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation when the edge is not assigned to an
     *                                                                           active installation
     * @throws com.resolum.alivia.assets.domain.exceptions.AppliedVersionAhead when the table has not reached that version
     */
    AppliedEdgeVersion handle(AcknowledgeEdgeVersionCommand command);

    /** The edge asked for its table. The time is written at most once a minute per edge. */
    void handle(RecordEdgeSeenCommand command);
}
