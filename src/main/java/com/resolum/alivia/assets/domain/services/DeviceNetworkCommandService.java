package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.*;

public interface DeviceNetworkCommandService {

    Long handle(AddDeviceToNetworkCommand command);
    void handle(RemoveDeviceFromNetworkCommand command);
    Long handle(AddEdgeToNetworkCommand command);
    void handle (RemoveEdgeFromNetworkCommand command);
   void handle (DeactivateDeviceNetworkCommand command);

}
