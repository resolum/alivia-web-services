package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceNetworkRepository extends JpaRepository<DeviceNetwork, Long> {

}
