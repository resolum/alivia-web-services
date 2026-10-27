package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.queries.ExistsEdgeByMacAddressQuery;
import com.resolum.alivia.assets.domain.model.queries.GetAllEdgesByBusinessIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByMacAddressQuery;

import java.util.List;
import java.util.Optional;

public interface EdgeQueryService {

    Optional<Edge>handle(GetEdgeByIdQuery query);
    List<Edge>handle(GetAllEdgesByBusinessIdQuery query);
     boolean handle(ExistsEdgeByMacAddressQuery query);
     Optional<Edge>handle(GetEdgeByMacAddressQuery query);

}
