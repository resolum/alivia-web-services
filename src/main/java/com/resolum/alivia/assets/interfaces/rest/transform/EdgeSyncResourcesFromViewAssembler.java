package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.valueobjects.AppliedEdgeVersion;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdgeSync;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeSyncStateResource;
import com.resolum.alivia.assets.interfaces.rest.resources.InstallationEdgeSyncResource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class EdgeSyncResourcesFromViewAssembler {

    public static EdgeSyncStateResource toResourceFromView(AppliedEdgeVersion view) {
        return new EdgeSyncStateResource(view.appliedVersion(), utc(view.ackedAt()));
    }

    public static InstallationEdgeSyncResource toResourceFromView(InstallationEdgeSync view) {
        var edges = view.edges().stream()
                .map(edge -> new InstallationEdgeSyncResource.Edge(edge.edgeId(), edge.edgeType().name(),
                        edge.appliedVersion(), utc(edge.ackedAt()), utc(edge.lastSeenAt()), edge.behindBy(),
                        edge.upToDate()))
                .toList();
        return new InstallationEdgeSyncResource(view.installationId(), view.currentVersion(), edges);
    }

    /** Times are stored in UTC without zone; the API says so. */
    private static Instant utc(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
