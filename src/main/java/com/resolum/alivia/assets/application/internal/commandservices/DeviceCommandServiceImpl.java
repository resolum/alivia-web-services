package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalCompanyService;
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
        if (deviceRepository.existsDeviceByMacAddress(new MacAddress(command.macAddress()))){
            throw new IllegalArgumentException(new MacAddressAlreadyExists(command.macAddress()));
        }
        if (edgeRepository.existsEdgeByMacAddress(new MacAddress(command.macAddress()))){
            throw   new MacAddressAlreadyExists(command.macAddress());
        }
        // falta una validación para saber si existe o no el business
        var device = new Device(command.macAddress(),command.deviceType(),command.businessId());
        try {
            deviceRepository.save(device);
        }catch (Exception e){
            throw new IllegalArgumentException("");
        }
        return device.getId();
    }

    @Override
    public Long handle(ChangeDeviceStatusCommand command) {
        return 0L;
    }

    @Override
    public Long handle(UpdateDeviceCommand command) {

       var device = deviceRepository.findById(command.deviceId()).orElseThrow(()->new DeviceNotFound(command.deviceId()));
       device.update(command.macAddress(),command.deviceType());


        try {
         deviceRepository.save(device);
        }catch (Exception e)
        {
            throw new IllegalArgumentException("");

        }
        return device.getId();
    }
}
