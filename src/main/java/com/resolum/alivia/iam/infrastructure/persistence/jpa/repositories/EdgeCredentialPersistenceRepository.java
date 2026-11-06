package com.resolum.alivia.iam.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data repository for {@link EdgeCredential} aggregates.
 */
public interface EdgeCredentialPersistenceRepository extends JpaRepository<EdgeCredential, Long> {

    Optional<EdgeCredential> findByEdgeId(Long edgeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EdgeCredential c where c.edgeId = :edgeId")
    Optional<EdgeCredential> findByEdgeIdForUpdate(@Param("edgeId") Long edgeId);

    /** Sets the last use only when none is recorded or the recorded one is older than {@code staleBefore}. */
    @Modifying
    @Query("update EdgeCredential c set c.lastUsedAt = :usedAt "
            + "where c.edgeId = :edgeId and (c.lastUsedAt is null or c.lastUsedAt < :staleBefore)")
    int recordUse(@Param("edgeId") Long edgeId, @Param("usedAt") LocalDateTime usedAt,
                  @Param("staleBefore") LocalDateTime staleBefore);
}
