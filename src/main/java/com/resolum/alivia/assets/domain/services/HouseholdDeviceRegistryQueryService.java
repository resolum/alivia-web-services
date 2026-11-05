package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;

import java.util.Optional;

public interface HouseholdDeviceRegistryQueryService {

    /**
     * The table of devices the household keeps for its edge services, with its version; empty when the installation is
     * unknown. It is what an edge service asks for when it reconnects.
     */
    Optional<EdgeAccessTable> handle(GetEdgeAccessTableByInstallationIdQuery query);
}
