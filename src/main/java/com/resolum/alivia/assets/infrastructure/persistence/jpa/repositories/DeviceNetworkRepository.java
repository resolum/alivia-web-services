package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.valueobjects.NetworkStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceNetworkRepository extends JpaRepository<DeviceNetwork, Long> {

    Optional<DeviceNetwork> findByInstallationId(InstallationId installationId);

    boolean existsByInstallationId(InstallationId installationId);

    /** Networks that use the edge as their microphone edge or their actuator edge. */
    @Query("select n from DeviceNetwork n where n.microphoneEdge.id = :edgeId or n.actuatorEdge.id = :edgeId")
    List<DeviceNetwork> findAllByEdgeId(@Param("edgeId") Long edgeId);

    boolean existsByHouseholdIdAndNetworkStatus(Long householdId, NetworkStatus networkStatus);

    /**
     * The network of the installation, locked until the transaction ends. A household has one active network,
     * so locking it serializes the changes made to the devices of that household.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from DeviceNetwork n where n.installationId = :installationId")
    Optional<DeviceNetwork> findByInstallationIdForUpdate(@Param("installationId") InstallationId installationId);
}
