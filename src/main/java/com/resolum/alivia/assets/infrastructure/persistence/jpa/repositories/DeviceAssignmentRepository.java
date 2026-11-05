package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceAssignmentRepository extends JpaRepository<DeviceAssignment, Long> {

    /** The assignment the device has now, if any. */
    @Query("select a from DeviceAssignment a where a.device.id = :deviceId and a.unassignedAt is null")
    Optional<DeviceAssignment> findActiveByDeviceId(@Param("deviceId") Long deviceId);

    /**
     * The assignment the device has now, locked until the transaction ends, so two requests cannot close the
     * same assignment and overwrite each other's reason.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DeviceAssignment a where a.device.id = :deviceId and a.unassignedAt is null")
    Optional<DeviceAssignment> findActiveByDeviceIdForUpdate(@Param("deviceId") Long deviceId);

    /** Every placement of the device, closed or not, most recent first. */
    @Query("select a from DeviceAssignment a where a.device.id = :deviceId order by a.assignedAt desc, a.id desc")
    List<DeviceAssignment> findHistoryByDeviceId(@Param("deviceId") Long deviceId);

    /** Devices of that type currently assigned to the household. */
    @Query("select count(a) from DeviceAssignment a "
            + "where a.householdId = :householdId and a.unassignedAt is null and a.device.deviceType = :type")
    long countActiveByHouseholdIdAndDeviceType(@Param("householdId") Long householdId,
                                               @Param("type") DeviceType type);

    /** Devices of that type currently assigned to the room of the household. */
    @Query("select count(a) from DeviceAssignment a "
            + "where a.householdId = :householdId and a.roomKey = :roomKey and a.unassignedAt is null "
            + "and a.device.deviceType = :type")
    long countActiveByHouseholdIdAndRoomKeyAndDeviceType(@Param("householdId") Long householdId,
                                                         @Param("roomKey") String roomKey,
                                                         @Param("type") DeviceType type);
}
