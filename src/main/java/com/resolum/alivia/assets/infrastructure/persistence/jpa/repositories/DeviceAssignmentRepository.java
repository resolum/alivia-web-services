package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceAssignmentRepository extends JpaRepository<DeviceAssignment, Long> {

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
