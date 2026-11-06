package com.resolum.alivia.assets.domain.model.aggregates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * What one edge service has confirmed of the table of devices of its installation. The server never decides what to
 * send from this: the edge asks for the table and the highest version wins. It only tells the business how far behind an
 * edge is.
 *
 * <p>The state is changed with single statements (see the repository), not by loading and saving it, so that two
 * confirmations that arrive together cannot lower the version, and so that reading the table never conflicts with a
 * confirmation. Times are UTC, as everywhere in the application.</p>
 */
@Getter
@Entity
@Table(name = "edge_sync_states")
public class EdgeSyncState {

    @Id
    @Column(name = "edge_id")
    private Long edgeId;

    /** Installation the versions below belong to. */
    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    /** Highest version of the table that the edge reported as applied; {@code null} until it reports one. */
    @Column(name = "applied_version")
    private Long appliedVersion;

    @Column(name = "acked_at")
    private LocalDateTime ackedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    protected EdgeSyncState() {
    }
}
