package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceNetworkCommand;

public interface DeviceNetworkCommandService {

    /** Registers the network of an installation for a household; returns its id. */
    Long handle(RegisterDeviceNetworkCommand command);
}
