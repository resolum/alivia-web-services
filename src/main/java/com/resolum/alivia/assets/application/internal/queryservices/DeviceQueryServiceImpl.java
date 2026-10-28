package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.queries.*;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DeviceQueryServiceImpl implements DeviceQueryService {
    private final DeviceRepository deviceRepository;
    public DeviceQueryServiceImpl(DeviceRepository deviceRepository){

        this.deviceRepository=deviceRepository;
    }

    @Override
    public Optional<Device> handle(GetDevIceByIdQuery query) {
        return deviceRepository.findById(query.id());
    }

    @Override
    public List<Device> handle(GetAllDevicesByBusinessIdQuery query) {
        return deviceRepository.findAllByBusinessId(new BusinessId(query.businessId()));
    }

    @Override
    public boolean handle(ExistsDeviceByMacAddressQuery query) {
        var aux= new MacAddress(query.macAddress());
        return deviceRepository.existsDeviceByMacAddress(aux);
    }

    @Override
    public Optional<Device> handle(GetDeviceByMacAddressQuery query) {
        var aux= new MacAddress(query.macAddress());

        return deviceRepository.findDeviceByMacAddress(aux);
    }

    @Override
    public Long handle(GetDeviceCountByBusinessIdQuery query) {
        return  deviceRepository.countByBusinessId(new BusinessId(query.businessId()));
    }

}
