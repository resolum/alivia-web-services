package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeTableVersion;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    /**
     * The registry that has the edge as one of its edge services, locked until the transaction ends. Changing an edge
     * that is placed takes this lock before reading the edge, the same order as placing and removing it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HouseholdDeviceRegistry r where r.microphoneEdge.id = :edgeId or r.actuatorEdge.id = :edgeId")
    Optional<HouseholdDeviceRegistry> findByAssignedEdgeIdForUpdate(@Param("edgeId") Long edgeId);

    /** The registries that have any of these edges as one of their edge services, in one query. */
    @Query("select r from HouseholdDeviceRegistry r "
            + "where r.microphoneEdge.id in :edgeIds or r.actuatorEdge.id in :edgeIds")
    List<HouseholdDeviceRegistry> findAllByAssignedEdgeIdIn(@Param("edgeIds") Collection<Long> edgeIds);

    /** The registry that has the edge as one of its edge services, without locking it (to read its table). */
    @Query("select r from HouseholdDeviceRegistry r where r.microphoneEdge.id = :edgeId or r.actuatorEdge.id = :edgeId")
    Optional<HouseholdDeviceRegistry> findByAssignedEdgeId(@Param("edgeId") Long edgeId);

    /**
     * Only the installation and the table version of the active registry that has the edge as one of its edge services:
     * an edge that is already up to date is answered without reading any entry.
     */
    @Query("select new com.resolum.alivia.assets.domain.model.valueobjects.EdgeTableVersion("
            + "r.installationId.installationId, r.tableVersion) from HouseholdDeviceRegistry r "
            + "where (r.microphoneEdge.id = :edgeId or r.actuatorEdge.id = :edgeId) "
            + "and r.status = com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus.ACTIVE")
    Optional<EdgeTableVersion> findActiveTableVersionByAssignedEdgeId(@Param("edgeId") Long edgeId);

    /** The table version of an installation, while its registry is active: what the waiting edge services are checked against. */
    @Query("select r.tableVersion from HouseholdDeviceRegistry r where r.installationId.installationId = :installationId "
            + "and r.status = com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus.ACTIVE")
    Optional<Long> findActiveTableVersionByInstallationId(@Param("installationId") Long installationId);
}
