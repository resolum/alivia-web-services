package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdges;
import com.resolum.alivia.assets.domain.model.queries.GetInstallationEdgesQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDevicesOfHouseholdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgesOfHouseholdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetHouseholdOfDeviceQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeTableVersionByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetTableVersionByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeScopeByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.AssignedEdge;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeScopeView;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeTableVersion;

import java.util.List;
import java.util.Optional;

public interface HouseholdDeviceRegistryQueryService {
    /** Edges placed in an installation; empty when the installation is not registered. */
    Optional<InstallationEdges> handle(GetInstallationEdgesQuery query);

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

    /**
     * Where the edge service is assigned: its kind, the installation, the household and the devices placed there, read
     * from one snapshot; empty when the edge is not assigned to the registry of an active installation.
     */
    Optional<EdgeScopeView> handle(GetEdgeScopeByEdgeIdQuery query);

    /**
     * The devices placed in the household, read from one snapshot; empty when the household has no active installation
     * registry or no device placed in it.
     */
    List<EdgeScopeView.ScopedDevice> handle(GetDevicesOfHouseholdQuery query);

    /**
     * The edge services assigned right now to the household's active installation; empty when it has no active registry
     * or no edge placed. An edge that was taken out of the installation is not in it.
     */
    List<AssignedEdge> handle(GetEdgesOfHouseholdQuery query);

    /** The household where the device is placed right now; empty when it is not placed in an active installation. */
    Optional<Long> handle(GetHouseholdOfDeviceQuery query);

    /** The current table version of an installation; empty when it is unknown or its registry is not active. */
    Optional<Long> handle(GetTableVersionByInstallationIdQuery query);
}
