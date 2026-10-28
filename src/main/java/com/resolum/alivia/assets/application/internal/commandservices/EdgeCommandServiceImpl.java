package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalCompanyService;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import org.springframework.stereotype.Service;

@Service
public class EdgeCommandServiceImpl implements EdgeCommandService {
    private final EdgeRepository edgeRepository;
    private final DeviceRepository deviceRepository;
    private  final ExternalCompanyService externalCompanyService;
    public EdgeCommandServiceImpl(EdgeRepository edgeRepository, DeviceRepository deviceRepository, ExternalCompanyService externalCompanyService) {
        this.edgeRepository = edgeRepository;
        this.deviceRepository = deviceRepository;
        this.externalCompanyService = externalCompanyService;
    }

    @Override
    public Long handle(RegisterEdgeCommand command) {
        if (edgeRepository.existsEdgeByMacAddress(new MacAddress(command.macAddress()))){
         throw   new MacAddressAlreadyExists(command.macAddress());
        }
        if (deviceRepository.existsDeviceByMacAddress(new MacAddress(command.macAddress()))){
            throw   new MacAddressAlreadyExists(command.macAddress());

        }
        var edge = new Edge(command.businessId(),command.macAddress(),command.edgeType());
        try {
            edgeRepository.save(edge);
        }catch (Exception e){

        }
        return edge.getId();

    }

    @Override
    public Long handle(UpdateEdgeCommand command) {
        var edge= edgeRepository.findById(command.edgeId()).orElseThrow(()->new EdgeNotFound(command.edgeId()));
        edge.update(command.macAddress(),command.edgeType());
        try {
            edgeRepository.save(edge);
        }catch (Exception e){

        }
        return edge.getId();

    }

    @Override
    public Long handle(ChangeEdgeStatusCommand command) {
        var edge = edgeRepository.findById(command.edgeId()).orElseThrow(()->new EdgeNotFound(command.edgeId()));
        edge.updateAssetStatus(command.status());
        edgeRepository.save(edge);
        return edge.getId();
    }
}
