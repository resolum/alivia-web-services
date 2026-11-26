package com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents;

import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Whether the backend hears from an edge; its id is the id of the edge. The index serves the search for silent edges. */
@Document("edge_statuses")
@CompoundIndex(name = "link_last_report", def = "{'link': 1, 'lastReportAt': 1}")
public class EdgeStatusDocument {

    @Id
    private Long id;
    private Long installationId;
    @Indexed(name = "household")
    private Long householdId;
    private Instant lastReportAt;
    private String link;
    private int pendingEvents;
    private Instant offlineSince;
    private String kind;

    protected EdgeStatusDocument() {
    }

    public EdgeStatus toDomain() {
        return new EdgeStatus(id, installationId, householdId, lastReportAt, EdgeLink.valueOf(link), pendingEvents,
                offlineSince, kind);
    }
}
