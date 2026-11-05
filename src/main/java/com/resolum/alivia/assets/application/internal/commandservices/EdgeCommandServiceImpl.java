package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeStillAssigned;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EdgeCommandServiceImpl implements EdgeCommandService {
    private final EdgeRepository edgeRepository;
    private final DeviceRepository deviceRepository;
    private  final ExternalBusinessService externalBusinessService;
    private final HouseholdDeviceRegistryRepository registryRepository;
    private final ExternalIamService externalIamService;

    public EdgeCommandServiceImpl(EdgeRepository edgeRepository, DeviceRepository deviceRepository,
                                  ExternalBusinessService externalBusinessService,
                                  HouseholdDeviceRegistryRepository registryRepository,
                                  ExternalIamService externalIamService) {
        this.edgeRepository = edgeRepository;
        this.deviceRepository = deviceRepository;
        this.externalBusinessService = externalBusinessService;
        this.registryRepository = registryRepository;
        this.externalIamService = externalIamService;
    }

    /**
     * Registering an edge only puts it in the inventory of the business: it starts {@code AVAILABLE} and belongs to no
     * installation. It becomes {@code IN_USE} when an installation work places it in an installation.
     */
    @Override
    public Long handle(RegisterEdgeCommand command) {
        // Building the edge validates the MAC and the type before anything is looked up.
        var edge = new Edge(command.businessId(), command.macAddress(), command.edgeType());
        if (!externalBusinessService.existsBusiness(command.businessId())) {
            throw new BusinessNotFound(String.format("business with id %d not found", command.businessId()));
        }
        if (edgeRepository.existsEdgeByMacAddress(new MacAddress(command.macAddress()))){
         throw   new MacAddressAlreadyExists(command.macAddress());
        }
        if (deviceRepository.existsDeviceByMacAddress(new MacAddress(command.macAddress()))){
            throw   new MacAddressAlreadyExists(command.macAddress());

        }
        saveEdge(edge);
        return edge.getId();

    }

    /**
     * Changing the type of an edge that is placed in an installation is refused (it would sit in the wrong place); for
     * an edge that is not placed, a new type revokes its credential, which names the old type. As with devices, the
     * registry that holds the edge is locked before the edge is read.
     */
    @Override
    @Transactional
    public Long handle(UpdateEdgeCommand command) {
        var placedIn = registryRepository.findByAssignedEdgeIdForUpdate(command.edgeId());
        var edge= edgeRepository.findById(command.edgeId()).orElseThrow(()->new EdgeNotFound(command.edgeId()));
        var newMacAddress = new MacAddress(command.macAddress());
        if (!newMacAddress.equals(edge.getMacAddress())
                && (edgeRepository.existsEdgeByMacAddress(newMacAddress) || deviceRepository.existsDeviceByMacAddress(newMacAddress))) {
            throw new MacAddressAlreadyExists(newMacAddress.macAddress());
        }
        var changesType = edge.changesTypeTo(command.edgeType());
        if (changesType && placedIn.isPresent()) {
            throw EdgeStillAssigned.typeChange(edge.getId());
        }
        edge.update(command.macAddress(),command.edgeType());
        saveEdge(edge);
        if (changesType) {
            externalIamService.revokeEdgeCredential(edge.getId());
        }
        return edge.getId();

    }

    private void saveEdge(Edge edge) {
        try {
            edgeRepository.saveAndFlush(edge);
        } catch (DataIntegrityViolationException exception) {
            // Another request registered the same MAC between the check and the insert (uk_edges_mac_address).
            throw new MacAddressAlreadyExists(edge.getMacAddress().macAddress());
        }
    }

    /**
     * {@code IN_USE} means that the edge is placed in an installation, so it is only reached by placing it; and an edge
     * that is placed cannot become {@code AVAILABLE} or {@code RETIRED} until it is removed from the installation, as
     * with devices. Going to maintenance or broken while placed is allowed: the equipment can fail in the household.
     * Retiring an edge revokes its credential.
     */
    @Override
    @Transactional
    public Long handle(ChangeEdgeStatusCommand command) {
        var placedIn = registryRepository.findByAssignedEdgeIdForUpdate(command.edgeId());
        var edge = edgeRepository.findById(command.edgeId()).orElseThrow(()->new EdgeNotFound(command.edgeId()));
        ensureStatusMatchesPlacement(edge, placedIn.isPresent(), command.status());
        edge.updateAssetStatus(command.status());
        edgeRepository.saveAndFlush(edge);
        if (edge.getAssetStatus() == AssetStatus.RETIRED) {
            externalIamService.revokeEdgeCredential(edge.getId());
        }
        return edge.getId();
    }

    private void ensureStatusMatchesPlacement(Edge edge, boolean placed, String requestedStatus) {
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
            throw new IllegalArgumentException("An edge becomes IN_USE only by being placed in an installation.");
        }
        if (placed && (target == AssetStatus.AVAILABLE || target == AssetStatus.RETIRED)) {
            throw new EdgeStillAssigned(edge.getId(), target);
        }
    }
}
