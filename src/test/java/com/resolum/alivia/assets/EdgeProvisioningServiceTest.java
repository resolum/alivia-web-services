package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeProvisioningService;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeProvisioningTokenRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.domain.services.EdgeCredentialCommandService;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
}
