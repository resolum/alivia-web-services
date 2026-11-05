package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findDeviceByBusinessId(BusinessId businessId);

    List<Device> findAllByBusinessId(BusinessId businessId);

    boolean existsDeviceByMacAddress(MacAddress macAddress);

    Optional<Device> findDeviceByMacAddress(MacAddress macAddress);

    Long countByBusinessId(BusinessId businessId);

}
