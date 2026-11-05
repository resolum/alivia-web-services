package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HouseholdDeviceRegistryRepository extends JpaRepository<HouseholdDeviceRegistry, Long> {

    Optional<HouseholdDeviceRegistry> findByInstallationId(InstallationId installationId);

    boolean existsByInstallationId(InstallationId installationId);

    boolean existsByHouseholdId(Long householdId);

    /**
     * The registry of the installation, locked until the transaction ends. Every change of a household's devices holds
     * this lock, so changes to the same household happen one after the other: the plan's limits cannot be exceeded by
     * two requests at once and each table version is used once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HouseholdDeviceRegistry r where r.installationId = :installationId")
    Optional<HouseholdDeviceRegistry> findByInstallationIdForUpdate(@Param("installationId") InstallationId installationId);

    /** The registry that holds the device in an active entry, locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HouseholdDeviceRegistry r where exists "
            + "(select 1 from DeviceAssignment e where e.registry = r and e.device.id = :deviceId and e.unassignedAt is null)")
    Optional<HouseholdDeviceRegistry> findByActiveDeviceIdForUpdate(@Param("deviceId") Long deviceId);
}
