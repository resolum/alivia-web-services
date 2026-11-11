package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Read access to the history of the placements of a device; the active ones are changed through the registry. */
@Repository
public interface DeviceAssignmentRepository extends JpaRepository<DeviceAssignment, Long> {

    /** The assignment the device has now, if any. */
    @Query("select a from DeviceAssignment a where a.device.id = :deviceId and a.unassignedAt is null")
    Optional<DeviceAssignment> findActiveByDeviceId(@Param("deviceId") Long deviceId);

    /** The assignments that these devices have now (a device without one is absent), in one query. */
    @Query("select a from DeviceAssignment a left join fetch a.registry where a.device.id in :deviceIds and a.unassignedAt is null")
    List<DeviceAssignment> findActiveByDeviceIdIn(@Param("deviceIds") Collection<Long> deviceIds);

    /** Every placement of the device, closed or not, most recent first. */
    @Query("select a from DeviceAssignment a where a.device.id = :deviceId order by a.assignedAt desc, a.id desc")
    List<DeviceAssignment> findHistoryByDeviceId(@Param("deviceId") Long deviceId);
}
