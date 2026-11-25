package com.resolum.alivia.tracking.domain.model.commands;

import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;

import java.util.List;

/**
 * An edge sends the events it has, in the order they happened: the ones that happen now or the ones it kept while it had
 * no connection.
 *
 * @param edgeId the edge that sends them (from its credential)
 * @param events the events, in order
 */
public record ReportEventBatchCommand(Long edgeId, List<ReportedEvent> events) {

    public ReportEventBatchCommand {
        if (edgeId == null) throw new IllegalArgumentException("The edge is required.");
        if (events == null || events.isEmpty() || events.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("The batch needs at least one event and none can be empty.");
        }
        events = List.copyOf(events);
    }
}
