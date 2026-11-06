package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.commandservices.EdgeCredentialCommandServiceImpl;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import com.resolum.alivia.iam.domain.model.commands.AuthenticateEdgeCommand;
import com.resolum.alivia.iam.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.iam.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AuthenticatedEdge;
import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;
import com.resolum.alivia.iam.domain.repositories.EdgeCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EdgeCredentialCommandServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    private final EdgeCredentialRepository repository = mock(EdgeCredentialRepository.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final EdgeCredentialCommandServiceImpl service =
            new EdgeCredentialCommandServiceImpl(repository, tokenService, CLOCK);

    @BeforeEach
    void setUp() {
        when(tokenService.generateEdgeSecret()).thenReturn("the-secret");
        when(tokenService.hashEdgeSecret("the-secret")).thenReturn("hash-of-secret");
        when(tokenService.hashEdgeSecret("wrong")).thenReturn("hash-of-wrong");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // --- issuing ---

    @Test
    void issuesTheFirstCredentialOfAnEdgeAndStoresOnlyTheHash() {
        when(repository.findByEdgeIdForUpdate(7L)).thenReturn(Optional.empty());

        var issued = service.handle(new IssueEdgeCredentialCommand(7L, EdgeKind.MICROPHONE, 3L));

        assertEquals("7.the-secret", issued.credential());
        assertEquals(EdgeKind.MICROPHONE, issued.edgeKind());
        var stored = ArgumentCaptor.forClass(EdgeCredential.class);
        verify(repository).save(stored.capture());
        assertTrue(stored.getValue().matches("hash-of-secret"));
        assertEquals(7L, stored.getValue().getEdgeId());
        assertEquals(3L, stored.getValue().getIssuedBy());
        assertEquals(NOW, stored.getValue().getIssuedAt());
    }

    @Test
    void issuingAgainReplacesTheSecretOfTheSameCredential() {
        var existing = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "old-hash", 3L, NOW.minusDays(400));
        when(repository.findByEdgeIdForUpdate(7L)).thenReturn(Optional.of(existing));

        service.handle(new IssueEdgeCredentialCommand(7L, EdgeKind.ACTUATOR, 4L));

        verify(repository).save(existing);
        assertFalse(existing.matches("old-hash"));
        assertTrue(existing.matches("hash-of-secret"));
        assertEquals(4L, existing.getIssuedBy());
        assertEquals(NOW, existing.getIssuedAt()); // the age starts again
    }

    @Test
    void theIssuedCredentialNeverShowsItsSecretWhenPrinted() {
        when(repository.findByEdgeIdForUpdate(7L)).thenReturn(Optional.empty());

        var issued = service.handle(new IssueEdgeCredentialCommand(7L, EdgeKind.ACTUATOR, 4L));

        assertFalse(issued.toString().contains("the-secret"));
        assertFalse(new AuthenticateEdgeCommand("7.the-secret").toString().contains("the-secret"));
    }

    // --- revoking ---

    @Test
    void revokesAnActiveCredential() {
        var existing = EdgeCredential.issue(7L, EdgeKind.ACTUATOR, "hash-of-secret", 3L, NOW.minusDays(1));
        when(repository.findByEdgeIdForUpdate(7L)).thenReturn(Optional.of(existing));

        service.handle(new RevokeEdgeCredentialCommand(7L, 9L, "REVOKED_BY_ADMINISTRATOR"));

        assertFalse(existing.isActive());
        assertEquals(NOW, existing.getRevokedAt());
        assertEquals(9L, existing.getRevokedBy());
        assertEquals("REVOKED_BY_ADMINISTRATOR", existing.getRevokedReason());
        verify(repository).save(existing);
    }

    @Test
    void revokingAnEdgeWithoutCredentialOrAlreadyRevokedChangesNothing() {
        when(repository.findByEdgeIdForUpdate(7L)).thenReturn(Optional.empty());
        service.handle(new RevokeEdgeCredentialCommand(7L, null, "EDGE_RETIRED"));

        var revoked = EdgeCredential.issue(8L, EdgeKind.ACTUATOR, "h", 3L, NOW.minusDays(2));
        revoked.revoke(NOW.minusDays(1), null, "EDGE_RETIRED");
        when(repository.findByEdgeIdForUpdate(8L)).thenReturn(Optional.of(revoked));
        service.handle(new RevokeEdgeCredentialCommand(8L, 9L, "REVOKED_BY_ADMINISTRATOR"));

        verify(repository, never()).save(any());
        assertEquals(NOW.minusDays(1), revoked.getRevokedAt());
    }

    // --- authenticating ---

    @Test
    void identifiesTheEdgeThatOwnsAValidCredentialAndRecordsTheUse() {
        when(repository.findByEdgeId(7L)).thenReturn(Optional.of(EdgeCredential.issue(7L, EdgeKind.MICROPHONE, "hash-of-secret", 3L, NOW.minusDays(1))));

        var edge = service.handle(new AuthenticateEdgeCommand("7.the-secret"));

        assertEquals(Optional.of(new AuthenticatedEdge(7L, EdgeKind.MICROPHONE)), edge);
        verify(repository).recordUse(7L, NOW, NOW.minusMinutes(1));
    }

    @Test
    void aWrongSecretOrARevokedOrUnknownEdgeAuthenticatesNobodyAndRecordsNothing() {
        when(repository.findByEdgeId(7L)).thenReturn(Optional.of(EdgeCredential.issue(7L, EdgeKind.MICROPHONE, "hash-of-secret", 3L, NOW.minusDays(1))));
        var revoked = EdgeCredential.issue(8L, EdgeKind.ACTUATOR, "hash-of-secret", 3L, NOW.minusDays(1));
        revoked.revoke(NOW, null, "EDGE_RETIRED");
        when(repository.findByEdgeId(8L)).thenReturn(Optional.of(revoked));
        when(repository.findByEdgeId(9L)).thenReturn(Optional.empty());

        assertTrue(service.handle(new AuthenticateEdgeCommand("7.wrong")).isEmpty());
        assertTrue(service.handle(new AuthenticateEdgeCommand("8.the-secret")).isEmpty());
        assertTrue(service.handle(new AuthenticateEdgeCommand("9.the-secret")).isEmpty());

        verify(repository, never()).recordUse(anyLong(), any(), any());
    }

    @Test
    void theSecretOfOneEdgeDoesNotOpenAnotherEdge() {
        when(repository.findByEdgeId(7L)).thenReturn(Optional.of(EdgeCredential.issue(7L, EdgeKind.MICROPHONE, "hash-of-secret", 3L, NOW.minusDays(1))));
        when(repository.findByEdgeId(8L)).thenReturn(Optional.of(EdgeCredential.issue(8L, EdgeKind.ACTUATOR, "another-hash", 3L, NOW.minusDays(1))));

        assertTrue(service.handle(new AuthenticateEdgeCommand("8.the-secret")).isEmpty());
        assertTrue(service.handle(new AuthenticateEdgeCommand("7.the-secret")).isPresent());
    }

    @Test
    void malformedCredentialsAreRejectedWithoutTouchingTheDatabase() {
        for (var presented : new String[]{null, "", "   ", "no-separator", ".the-secret", "7.", "abc.the-secret",
                "0.the-secret", "-3.the-secret", "7 .the-secret", "9999999999999999999999.the-secret",
                "eyJhbGciOi.eyJzdWIi.signature", "7." + "x".repeat(300)}) {
            assertTrue(service.handle(new AuthenticateEdgeCommand(presented)).isEmpty(), "accepted: " + presented);
        }

        verifyNoInteractions(repository);
    }
}
