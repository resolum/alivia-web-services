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
        throw notImplemented();
    }

    @Override
    public void handle(RemoveDeviceFromNetworkCommand command) {
        throw notImplemented();
    }

    @Override
    public Long handle(AddEdgeToNetworkCommand command) {
        throw notImplemented();
    }

    @Override
    public void handle(RemoveEdgeFromNetworkCommand command) {
        throw notImplemented();
    }

    @Override
    public void handle(DeactivateDeviceNetworkCommand command) {
        throw notImplemented();
    }

    /** Network operations arrive with the assignment rules (P-376); failing is better than answering id 0. */
    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("Device network operations are not implemented yet.");
    }
}
