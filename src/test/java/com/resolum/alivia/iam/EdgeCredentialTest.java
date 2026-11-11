package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeCredentialTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    @Test
    void anIssuedCredentialIsActiveAndKnowsItsEdgeKindAndWhoIssuedItWhen() {
        var credential = EdgeCredential.issue(7L, EdgeKind.MICROPHONE, "hash-1", 3L, NOW);

        assertEquals(7L, credential.getEdgeId());
        assertEquals(EdgeKind.MICROPHONE, credential.getEdgeKind());
        assertEquals(3L, credential.getIssuedBy());
        assertEquals(NOW, credential.getIssuedAt());
        assertTrue(credential.isActive());
        assertNull(credential.getLastUsedAt());
        assertNull(credential.getRevokedBy());
        assertNull(credential.getRevokedReason());
        assertTrue(credential.matches("hash-1"));
        assertFalse(credential.matches("hash-2"));
        assertFalse(credential.matches(null));
    }

    @Test
    void rejectsAnInvalidEdgeKindHashAccountOrMoment() {
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(null, EdgeKind.ACTUATOR, "h", 1L, NOW));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(0L, EdgeKind.ACTUATOR, "h", 1L, NOW));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, null, "h", 1L, NOW));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, EdgeKind.ACTUATOR, null, 1L, NOW));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, EdgeKind.ACTUATOR, "  ", 1L, NOW));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, EdgeKind.ACTUATOR, "h", null, NOW));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, EdgeKind.ACTUATOR, "h", 0L, NOW));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, EdgeKind.ACTUATOR, "h", 1L, null));
    }

    @Test
    void rotatingReplacesTheSecretAtOnceAndRecordsWhoAndWhen() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "old", 3L, NOW);

        credential.rotate(EdgeKind.ACTUATOR, "new", 4L, NOW.plusDays(30));

        assertFalse(credential.matches("old"));
        assertTrue(credential.matches("new"));
        assertEquals(4L, credential.getIssuedBy());
        assertEquals(NOW.plusDays(30), credential.getIssuedAt());
        assertThrows(IllegalArgumentException.class, () -> credential.rotate(EdgeKind.ACTUATOR, "x", null, NOW));
    }

    @Test
    void revokingStopsTheCredentialAndIsIdempotent() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash", 3L, NOW);

        assertTrue(credential.revoke(NOW, 9L, "REVOKED_BY_ADMINISTRATOR"));
        assertFalse(credential.isActive());
        assertEquals(NOW, credential.getRevokedAt());
        assertEquals(9L, credential.getRevokedBy());
        assertEquals("REVOKED_BY_ADMINISTRATOR", credential.getRevokedReason());

        // A second revocation changes nothing, not even who or why.
        assertFalse(credential.revoke(NOW.plusHours(1), null, "EDGE_RETIRED"));
        assertEquals(NOW, credential.getRevokedAt());
        assertEquals(9L, credential.getRevokedBy());
        assertEquals("REVOKED_BY_ADMINISTRATOR", credential.getRevokedReason());
    }

    @Test
    void aChangeOfTheEdgeRevokesWithoutAnAdministratorButWithAReason() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash", 3L, NOW);

        assertTrue(credential.revoke(NOW, null, "EDGE_RETIRED"));

        assertNull(credential.getRevokedBy());
        assertEquals("EDGE_RETIRED", credential.getRevokedReason());
    }

    @Test
    void revokingNeedsAReasonAMomentAndAValidAccount() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash", 3L, NOW);

        assertThrows(IllegalArgumentException.class, () -> credential.revoke(NOW, null, null));
        assertThrows(IllegalArgumentException.class, () -> credential.revoke(NOW, null, "  "));
        assertThrows(IllegalArgumentException.class, () -> credential.revoke(NOW, null, "X".repeat(41)));
        assertThrows(IllegalArgumentException.class, () -> credential.revoke(NOW, 0L, "EDGE_RETIRED"));
        assertThrows(IllegalArgumentException.class, () -> credential.revoke(null, null, "EDGE_RETIRED"));
        assertTrue(credential.isActive());
    }

    @Test
    void rotatingARevokedCredentialMakesItValidAgainAndForgetsTheRevocation() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "old", 3L, NOW);
        credential.revoke(NOW, 9L, "REVOKED_BY_ADMINISTRATOR");

        credential.rotate(EdgeKind.MICROPHONE, "new", 3L, NOW.plusDays(1));

        assertTrue(credential.isActive());
        assertNull(credential.getRevokedAt());
        assertNull(credential.getRevokedBy());
        assertNull(credential.getRevokedReason());
        assertEquals(EdgeKind.MICROPHONE, credential.getEdgeKind());
    }

    @Test
    void theEdgeKindIsReadIgnoringCaseAndSpaces() {
        assertEquals(EdgeKind.MICROPHONE, EdgeKind.parse(" microphone "));
        assertEquals(EdgeKind.ACTUATOR, EdgeKind.parse("ACTUATOR"));
        assertThrows(IllegalArgumentException.class, () -> EdgeKind.parse("MICROPHONE_EDGE"));
        assertThrows(IllegalArgumentException.class, () -> EdgeKind.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> EdgeKind.parse(null));
    }
}
