package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAlreadyRegistered;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceNetworkCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.NetworkStatus;
import com.resolum.alivia.assets.domain.services.DeviceNetworkCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class DeviceNetworkCommandServiceImpl implements DeviceNetworkCommandService {
    private  final DeviceNetworkRepository deviceNetworkRepository;
    private final ExternalBusinessService externalBusinessService;

    public DeviceNetworkCommandServiceImpl(DeviceNetworkRepository deviceNetworkRepository,
                                           ExternalBusinessService externalBusinessService) {
        this.deviceNetworkRepository = deviceNetworkRepository;
        this.externalBusinessService = externalBusinessService;
    }

    @Override
    public Long handle(RegisterDeviceNetworkCommand command) {
        var network = new DeviceNetwork(command.installationId(), command.householdId(), command.businessId());
        if (!externalBusinessService.existsBusiness(command.businessId())) {
            throw new BusinessNotFound(String.format("business with id %d not found", command.businessId()));
        }
        if (deviceNetworkRepository.existsByInstallationId(new InstallationId(command.installationId()))) {
            throw InstallationAlreadyRegistered.installation(command.installationId());
        }
        if (deviceNetworkRepository.existsByHouseholdIdAndNetworkStatus(command.householdId(), NetworkStatus.ACTIVE)) {
            throw InstallationAlreadyRegistered.activeInHousehold(command.householdId());
        }
        try {
            return deviceNetworkRepository.saveAndFlush(network).getId();
        } catch (DataIntegrityViolationException exception) {
            // Another request registered the installation or the household's active network first
            // (uk_device_networks_installation, uk_device_networks_active_household).
            throw InstallationAlreadyRegistered.installation(command.installationId());
        }
    }
}
