package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAlreadyRegistered;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.commands.ConfirmInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceNetworkCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.NetworkStatus;
import com.resolum.alivia.assets.domain.services.DeviceNetworkCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeviceNetworkCommandServiceImpl implements DeviceNetworkCommandService {
    /** A household can have only one network in these statuses. */
    private static final List<NetworkStatus> OPEN_STATUSES = List.of(NetworkStatus.PENDING_CONFIRMATION, NetworkStatus.ACTIVE);

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
        if (deviceNetworkRepository.existsByHouseholdIdAndNetworkStatusIn(command.householdId(), OPEN_STATUSES)) {
            throw InstallationAlreadyRegistered.activeInHousehold(command.householdId());
        }
        try {
            return deviceNetworkRepository.saveAndFlush(network).getId();
        } catch (DataIntegrityViolationException exception) {
            // Another request registered the installation or the household's open network first
            // (uk_device_networks_installation, uk_device_networks_open_household).
            throw InstallationAlreadyRegistered.installation(command.installationId());
        }
    }

    @Override
    @Transactional
    public void handle(ConfirmInstallationCommand command) {
        // Locked, so a confirmation cannot interleave with a technician placing a device.
        var network = deviceNetworkRepository.findByInstallationIdForUpdate(new InstallationId(command.installationId()))
                .orElseThrow(() -> new InstallationNotFound(command.installationId()));
        network.confirm();
        deviceNetworkRepository.save(network);
    }
}
