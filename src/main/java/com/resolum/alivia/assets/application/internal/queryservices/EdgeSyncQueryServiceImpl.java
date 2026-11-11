package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.EdgeSyncState;
import com.resolum.alivia.assets.domain.model.queries.GetInstallationEdgeSyncQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdgeSync;
import com.resolum.alivia.assets.domain.services.EdgeSyncQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeSyncStateRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class EdgeSyncQueryServiceImpl implements EdgeSyncQueryService {
    private final HouseholdDeviceRegistryRepository registryRepository;
    private final EdgeSyncStateRepository syncStates;

    public EdgeSyncQueryServiceImpl(HouseholdDeviceRegistryRepository registryRepository,
                                    EdgeSyncStateRepository syncStates) {
        this.registryRepository = registryRepository;
        this.syncStates = syncStates;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InstallationEdgeSync> handle(GetInstallationEdgeSyncQuery query) {
        return registryRepository.findByInstallationId(new InstallationId(query.installationId())).map(registry -> {
            var states = syncStates.findAllByInstallationId(query.installationId()).stream()
                    .collect(Collectors.toMap(EdgeSyncState::getEdgeId, Function.identity()));
            var current = registry.getTableVersion();
            var edges = Stream.of(registry.getMicrophoneEdge(), registry.getActuatorEdge())
                    .filter(Objects::nonNull)
                    .map(edge -> edgeSync(edge, states, current))
                    .toList();
            return new InstallationEdgeSync(query.installationId(), current, edges);
        });
    }

    private static InstallationEdgeSync.EdgeSync edgeSync(Edge edge, Map<Long, EdgeSyncState> states, long current) {
        var state = states.get(edge.getId());
        return InstallationEdgeSync.EdgeSync.of(edge.getId(), edge.getEdgeType(), current,
                state == null ? null : state.getAppliedVersion(),
                state == null ? null : state.getAckedAt(),
                state == null ? null : state.getLastSeenAt());
    }
}
