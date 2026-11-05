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
    void anIssuedCredentialIsActiveAndKnowsItsEdgeAndKind() {
        var credential = EdgeCredential.issue(7L, EdgeKind.MICROPHONE, "hash-1");

        assertEquals(7L, credential.getEdgeId());
        assertEquals(EdgeKind.MICROPHONE, credential.getEdgeKind());
        assertTrue(credential.isActive());
        assertNull(credential.getLastUsedAt());
        assertTrue(credential.matches("hash-1"));
        assertFalse(credential.matches("hash-2"));
        assertFalse(credential.matches(null));
    }

    @Test
    void rejectsAnInvalidEdgeKindOrHash() {
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(null, EdgeKind.ACTUATOR, "h"));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(0L, EdgeKind.ACTUATOR, "h"));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, null, "h"));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, EdgeKind.ACTUATOR, null));
        assertThrows(IllegalArgumentException.class, () -> EdgeCredential.issue(1L, EdgeKind.ACTUATOR, "  "));
    }

    @Test
    void rotatingReplacesTheSecretAtOnce() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "old");

        credential.rotate(EdgeKind.ACTUATOR, "new");

        assertFalse(credential.matches("old"));
        assertTrue(credential.matches("new"));
    }

    @Test
    void revokingStopsTheCredentialAndIsIdempotent() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash");

        assertTrue(credential.revoke(NOW));
        assertFalse(credential.isActive());
        assertEquals(NOW, credential.getRevokedAt());

        assertFalse(credential.revoke(NOW.plusHours(1)));
        assertEquals(NOW, credential.getRevokedAt());
    }

    @Test
    void rotatingARevokedCredentialMakesItValidAgain() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "old");
        credential.revoke(NOW);

        credential.rotate(EdgeKind.MICROPHONE, "new");

        assertTrue(credential.isActive());
        assertNull(credential.getRevokedAt());
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
