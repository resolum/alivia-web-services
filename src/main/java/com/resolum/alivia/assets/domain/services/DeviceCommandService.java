package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.ChangeDeviceStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateDeviceCommand;

public interface DeviceCommandService {
    Long handle (RegisterDeviceCommand command);
    Long handle(ChangeDeviceStatusCommand command);
    Long handle(UpdateDeviceCommand command);

}
