package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.domain.model.commands.*;
import com.resolum.alivia.assets.domain.services.DeviceNetworkCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import org.springframework.stereotype.Service;

@Service
public class DeviceNetworkCommandServiceImpl implements DeviceNetworkCommandService {
    private  final DeviceNetworkRepository deviceNetworkRepository;

    public DeviceNetworkCommandServiceImpl(DeviceNetworkRepository deviceNetworkRepository) {
        this.deviceNetworkRepository = deviceNetworkRepository;
    }

    @Override
    public Long handle(AddDeviceToNetworkCommand command) {
        return 0L;
    }

    @Override
    public void handle(RemoveDeviceFromNetworkCommand command) {

    }

    @Override
    public Long handle(AddEdgeToNetworkCommand command) {
        return 0L;
    }

    @Override
    public void handle(RemoveEdgeFromNetworkCommand command) {

    }

    @Override
    public void handle(DeactivateDeviceNetworkCommand command) {

    }
}
