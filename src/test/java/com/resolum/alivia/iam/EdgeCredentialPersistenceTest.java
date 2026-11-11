package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;
import com.resolum.alivia.iam.infrastructure.persistence.jpa.repositories.EdgeCredentialPersistenceRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the database does for the credentials of the edge services. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:edgecredentials;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class EdgeCredentialPersistenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    @Autowired
    private EdgeCredentialPersistenceRepository credentials;
    @Autowired
    private EntityManager entityManager;

    @Test
    void storesAndFindsTheCredentialOfAnEdgeWithoutExposingThePlainSecret() {
        credentials.saveAndFlush(EdgeCredential.issue(7L, EdgeKind.MICROPHONE, "a".repeat(64), 3L, NOW));
        entityManager.clear();

        var found = credentials.findByEdgeId(7L).orElseThrow();

        assertEquals(EdgeKind.MICROPHONE, found.getEdgeKind());
        assertEquals(3L, found.getIssuedBy());
        assertEquals(NOW, found.getIssuedAt());
        assertTrue(found.matches("a".repeat(64)));
        assertTrue(credentials.findByEdgeId(8L).isEmpty());
        assertTrue(credentials.findByEdgeIdForUpdate(7L).isPresent());
    }

    @Test
    void anEdgeHasOneCredentialOnly() {
        credentials.saveAndFlush(EdgeCredential.issue(7L, EdgeKind.MICROPHONE, "hash-1", 3L, NOW));

        assertThrows(DataIntegrityViolationException.class,
                () -> credentials.saveAndFlush(EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash-2", 3L, NOW)));
    }

    @Test
    void theLastUseIsWrittenOnlyWhenNoRecentUseWasRecorded() {
        credentials.saveAndFlush(EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash", 3L, NOW));
        entityManager.clear();

        assertEquals(1, credentials.recordUse(7L, NOW, NOW.minusMinutes(1)));
        // 30 seconds later: the recorded use is within the minute, so nothing is written.
        assertEquals(0, credentials.recordUse(7L, NOW.plusSeconds(30), NOW.minusSeconds(30)));
        // Two minutes later it is stale.
        assertEquals(1, credentials.recordUse(7L, NOW.plusMinutes(2), NOW.plusMinutes(1)));
        entityManager.clear();

        assertEquals(NOW.plusMinutes(2), credentials.findByEdgeId(7L).orElseThrow().getLastUsedAt());
        assertEquals(0, credentials.recordUse(99L, NOW, NOW));
    }

    @Test
    void recordingTheUseDoesNotTouchTheSecretOrTheRevocation() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash", 3L, NOW);
        credential.revoke(NOW, 9L, "REVOKED_BY_ADMINISTRATOR");
        credentials.saveAndFlush(credential);
        entityManager.clear();

        credentials.recordUse(7L, NOW.plusHours(1), NOW);
        entityManager.clear();

        var found = credentials.findByEdgeId(7L).orElseThrow();
        assertEquals(NOW, found.getRevokedAt());
        assertEquals(9L, found.getRevokedBy());
        assertEquals("REVOKED_BY_ADMINISTRATOR", found.getRevokedReason());
        assertTrue(found.matches("hash"));
        assertEquals(NOW.plusHours(1), found.getLastUsedAt());
    }
}
