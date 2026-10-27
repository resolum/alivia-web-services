package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EdgeRepository extends JpaRepository<Edge,Long> {
    Optional<Edge> findEdgeByBusinessId(BusinessId businessId);

    List<Edge> findAllByBusinessId(BusinessId businessId);

    boolean existsEdgeByMacAddress(MacAddress macAddress);

    Optional<Edge> findEdgeByMacAddress(MacAddress macAddress);

}
