package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.EdgeProvisioningToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface EdgeProvisioningTokenRepository extends JpaRepository<EdgeProvisioningToken, Long> {
    void deleteByEdgeId(Long edgeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EdgeProvisioningToken t where t.tokenHash=:hash")
    Optional<EdgeProvisioningToken> findByTokenHashForUpdate(@Param("hash") String hash);
}
