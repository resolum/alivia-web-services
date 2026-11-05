package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalCompanyService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.commands.ChangeDeviceStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class DeviceCommandServiceImpl implements DeviceCommandService {
    private final DeviceRepository deviceRepository;
    private final ExternalCompanyService externalCompanyService;
    private final EdgeRepository edgeRepository;
    public DeviceCommandServiceImpl(DeviceRepository deviceRepository, ExternalCompanyService externalCompanyService, EdgeRepository edgeRepository) {
        this.deviceRepository = deviceRepository;
        this.externalCompanyService = externalCompanyService;
        this.edgeRepository = edgeRepository;
    }

    @Override
    public Long handle(RegisterDeviceCommand command) {
        // Building the device validates the MAC, alias and type before anything is looked up.
        var device = new Device(command.macAddress(), command.alias(), command.deviceType(), command.businessId());
        var macAddress = device.getMacAddress();
        if (!externalCompanyService.existsBusiness(command.businessId())) {
            throw new BusinessNotFound(String.format("business with id %d not found", command.businessId()));
        }
        ensureMacAddressIsFree(macAddress);
        saveDevice(device);
        return device.getId();
    }

    @Override
    public Long handle(ChangeDeviceStatusCommand command) {
        var device= deviceRepository.findById(command.deviceId()).orElseThrow(()->new DeviceNotFound(command.deviceId()));
        device.updateAssetStatus(command.status());
        deviceRepository.save(device);
        return device.getId();
    }

    @Override
    public Long handle(UpdateDeviceCommand command) {
        var device = deviceRepository.findById(command.deviceId()).orElseThrow(()->new DeviceNotFound(command.deviceId()));
        var newMacAddress = new MacAddress(command.macAddress());
        if (!newMacAddress.equals(device.getMacAddress())) {
            ensureMacAddressIsFree(newMacAddress);
        }
        device.update(command.macAddress(),command.deviceType());
        saveDevice(device);
        return device.getId();
    }

    /** A MAC address identifies one asset: it cannot be shared by two devices nor by a device and an edge. */
    private void ensureMacAddressIsFree(MacAddress macAddress) {
        if (deviceRepository.existsDeviceByMacAddress(macAddress) || edgeRepository.existsEdgeByMacAddress(macAddress)) {
            throw new MacAddressAlreadyExists(macAddress.macAddress());
        }
    }

    private void saveDevice(Device device) {
        try {
            deviceRepository.save(device);
        } catch (DataIntegrityViolationException exception) {
            // Another request registered the same MAC between the check and the insert (uk_devices_mac_address).
            throw new MacAddressAlreadyExists(device.getMacAddress().macAddress());
        }
    }
}
