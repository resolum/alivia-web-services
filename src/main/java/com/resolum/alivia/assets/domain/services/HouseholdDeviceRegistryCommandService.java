package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.RegisterInstallationCommand;

public interface HouseholdDeviceRegistryCommandService {

    /** Registers the installation of a household and opens its device registry; returns the id of the registry. */
    Long handle(RegisterInstallationCommand command);
}
