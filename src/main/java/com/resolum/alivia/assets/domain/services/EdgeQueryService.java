package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface EdgeQueryService {

    Optional<Edge>handle(GetEdgeByIdQuery query);
    List<Edge>handle(GetAllEdgesByBusinessIdQuery query);
     boolean handle(ExistsEdgeByMacAddressQuery query);
     Optional<Edge>handle(GetEdgeByMacAddressQuery query);
     Long handle(GetEdgeCountByBusinessIdQuery query);
}
