package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceInventoryItem;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import com.resolum.alivia.assets.domain.model.queries.*;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DeviceQueryServiceImpl implements DeviceQueryService {
    private final DeviceRepository deviceRepository;
    private final DeviceAssignmentRepository assignmentRepository;

    public DeviceQueryServiceImpl(DeviceRepository deviceRepository, DeviceAssignmentRepository assignmentRepository) {
        this.deviceRepository = deviceRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public Optional<Device> handle(GetDeviceByIdQuery query) {
        return deviceRepository.findById(query.id());
    }

    @Override
    public List<Device> handle(GetAllDevicesByBusinessIdQuery query) {
        return deviceRepository.findAllByBusinessId(new BusinessId(query.businessId()));
    }

    @Override
    public boolean handle(ExistsDeviceByMacAddressQuery query) {
        var aux= new MacAddress(query.macAddress());
        return deviceRepository.existsDeviceByMacAddress(aux);
    }

    @Override
    public Optional<Device> handle(GetDeviceByMacAddressQuery query) {
        var aux= new MacAddress(query.macAddress());

        return deviceRepository.findDeviceByMacAddress(aux);
    }

    @Override
    public Long handle(GetDeviceCountByBusinessIdQuery query) {
        return  deviceRepository.countByBusinessId(new BusinessId(query.businessId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceInventoryItem> handle(GetDevicesQuery query) {
        var devices = deviceRepository.findAll(matching(query), query.pageable());
        var installations = installationsOf(devices.getContent().stream().map(Device::getId).toList());
        return devices.map(device -> new DeviceInventoryItem(device, installations.get(device.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceInventoryItem> handle(GetDeviceInventoryItemByIdQuery query) {
        return deviceRepository.findById(query.id()).map(device -> new DeviceInventoryItem(device,
                installationsOf(List.of(device.getId())).get(device.getId())));
    }

    /** The installation of each device that is assigned, for a whole page in one query. */
    private Map<Long, Long> installationsOf(List<Long> deviceIds) {
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        var installations = new HashMap<Long, Long>();
        for (var assignment : assignmentRepository.findActiveByDeviceIdIn(deviceIds)) {
            var installationId = installationOf(assignment);
            if (installationId != null) {
                installations.putIfAbsent(assignment.getDevice().getId(), installationId);
            }
        }
        return installations;
    }

    /**
     * Assignments made before the installation was recorded on them have none of their own (the column is nullable only
     * for them); the registry of their household, which the migration linked them to, knows it.
     */
    private static Long installationOf(DeviceAssignment assignment) {
        if (assignment.getInstallationId() != null) {
            return assignment.getInstallationId();
        }
        return assignment.getRegistry() == null ? null : assignment.getRegistry().getInstallationId().installationId();
    }

    private static Specification<Device> matching(GetDevicesQuery query) {
        return (root, criteria, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("businessId").get("businessId"), query.businessId()));
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("assetStatus"), query.status()));
            }
            if (query.deviceType() != null) {
                predicates.add(builder.equal(root.get("deviceType"), query.deviceType()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

}
