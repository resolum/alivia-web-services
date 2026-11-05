package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.EdgeSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Every change is one statement: nothing is loaded and saved, so concurrent requests never overwrite each other. The
 * modifying queries clear the persistence context, so the state read afterwards is the stored one.
 */
@Repository
public interface EdgeSyncStateRepository extends JpaRepository<EdgeSyncState, Long> {

    List<EdgeSyncState> findAllByInstallationId(Long installationId);

    /** Creates the state of an edge that has none; does nothing when another request created it first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into edge_sync_states (edge_id, installation_id, last_seen_at) "
            + "values (:edgeId, :installationId, :seenAt) on conflict (edge_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("edgeId") Long edgeId, @Param("installationId") Long installationId,
                       @Param("seenAt") LocalDateTime seenAt);

    /** The edge now belongs to another installation: what it confirmed there says nothing here, so it starts over. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EdgeSyncState s set s.installationId = :installationId, s.appliedVersion = null, s.ackedAt = null "
            + "where s.edgeId = :edgeId and s.installationId <> :installationId")
    int resetIfOtherInstallation(@Param("edgeId") Long edgeId, @Param("installationId") Long installationId);

    /** Raises the confirmed version; it never lowers it and does nothing for an equal or lower one. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EdgeSyncState s set s.appliedVersion = :version, s.ackedAt = :ackedAt, s.lastSeenAt = :ackedAt "
            + "where s.edgeId = :edgeId and (s.appliedVersion is null or s.appliedVersion < :version)")
    int raiseAppliedVersion(@Param("edgeId") Long edgeId, @Param("version") Long version,
                            @Param("ackedAt") LocalDateTime ackedAt);

    /** Records the last time the edge was heard, unless that was recorded after {@code staleBefore}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EdgeSyncState s set s.lastSeenAt = :seenAt "
            + "where s.edgeId = :edgeId and (s.lastSeenAt is null or s.lastSeenAt < :staleBefore)")
    int touchLastSeen(@Param("edgeId") Long edgeId, @Param("seenAt") LocalDateTime seenAt,
                      @Param("staleBefore") LocalDateTime staleBefore);

    /** Forgets what the edge confirmed (it was placed in an installation or taken out of it). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EdgeSyncState s where s.edgeId = :edgeId")
    int deleteByEdgeId(@Param("edgeId") Long edgeId);
}
