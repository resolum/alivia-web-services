package com.resolum.alivia.tracking.domain.model.valueobjects;

import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether the edges of a home are heard, taken together: the same reading for the state of the devices and for the
 * metrics, so both say the same thing about the same home.
 *
 * @param link                 {@code ONLINE}, {@code OFFLINE} (at least one edge is not heard) or {@code UNKNOWN}
 * @param silentEdgeIds        the edges that are not heard
 * @param offlineSince         since when the first silent edge stopped reporting; {@code null} when none is silent
 * @param lastReportAt         the latest report of any edge; {@code null} when none has ever reported
 * @param mayHavePendingEvents whether events may be waiting in an edge: one is silent, or said it has some to send
 */
public record HomeLinkSummary(HomeLink link, Set<Long> silentEdgeIds, Instant offlineSince, Instant lastReportAt,
                              boolean mayHavePendingEvents) {

    public HomeLinkSummary {
        silentEdgeIds = silentEdgeIds == null ? Set.of() : Set.copyOf(silentEdgeIds);
    }

    /**
     * Only the edges the home has now count: the status of one that was replaced or taken out of the installation stays in
     * the database, but it is no longer part of the home, and its silence says nothing about it.
     *
     * @param assigned the edges assigned now to the active installation of the home, as Assets says
     */
    public static HomeLinkSummary of(List<EdgeStatus> edges, Set<Long> assigned) {
        return of(edges.stream().filter(edge -> assigned.contains(edge.edgeId())).toList());
    }

    public static HomeLinkSummary of(List<EdgeStatus> edges) {
        var silent = edges.stream().filter(edge -> edge.link() == EdgeLink.OFFLINE).toList();
        var link = edges.isEmpty() ? HomeLink.UNKNOWN : silent.isEmpty() ? HomeLink.ONLINE : HomeLink.OFFLINE;
        return new HomeLinkSummary(link, silent.stream().map(EdgeStatus::edgeId).collect(Collectors.toSet()),
                silent.stream().map(EdgeStatus::offlineSince).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null),
                edges.stream().map(EdgeStatus::lastReportAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                edges.stream().anyMatch(EdgeStatus::mayHavePendingEvents));
    }
}
