package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAlreadyRegistered;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.PlaceEdgeInInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RemoveEdgeFromInstallationCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdges;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeSyncStateRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseholdDeviceRegistryCommandServiceImpl implements HouseholdDeviceRegistryCommandService {
    private final HouseholdDeviceRegistryRepository registryRepository;
    private final ExternalBusinessService externalBusinessService;
    private final EdgeRepository edgeRepository;
    private final ExternalIamService externalIamService;
    private final EdgeSyncStateRepository syncStates;

    public HouseholdDeviceRegistryCommandServiceImpl(HouseholdDeviceRegistryRepository registryRepository,
                                                     ExternalBusinessService externalBusinessService,
                                                     EdgeRepository edgeRepository,
                                                     ExternalIamService externalIamService,
                                                     EdgeSyncStateRepository syncStates) {
        this.registryRepository = registryRepository;
        this.externalBusinessService = externalBusinessService;
        this.edgeRepository = edgeRepository;
        this.externalIamService = externalIamService;
        this.syncStates = syncStates;
    }

    @Override
    public Long handle(RegisterInstallationCommand command) {
        var registry = HouseholdDeviceRegistry.open(command.installationId(), command.householdId(), command.businessId());
        if (!externalBusinessService.existsBusiness(command.businessId())) {
            throw new BusinessNotFound(String.format("business with id %d not found", command.businessId()));
        }
        if (registryRepository.existsByInstallationId(new InstallationId(command.installationId()))) {
            throw InstallationAlreadyRegistered.installation(command.installationId());
        }
        if (registryRepository.existsByHouseholdId(command.householdId())) {
            throw InstallationAlreadyRegistered.activeInHousehold(command.householdId());
        }
        try {
            return registryRepository.saveAndFlush(registry).getId();
        } catch (DataIntegrityViolationException exception) {
            // Another request registered the installation or the household first
            // (uk_household_device_registries_installation, uk_household_device_registries_household).
            throw InstallationAlreadyRegistered.installation(command.installationId());
        }
    }

    /**
     * Same order as the devices: the registry is locked first and the edge is read after, so placing an edge cannot
     * overlap with the change of that edge. The edge that leaves loses its credential in the same transaction.
     */
    @Override
    @Transactional
    public InstallationEdges handle(PlaceEdgeInInstallationCommand command) {
        var registry = lockRegistry(command.installationId());
        var edge = edgeRepository.findById(command.edgeId()).orElseThrow(() -> new EdgeNotFound(command.edgeId()));

        var alreadyThere = registry.hasEdge(edge.getId());
        var replaced = registry.placeEdge(edge);

        try {
            edgeRepository.save(edge);
            replaced.ifPresent(edgeRepository::save);
            registryRepository.saveAndFlush(registry);
        } catch (DataIntegrityViolationException exception) {
            // Another registry took the same edge first (uk_household_device_registries_*_edge).
            throw new EdgeNotAvailable(edge.getId(), AssetStatus.IN_USE);
        }
        replaced.ifPresent(left -> {
            externalIamService.revokeEdgeCredential(left.getId());
            syncStates.deleteByEdgeId(left.getId());
        });
        if (!alreadyThere) {
            // What the edge confirmed before refers to another installation, or to a previous stay in this one.
            syncStates.deleteByEdgeId(edge.getId());
        }
        return InstallationEdges.of(registry, replaced.orElse(null));
    }

    @Override
    @Transactional
    public InstallationEdges handle(RemoveEdgeFromInstallationCommand command) {
        var registry = lockRegistry(command.installationId());

        var removed = registry.removeEdge(command.edgeId());

        edgeRepository.save(removed);
        registryRepository.saveAndFlush(registry);
        externalIamService.revokeEdgeCredential(removed.getId());
        syncStates.deleteByEdgeId(removed.getId());
        return InstallationEdges.of(registry, removed);
    }

    private HouseholdDeviceRegistry lockRegistry(Long installationId) {
        return registryRepository.findByInstallationIdForUpdate(new InstallationId(installationId))
                .orElseThrow(() -> new InstallationNotFound(installationId));
    }
}
