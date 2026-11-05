package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.ConfirmInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceNetworkCommand;

public interface DeviceNetworkCommandService {

    /** Registers the network of an installation for a household, pending confirmation; returns its id. */
    Long handle(RegisterDeviceNetworkCommand command);

    /** The technicians confirmed the installation: its network becomes active. Confirming twice changes nothing. */
    void handle(ConfirmInstallationCommand command);
}
