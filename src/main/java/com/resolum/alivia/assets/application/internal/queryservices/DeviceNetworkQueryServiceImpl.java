package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByDeviceIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByInstallationIdQuery;
import com.resolum.alivia.assets.domain.services.DeviceNetworkQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DeviceNetworkQueryServiceImpl implements DeviceNetworkQueryService {
   private final DeviceNetworkRepository deviceNetworkRepository;

    public DeviceNetworkQueryServiceImpl(DeviceNetworkRepository deviceNetworkRepository) {
        this.deviceNetworkRepository = deviceNetworkRepository;
    }

    @Override
    public Optional<DeviceNetwork> handle(GetDeviceNetworkByIdQuery query) {
        return deviceNetworkRepository.findById(query.id());
    }

    @Override
    public Optional<DeviceNetwork> handle(GetDeviceNetworkByInstallationIdQuery query) {
        return Optional.empty();
    }

    @Override
    public Optional<DeviceNetwork> handle(GetDeviceNetworkByEdgeIdQuery query) {
        return Optional.empty();
    }

    @Override
    public Optional<DeviceNetwork> handle(GetDeviceNetworkByDeviceIdQuery query) {
        return Optional.empty();
    }
}
