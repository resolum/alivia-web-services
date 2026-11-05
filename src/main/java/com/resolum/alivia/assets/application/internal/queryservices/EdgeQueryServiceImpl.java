package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.queries.*;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EdgeQueryServiceImpl implements EdgeQueryService {
    private final EdgeRepository edgeRepository;
    public EdgeQueryServiceImpl(EdgeRepository edgeRepository){

        this.edgeRepository=edgeRepository;
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
}
