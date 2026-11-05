package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByDeviceIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.services.DeviceNetworkQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DeviceNetworkQueryServiceImpl implements DeviceNetworkQueryService {
   private final DeviceNetworkRepository deviceNetworkRepository;
    private final DeviceAssignmentRepository deviceAssignmentRepository;

    public DeviceNetworkQueryServiceImpl(DeviceNetworkRepository deviceNetworkRepository,
                                         DeviceAssignmentRepository deviceAssignmentRepository) {
        this.deviceNetworkRepository = deviceNetworkRepository;
        this.deviceAssignmentRepository = deviceAssignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EdgeAccessTable> handle(GetEdgeAccessTableByInstallationIdQuery query) {
        return deviceNetworkRepository.findByInstallationId(new InstallationId(query.installationId()))
                .filter(network -> network.getHouseholdId() != null)
                .map(network -> EdgeAccessTable.of(query.installationId(), network.getHouseholdId(),
                        deviceAssignmentRepository.findAllActiveByInstallationId(query.installationId())));
    }

    @Override
    public Optional<DeviceNetwork> handle(GetDeviceNetworkByIdQuery query) {
        return deviceNetworkRepository.findById(query.id());
    }

    @Override
    public Optional<DeviceNetwork> handle(GetDeviceNetworkByInstallationIdQuery query) {
        return deviceNetworkRepository.findByInstallationId(new InstallationId(query.installationId()));
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
