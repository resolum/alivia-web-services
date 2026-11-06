package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeInventoryItem;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import com.resolum.alivia.assets.domain.model.queries.*;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EdgeQueryServiceImpl implements EdgeQueryService {
    private final EdgeRepository edgeRepository;
    private final HouseholdDeviceRegistryRepository registryRepository;

    public EdgeQueryServiceImpl(EdgeRepository edgeRepository, HouseholdDeviceRegistryRepository registryRepository) {
        this.edgeRepository = edgeRepository;
        this.registryRepository = registryRepository;
    }

    @Override
    public Optional<Edge> handle(GetEdgeByIdQuery query) {
        return edgeRepository.findById(query.id());
    }

    @Override
    public List<Edge> handle(GetAllEdgesByBusinessIdQuery query) {
        return edgeRepository.findAllByBusinessId(new BusinessId(query.businessId()));
    }

    @Override
    public boolean handle(ExistsEdgeByMacAddressQuery query) {
        return edgeRepository.existsEdgeByMacAddress(new MacAddress(query.macAddress()));
    }

    @Override
    public Optional<Edge> handle(GetEdgeByMacAddressQuery query) {
        return edgeRepository.findEdgeByMacAddress(new MacAddress(query.macAddress()));
    }

    @Override
    public Long handle(GetEdgeCountByBusinessIdQuery query) {
        return edgeRepository.countByBusinessId(new BusinessId(query.businessId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EdgeInventoryItem> handle(GetEdgesQuery query) {
        var edges = edgeRepository.findAll(matching(query), query.pageable());
        var installations = installationsOf(edges.getContent().stream().map(Edge::getId).toList());
        return edges.map(edge -> new EdgeInventoryItem(edge, installations.get(edge.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EdgeInventoryItem> handle(GetEdgeInventoryItemByIdQuery query) {
        return edgeRepository.findById(query.id()).map(edge -> new EdgeInventoryItem(edge,
                installationsOf(List.of(edge.getId())).get(edge.getId())));
    }

    /** The installation of each edge that is assigned, for a whole page in one query. */
    private Map<Long, Long> installationsOf(List<Long> edgeIds) {
        var installations = new HashMap<Long, Long>();
        if (edgeIds.isEmpty()) {
            return installations;
        }
        for (var registry : registryRepository.findAllByAssignedEdgeIdIn(edgeIds)) {
            var installationId = registry.getInstallationId().installationId();
            if (registry.getMicrophoneEdge() != null) {
                installations.putIfAbsent(registry.getMicrophoneEdge().getId(), installationId);
            }
            if (registry.getActuatorEdge() != null) {
                installations.putIfAbsent(registry.getActuatorEdge().getId(), installationId);
            }
        }
        return installations;
    }

    private static Specification<Edge> matching(GetEdgesQuery query) {
        return (root, criteria, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("businessId").get("businessId"), query.businessId()));
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("assetStatus"), query.status()));
            }
            if (query.edgeType() != null) {
                predicates.add(builder.equal(root.get("edgeType"), query.edgeType()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
