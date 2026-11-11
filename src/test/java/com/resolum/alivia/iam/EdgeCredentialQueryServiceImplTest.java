package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.queryservices.EdgeCredentialQueryServiceImpl;
import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import com.resolum.alivia.iam.domain.model.queries.GetEdgeCredentialByEdgeIdQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;
import com.resolum.alivia.iam.domain.repositories.EdgeCredentialRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EdgeCredentialQueryServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    private final EdgeCredentialRepository repository = mock(EdgeCredentialRepository.class);
    private final EdgeCredentialQueryServiceImpl service =
            new EdgeCredentialQueryServiceImpl(repository, CLOCK, Duration.ofDays(365));

    private void stored(long edgeId, LocalDateTime issuedAt) {
        when(repository.findByEdgeId(edgeId)).thenReturn(
                Optional.of(EdgeCredential.issue(edgeId, EdgeKind.MICROPHONE, "hash", 3L, issuedAt)));
    }

    @Test
    void aFreshCredentialIsDescribedWithoutARotationRecommendation() {
        stored(7L, NOW.minusDays(10).minusHours(5));

        var view = service.handle(new GetEdgeCredentialByEdgeIdQuery(7L)).orElseThrow();

        assertEquals(7L, view.edgeId());
        assertEquals(EdgeKind.MICROPHONE, view.edgeKind());
        assertTrue(view.active());
        assertEquals(3L, view.issuedBy());
        assertEquals(10, view.ageDays());
        assertFalse(view.rotationRecommended());
        assertNull(view.revokedAt());
        assertNull(view.revokedReason());
    }

    @Test
    void anActiveCredentialPastTheConfiguredAgeIsRecommendedForRotation() {
        stored(7L, NOW.minusDays(365)); // exactly the age: not yet
        assertFalse(service.handle(new GetEdgeCredentialByEdgeIdQuery(7L)).orElseThrow().rotationRecommended());

        stored(8L, NOW.minusDays(365).minusSeconds(1));
        var old = service.handle(new GetEdgeCredentialByEdgeIdQuery(8L)).orElseThrow();
        assertTrue(old.rotationRecommended());
        assertEquals(365, old.ageDays());
    }

    @Test
    void aRevokedCredentialShowsWhoAndWhyAndIsNeverRecommendedForRotation() {
        var credential = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash", 3L, NOW.minusDays(500));
        credential.revoke(NOW.minusDays(2), 9L, "REVOKED_BY_ADMINISTRATOR");
        when(repository.findByEdgeId(7L)).thenReturn(Optional.of(credential));

        var view = service.handle(new GetEdgeCredentialByEdgeIdQuery(7L)).orElseThrow();

        assertFalse(view.active());
        assertFalse(view.rotationRecommended());
        assertEquals(NOW.minusDays(2), view.revokedAt());
        assertEquals(9L, view.revokedBy());
        assertEquals("REVOKED_BY_ADMINISTRATOR", view.revokedReason());
    }

    @Test
    void anEdgeWithoutCredentialIsEmptyAndABadIdIsRejected() {
        when(repository.findByEdgeId(7L)).thenReturn(Optional.empty());

        assertTrue(service.handle(new GetEdgeCredentialByEdgeIdQuery(7L)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new GetEdgeCredentialByEdgeIdQuery(0L));
        assertThrows(IllegalArgumentException.class, () -> new GetEdgeCredentialByEdgeIdQuery(null));
    }

    @Test
    void aCredentialIssuedInTheFutureHasAgeZero() {
        stored(7L, NOW.plusHours(3)); // clocks of two instances may differ a little

        var view = service.handle(new GetEdgeCredentialByEdgeIdQuery(7L)).orElseThrow();

        assertEquals(0, view.ageDays());
        assertFalse(view.rotationRecommended());
    }
}
