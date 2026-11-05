package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.PlaceEdgeInInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RemoveEdgeFromInstallationCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdges;

public interface HouseholdDeviceRegistryCommandService {

    /** Registers the installation of a household and opens its device registry; returns the id of the registry. */
    Long handle(RegisterInstallationCommand command);

    /**
     * Puts an edge in an installation, in the place of its type. The edge that was there goes back to the inventory
     * and loses its credential.
     */
    InstallationEdges handle(PlaceEdgeInInstallationCommand command);

    /** Takes an edge out of an installation; it goes back to the inventory and loses its credential. */
    InstallationEdges handle(RemoveEdgeFromInstallationCommand command);
}
