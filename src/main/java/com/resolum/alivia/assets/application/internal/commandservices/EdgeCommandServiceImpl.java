package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class EdgeCommandServiceImpl implements EdgeCommandService {
    private final EdgeRepository edgeRepository;
    private final DeviceRepository deviceRepository;
    private  final ExternalBusinessService externalBusinessService;
    public EdgeCommandServiceImpl(EdgeRepository edgeRepository, DeviceRepository deviceRepository, ExternalBusinessService externalBusinessService) {
        this.edgeRepository = edgeRepository;
        this.deviceRepository = deviceRepository;
        this.externalBusinessService = externalBusinessService;
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
        saveEdge(edge);
        return edge.getId();

    }

    @Override
    public Long handle(UpdateEdgeCommand command) {
        var edge= edgeRepository.findById(command.edgeId()).orElseThrow(()->new EdgeNotFound(command.edgeId()));
        var newMacAddress = new MacAddress(command.macAddress());
        if (!newMacAddress.equals(edge.getMacAddress())
                && (edgeRepository.existsEdgeByMacAddress(newMacAddress) || deviceRepository.existsDeviceByMacAddress(newMacAddress))) {
            throw new MacAddressAlreadyExists(newMacAddress.macAddress());
        }
        edge.update(command.macAddress(),command.edgeType());
        saveEdge(edge);
        return edge.getId();

    }

    private void saveEdge(Edge edge) {
        try {
            edgeRepository.save(edge);
        } catch (DataIntegrityViolationException exception) {
            // Another request registered the same MAC between the check and the insert (uk_edges_mac_address).
            throw new MacAddressAlreadyExists(edge.getMacAddress().macAddress());
        }
    }

    @Override
    public Long handle(ChangeEdgeStatusCommand command) {
        var edge = edgeRepository.findById(command.edgeId()).orElseThrow(()->new EdgeNotFound(command.edgeId()));
        edge.updateAssetStatus(command.status());
        edgeRepository.save(edge);
        return edge.getId();
    }
}
