package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeScopeByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeTableVersionByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetTableVersionByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeScopeView;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeTableVersion;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class HouseholdDeviceRegistryQueryServiceImpl implements HouseholdDeviceRegistryQueryService {
    private final HouseholdDeviceRegistryRepository registryRepository;

    public HouseholdDeviceRegistryQueryServiceImpl(HouseholdDeviceRegistryRepository registryRepository) {
        this.registryRepository = registryRepository;
    }

    /**
     * The registry row (with its version) and its entries are two reads. At the default isolation a change could commit
     * between them and the table would carry the entries of a newer version under an older number; a single snapshot
     * rules that out.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<EdgeAccessTable> handle(GetEdgeAccessTableByEdgeIdQuery query) {
        return registryRepository.findByAssignedEdgeId(query.edgeId())
                .filter(HouseholdDeviceRegistry::isActive)
                .map(HouseholdDeviceRegistry::table);
    }

    /** Same snapshot as the table: the registry and its entries are read together. */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<EdgeScopeView> handle(GetEdgeScopeByEdgeIdQuery query) {
        return registryRepository.findByAssignedEdgeId(query.edgeId())
                .filter(HouseholdDeviceRegistry::isActive)
                .map(registry -> new EdgeScopeView(query.edgeId(),
                        registry.getMicrophoneEdge() != null && query.edgeId().equals(registry.getMicrophoneEdge().getId())
                                ? EdgeType.MICROPHONE_EDGE : EdgeType.ACTUATOR_EDGE,
                        registry.getInstallationId().installationId(), registry.getHouseholdId(),
                        registry.activeAssignments().stream()
                                .map(assignment -> new EdgeScopeView.ScopedDevice(assignment.getDevice().getId(),
                                        assignment.getDevice().getAlias(), assignment.getDevice().getDeviceType(),
                                        assignment.getDevice().getAssetStatus(), assignment.getRoomKey()))
                                .toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EdgeTableVersion> handle(GetEdgeTableVersionByEdgeIdQuery query) {
        return registryRepository.findActiveTableVersionByAssignedEdgeId(query.edgeId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> handle(GetTableVersionByInstallationIdQuery query) {
        return registryRepository.findActiveTableVersionByInstallationId(query.installationId());
    }
}
