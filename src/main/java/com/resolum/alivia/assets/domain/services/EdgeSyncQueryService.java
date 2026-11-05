package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.queries.GetInstallationEdgeSyncQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdgeSync;

import java.util.Optional;

public interface EdgeSyncQueryService {

    /** How far each edge service of the installation is from the current table; empty when the installation is unknown. */
    Optional<InstallationEdgeSync> handle(GetInstallationEdgeSyncQuery query);
}
