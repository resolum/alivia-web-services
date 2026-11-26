package com.resolum.alivia.tracking.domain.repositories;

import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Keeps whether the backend hears from each edge. */
public interface EdgeStatusRepository {

    Optional<EdgeStatus> findByEdgeId(Long edgeId);

    /**
     * Keeps a heartbeat and answers the status that was there before, so the caller knows in one atomic step whether the
     * edge was offline (and this report is its reconnection) or had never reported.
     */
    Optional<EdgeStatus> recordHeartbeat(EdgeStatus status);

    /**
     * Marks the edge offline only if it is still online and has not reported since {@code seen}. Of several callers at once
     * exactly one gets {@code true}.
     */
    boolean markOffline(Long edgeId, Instant seen, Instant offlineSince);

    /**
     * Forgets the status of an edge that is online and has not reported since {@code seen}: the one of an edge that left the
     * installation, so that the first report it makes in another home is a first report and not a reconnection. Nothing is
     * removed if the edge reported meanwhile.
     */
    boolean removeIfSilent(Long edgeId, Instant seen);

    /** The edges of the household that have reported at least once. */
    List<EdgeStatus> findAllByHouseholdId(Long householdId);

    /** The edges that are online and have not reported since {@code limit}. */
    List<EdgeStatus> findOnlineSilentSince(Instant limit);
}
