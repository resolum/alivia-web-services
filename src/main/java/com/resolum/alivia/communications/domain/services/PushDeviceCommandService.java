package com.resolum.alivia.communications.domain.services;

import com.resolum.alivia.communications.domain.model.commands.RegisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.model.commands.UnregisterPushDeviceCommand;

public interface PushDeviceCommandService {
    Long handle(RegisterPushDeviceCommand command);
    void handle(UnregisterPushDeviceCommand command);
}
