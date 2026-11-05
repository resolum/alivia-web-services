package com.resolum.alivia.assets;

import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeSyncStateRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** The statements that keep what each edge confirmed, as the database runs them. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:edgesync;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class EdgeSyncStatePersistenceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 5, 12, 0);

    @Autowired
    private EdgeSyncStateRepository states;
    @Autowired
    private EntityManager entityManager;

    /**
     * Creates a state the way the application does, except that H2 does not know {@code on conflict do nothing}, which
     * PostgreSQL runs (that statement is checked against PostgreSQL, not here).
     */
    private int seed(Long edgeId, Long installationId, LocalDateTime seenAt) {
        var created = entityManager.createNativeQuery(
                        "insert into edge_sync_states (edge_id, installation_id, last_seen_at) values (?1, ?2, ?3)")
                .setParameter(1, edgeId).setParameter(2, installationId).setParameter(3, seenAt).executeUpdate();
        entityManager.clear();
        return created;
    }

    @Test
    void theConfirmedVersionOnlyGrows() {
        seed(7L, 3L, T0);

        assertEquals(1, states.raiseAppliedVersion(7L, 5L, T0.plusMinutes(1)));
        assertEquals(0, states.raiseAppliedVersion(7L, 5L, T0.plusMinutes(2)));
        assertEquals(0, states.raiseAppliedVersion(7L, 4L, T0.plusMinutes(3)));
        assertEquals(1, states.raiseAppliedVersion(7L, 9L, T0.plusMinutes(4)));
        assertEquals(0, states.raiseAppliedVersion(7L, 0L, T0.plusMinutes(5)));

        var state = states.findById(7L).orElseThrow();
        assertEquals(9L, state.getAppliedVersion());
        assertEquals(T0.plusMinutes(4), state.getAckedAt());
    }

    @Test
    void versionZeroCanBeConfirmedFirst() {
        seed(7L, 3L, T0);

        assertEquals(1, states.raiseAppliedVersion(7L, 0L, T0));

        assertEquals(0L, states.findById(7L).orElseThrow().getAppliedVersion());
    }

    @Test
    void aRaiseOfAnEdgeWithoutStateChangesNothing() {
        assertEquals(0, states.raiseAppliedVersion(8L, 5L, T0));
        assertTrue(states.findById(8L).isEmpty());
    }

    @Test
    void whenTheEdgeWasHeardIsWrittenOnlyIfTheLastSightingIsOlderThanTheLimit() {
        seed(7L, 3L, T0);

        assertEquals(0, states.touchLastSeen(7L, T0.plusSeconds(30), T0.minusSeconds(30)));
        assertEquals(T0, states.findById(7L).orElseThrow().getLastSeenAt());
        assertEquals(1, states.touchLastSeen(7L, T0.plusMinutes(2), T0.plusMinutes(1)));
        assertEquals(T0.plusMinutes(2), states.findById(7L).orElseThrow().getLastSeenAt());
    }

    @Test
    void anEdgeThatMovedToAnotherInstallationStartsOver() {
        seed(7L, 3L, T0);
        states.raiseAppliedVersion(7L, 5L, T0);

        assertEquals(0, states.resetIfOtherInstallation(7L, 3L));
        assertEquals(5L, states.findById(7L).orElseThrow().getAppliedVersion());

        assertEquals(1, states.resetIfOtherInstallation(7L, 4L));
        var state = states.findById(7L).orElseThrow();
        assertEquals(4L, state.getInstallationId());
        assertNull(state.getAppliedVersion());
        assertNull(state.getAckedAt());
    }

    @Test
    void theStateOfAnEdgeIsForgottenAndTheOthersAreKept() {
        seed(7L, 3L, T0);
        seed(8L, 3L, T0);
        seed(9L, 4L, T0);

        assertEquals(1, states.deleteByEdgeId(7L));
        assertEquals(0, states.deleteByEdgeId(7L));

        assertTrue(states.findById(7L).isEmpty());
        assertEquals(1, states.findAllByInstallationId(3L).size());
        assertEquals(8L, states.findAllByInstallationId(3L).get(0).getEdgeId());
        assertEquals(1, states.findAllByInstallationId(4L).size());
    }

    @Test
    void changesAreVisibleRightAfterTheStatementEvenIfTheStateWasLoadedBefore() {
        seed(7L, 3L, T0);
        var before = states.findById(7L).orElseThrow();
        assertNull(before.getAppliedVersion());

        states.raiseAppliedVersion(7L, 5L, T0);

        assertEquals(5L, states.findById(7L).orElseThrow().getAppliedVersion());
        entityManager.clear();
        assertEquals(5L, states.findById(7L).orElseThrow().getAppliedVersion());
    }
}
