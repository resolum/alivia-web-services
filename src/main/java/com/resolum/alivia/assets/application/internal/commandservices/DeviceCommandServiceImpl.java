package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.DeviceStillAssigned;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.commands.ChangeDeviceStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceCommandServiceImpl implements DeviceCommandService {
    private final DeviceRepository deviceRepository;
    private final ExternalBusinessService externalBusinessService;
    private final EdgeRepository edgeRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final HouseholdDeviceRegistryRepository registryRepository;
    public DeviceCommandServiceImpl(DeviceRepository deviceRepository, ExternalBusinessService externalBusinessService, EdgeRepository edgeRepository,
                                    DeviceAssignmentRepository assignmentRepository,
                                    HouseholdDeviceRegistryRepository registryRepository) {
        this.assignmentRepository = assignmentRepository;
        this.registryRepository = registryRepository;
        this.deviceRepository = deviceRepository;
        this.externalBusinessService = externalBusinessService;
        this.edgeRepository = edgeRepository;
    }

    @Override
    public Long handle(RegisterDeviceCommand command) {
        // Building the device validates the MAC, alias and type before anything is looked up.
        var device = new Device(command.macAddress(), command.alias(), command.deviceType(), command.businessId());
        var macAddress = device.getMacAddress();
        if (!externalBusinessService.existsBusiness(command.businessId())) {
            throw new BusinessNotFound(String.format("business with id %d not found", command.businessId()));
        }
        ensureMacAddressIsFree(macAddress);
        saveDevice(device);
        return device.getId();
    }

    @Override
    @Transactional
    public Long handle(ChangeDeviceStatusCommand command) {
        var device= deviceRepository.findById(command.deviceId()).orElseThrow(()->new DeviceNotFound(command.deviceId()));
        ensureStatusMatchesAssignment(device, command.status());
        device.updateAssetStatus(command.status());
        saveDevice(device);
        refreshRegistryOf(device);
        return device.getId();
    }

    @Override
    @Transactional
    public Long handle(UpdateDeviceCommand command) {
        var device = deviceRepository.findById(command.deviceId()).orElseThrow(()->new DeviceNotFound(command.deviceId()));
        var newMacAddress = new MacAddress(command.macAddress());
        // Another type could exceed what the household contracted, which is only checked when assigning.
        if (device.changesTypeTo(command.deviceType()) && assignmentRepository.findActiveByDeviceId(device.getId()).isPresent()) {
            throw DeviceStillAssigned.typeChange(device.getId());
        }
        if (!newMacAddress.equals(device.getMacAddress())) {
            ensureMacAddressIsFree(newMacAddress);
        }
        device.update(command.macAddress(),command.deviceType());
        saveDevice(device);
        refreshRegistryOf(device);
        return device.getId();
    }

    /**
     * The table of devices that a household keeps for its edge services shows the status, the MAC address and the
     * type of its devices: when one of them changes, the registry that holds it gives the table a new version and
     * announces it. It is done in the same transaction as the change, with the registry locked.
     */
    private void refreshRegistryOf(Device device) {
        registryRepository.findByActiveDeviceIdForUpdate(device.getId()).ifPresent(registry -> {
            registry.deviceChanged(device.getId());
            registryRepository.saveAndFlush(registry);
        });
    }

    /**
     * {@code IN_USE} means an active assignment, so it is only reached by assigning the device; and a device
     * with an active assignment cannot become {@code AVAILABLE} or {@code RETIRED} until it is unassigned.
     * Going to maintenance or broken while assigned is allowed: the equipment can fail in the household.
     */
    private void ensureStatusMatchesAssignment(Device device, String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return; // updateAssetStatus rejects it
        }
        AssetStatus target;
        try {
            target = AssetStatus.valueOf(requestedStatus.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknownStatus) {
            return; // updateAssetStatus rejects it
        }
        if (target == AssetStatus.IN_USE) {
            throw new IllegalArgumentException("A device becomes IN_USE only by being assigned to an installation.");
        }
        if ((target == AssetStatus.AVAILABLE || target == AssetStatus.RETIRED)
                && assignmentRepository.findActiveByDeviceId(device.getId()).isPresent()) {
            throw new DeviceStillAssigned(device.getId(), target);
        }
    }

    /** A MAC address identifies one asset: it cannot be shared by two devices nor by a device and an edge. */
    private void ensureMacAddressIsFree(MacAddress macAddress) {
        if (deviceRepository.existsDeviceByMacAddress(macAddress) || edgeRepository.existsEdgeByMacAddress(macAddress)) {
            throw new MacAddressAlreadyExists(macAddress.macAddress());
        }
    }

    private void saveDevice(Device device) {
        try {
            deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException exception) {
            // Another request registered the same MAC between the check and the insert (uk_devices_mac_address).
            throw new MacAddressAlreadyExists(device.getMacAddress().macAddress());
        }
    }
}
