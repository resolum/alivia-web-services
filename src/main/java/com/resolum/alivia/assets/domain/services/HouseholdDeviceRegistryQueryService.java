package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeTableVersionByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetTableVersionByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeTableVersion;

import java.util.Optional;

public interface HouseholdDeviceRegistryQueryService {

    /**
     * The table of devices of the installation where the edge is assigned, as an edge service asks for it; empty when
     * the edge is not assigned to the registry of an active installation. The version and the entries are read from the
     * same snapshot of the database, so the table is exactly the state of its version.
     */
    Optional<EdgeAccessTable> handle(GetEdgeAccessTableByEdgeIdQuery query);

    /**
     * Only the installation and the version of that table, which is enough to tell an edge service that it is up to date; empty when the
     * edge is not assigned to the registry of an active installation.
     */
    Optional<EdgeTableVersion> handle(GetEdgeTableVersionByEdgeIdQuery query);

    /** The current table version of an installation; empty when it is unknown or its registry is not active. */
    Optional<Long> handle(GetTableVersionByInstallationIdQuery query);
}
