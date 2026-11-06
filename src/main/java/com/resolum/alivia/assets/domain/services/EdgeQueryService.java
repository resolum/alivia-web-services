package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.queries.*;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeInventoryItem;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface EdgeQueryService {

    Optional<Edge>handle(GetEdgeByIdQuery query);
    List<Edge>handle(GetAllEdgesByBusinessIdQuery query);
     boolean handle(ExistsEdgeByMacAddressQuery query);
     Optional<Edge>handle(GetEdgeByMacAddressQuery query);
     Long handle(GetEdgeCountByBusinessIdQuery query);

    /** A page of the inventory of edges of the business, with the installation where each one is assigned. */
    Page<EdgeInventoryItem> handle(GetEdgesQuery query);

    /** One edge of the inventory, with the installation where it is assigned; empty when it does not exist. */
    Optional<EdgeInventoryItem> handle(GetEdgeInventoryItemByIdQuery query);
}
