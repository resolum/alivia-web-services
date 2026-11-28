package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeProvisioningService;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeProvisioningTokenRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.time.LocalDateTime;
import com.resolum.alivia.assets.domain.model.aggregates.EdgeProvisioningToken;
import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EdgeProvisioningServiceTest {
    @Test void tokenCanBeRedeemedOnlyOnceForItsEdge() {
        var edges = mock(EdgeRepository.class);
        var tokens = mock(EdgeProvisioningTokenRepository.class);
        var credentials = mock(EdgeCredentialCommandService.class);
        var edge = mock(Edge.class);
        when(edge.getId()).thenReturn(2L);
        when(edge.getEdgeType()).thenReturn(EdgeType.ACTUATOR_EDGE);
        when(edges.findByIdForUpdate(2L)).thenReturn(Optional.of(edge));
        var service = new EdgeProvisioningService(edges, tokens, credentials,
                Clock.fixed(Instant.parse("2026-11-27T06:00:00Z"), ZoneOffset.UTC));
        var issued = service.issue(2L, 7L);
        assertTrue(issued.token().startsWith("setup_"));
        verify(tokens).save(any());
    }

    @Test void tokenCanBeIssuedByMacAndRedeemedOnce() {
        var edges = mock(EdgeRepository.class);
        var tokens = mock(EdgeProvisioningTokenRepository.class);
        var credentials = mock(EdgeCredentialCommandService.class);
        var edge = mock(Edge.class);
        when(edge.getId()).thenReturn(2L);
        when(edges.findEdgeByMacAddress(any())).thenReturn(Optional.of(edge));
        when(edges.findByIdForUpdate(2L)).thenReturn(Optional.of(edge));
        var expected = new IssuedEdgeCredential(2L, EdgeType.ACTUATOR_EDGE, "secret");
        when(credentials.handle(any(IssueEdgeCredentialCommand.class))).thenReturn(expected);
        var clock = Clock.fixed(Instant.parse("2026-11-27T06:00:00Z"), ZoneOffset.UTC);
        var service = new EdgeProvisioningService(edges, tokens, credentials, clock);

        var issued = service.issueByMac("AA:BB:CC:DD:EE:FF", 7L);
        var grant = new EdgeProvisioningToken(2L, "hash", LocalDateTime.of(2026, 11, 27, 6, 10), 7L);
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(grant));

        assertEquals(expected, service.redeem(issued.token()));
        assertThrows(IllegalArgumentException.class, () -> service.redeem(issued.token()));
        verify(credentials).handle(any(IssueEdgeCredentialCommand.class));
    }

    @Test void invalidAndExpiredTokensAreRejected() {
        var edges = mock(EdgeRepository.class);
        var tokens = mock(EdgeProvisioningTokenRepository.class);
        var credentials = mock(EdgeCredentialCommandService.class);
        var clock = Clock.fixed(Instant.parse("2026-11-27T06:00:00Z"), ZoneOffset.UTC);
        var service = new EdgeProvisioningService(edges, tokens, credentials, clock);

        assertThrows(IllegalArgumentException.class, () -> service.redeem("missing"));
        var expired = new EdgeProvisioningToken(2L, "hash", LocalDateTime.of(2026, 11, 27, 5, 59), 7L);
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(expired));
        assertThrows(IllegalArgumentException.class, () -> service.redeem("expired"));
        verifyNoInteractions(credentials);
    }
}
