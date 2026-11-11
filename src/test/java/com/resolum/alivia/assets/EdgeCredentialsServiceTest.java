package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeCredentialCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeRetired;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.services.EdgeTableWaitService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Which edge may have a credential, and how Assets asks IAM for it. */
class EdgeCredentialsServiceTest {

    private EdgeRepository edges;
    private ExternalIamService iam;
    private EdgeCredentialCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        edges = mock(EdgeRepository.class);
        iam = mock(ExternalIamService.class);
        service = new EdgeCredentialCommandServiceImpl(edges, iam);
    }

    private Edge storedEdge(long id, String type) {
        var edge = new Edge(1L, String.format("AA:BB:CC:DD:EE:%02X", id), type);
        ReflectionTestUtils.setField(edge, "id", id);
        when(edges.findByIdForUpdate(id)).thenReturn(Optional.of(edge));
        return edge;
    }

    @Test
    void issuesTheCredentialOfAnEdgeOfItsTypeAndReturnsTheSecretOnce() {
        storedEdge(7, "MICROPHONE_EDGE");
        when(iam.issueEdgeCredential(7L, EdgeType.MICROPHONE_EDGE, 3L)).thenReturn("7.the-secret");

        var issued = service.handle(new IssueEdgeCredentialCommand(7L, 3L));

        assertEquals(7L, issued.edgeId());
        assertEquals(EdgeType.MICROPHONE_EDGE, issued.edgeType());
        assertEquals("7.the-secret", issued.credential());
        assertFalse(issued.toString().contains("the-secret"));
    }

    @Test
    void theEdgeIsReadUnderLockBeforeIamStoresTheCredential() {
        storedEdge(7, "ACTUATOR_EDGE");

        service.handle(new IssueEdgeCredentialCommand(7L, 3L));

        var order = inOrder(edges, iam);
        order.verify(edges).findByIdForUpdate(7L);
        order.verify(iam).issueEdgeCredential(7L, EdgeType.ACTUATOR_EDGE, 3L);
    }

    @Test
    void anEdgeInAnyStatusButRetiredMayReceiveACredential() {
        for (var status : new AssetStatus[]{AssetStatus.AVAILABLE, AssetStatus.IN_USE, AssetStatus.MAINTENANCE, AssetStatus.BROKEN}) {
            var edge = storedEdge(7, "ACTUATOR_EDGE");
            ReflectionTestUtils.setField(edge, "assetStatus", status);

            assertDoesNotThrow(() -> service.handle(new IssueEdgeCredentialCommand(7L, 3L)), status.name());
        }
    }

    @Test
    void aRetiredEdgeCannotReceiveACredential() {
        var edge = storedEdge(7, "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(edge, "assetStatus", AssetStatus.RETIRED);

        assertThrows(EdgeRetired.class, () -> service.handle(new IssueEdgeCredentialCommand(7L, 3L)));

        verifyNoInteractions(iam);
    }

    @Test
    void aMissingEdgeHasNoCredentialToIssueOrRevoke() {
        when(edges.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        when(edges.existsById(9L)).thenReturn(false);

        assertThrows(EdgeNotFound.class, () -> service.handle(new IssueEdgeCredentialCommand(9L, 3L)));
        assertThrows(EdgeNotFound.class, () -> service.handle(new RevokeEdgeCredentialCommand(9L, 3L)));

        verifyNoInteractions(iam);
    }

    @Test
    void revokesTheCredentialOfAnExistingEdge() {
        when(edges.existsById(7L)).thenReturn(true);

        service.handle(new RevokeEdgeCredentialCommand(7L, 3L));

        verify(iam).revokeEdgeCredential(7L, 3L, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.REVOKED_BY_ADMINISTRATOR);
    }

    @Test
    void theCommandsRejectInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new IssueEdgeCredentialCommand(null, 3L));
        assertThrows(IllegalArgumentException.class, () -> new IssueEdgeCredentialCommand(0L, 3L));
        assertThrows(IllegalArgumentException.class, () -> new RevokeEdgeCredentialCommand(-1L, 3L));
        // An administrator is always behind these two (the changes of the edge itself go through the adapter).
        assertThrows(IllegalArgumentException.class, () -> new IssueEdgeCredentialCommand(7L, null));
        assertThrows(IllegalArgumentException.class, () -> new IssueEdgeCredentialCommand(7L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new RevokeEdgeCredentialCommand(7L, null));
        assertThrows(IllegalArgumentException.class, () -> new RevokeEdgeCredentialCommand(7L, -1L));
    }

    // --- the adapter towards IAM ---

    @Test
    void theAdapterNamesTheKindOfEachTypeOfEdgeForIam() {
        var facade = mock(IamContextFacade.class);
        var adapter = new ExternalIamService(facade, mock(EdgeTableWaitService.class));
        when(facade.issueEdgeCredential(any(), any(), any())).thenReturn("1.secret");

        assertEquals("1.secret", adapter.issueEdgeCredential(1L, EdgeType.MICROPHONE_EDGE, 3L));
        adapter.issueEdgeCredential(2L, EdgeType.ACTUATOR_EDGE, 3L);
        adapter.revokeEdgeCredential(2L, 9L, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.REVOKED_BY_ADMINISTRATOR);
        adapter.revokeEdgeCredential(4L, null, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.EDGE_RETIRED);

        verify(facade).issueEdgeCredential(1L, "MICROPHONE", 3L);
        verify(facade).issueEdgeCredential(2L, "ACTUATOR", 3L);
        verify(facade).revokeEdgeCredential(2L, 9L, "REVOKED_BY_ADMINISTRATOR");
        verify(facade).revokeEdgeCredential(4L, null, "EDGE_RETIRED");
    }

    @Test
    void theAdapterReadsTheStateOfACredentialThroughTheFacade() {
        var facade = mock(IamContextFacade.class);
        var adapter = new ExternalIamService(facade, mock(EdgeTableWaitService.class));
        var summary = new IamContextFacade.EdgeCredentialSummary(7L, true, java.time.LocalDateTime.of(2026, 1, 1, 8, 0), 3L, 308,
                false, null, null, null, null);
        when(facade.getEdgeCredentialSummary(7L)).thenReturn(Optional.of(summary));
        when(facade.getEdgeCredentialSummary(8L)).thenReturn(Optional.empty());

        assertEquals(Optional.of(summary), adapter.getEdgeCredentialSummary(7L));
        assertTrue(adapter.getEdgeCredentialSummary(8L).isEmpty());
    }

    // --- the wait of an edge ends when its credential changes ---

    @Test
    void revokingOrReplacingTheCredentialEndsTheWaitThatBeganWithTheOldOne() {
        var facade = mock(IamContextFacade.class);
        var waits = mock(EdgeTableWaitService.class);
        var adapter = new ExternalIamService(facade, waits);
        when(facade.issueEdgeCredential(any(), any(), any())).thenReturn("1.secret");

        adapter.revokeEdgeCredential(7L, null, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.EDGE_REMOVED_FROM_INSTALLATION);
        adapter.issueEdgeCredential(8L, EdgeType.ACTUATOR_EDGE, 3L);

        var order = inOrder(facade, waits);
        order.verify(facade).revokeEdgeCredential(7L, null, "EDGE_REMOVED_FROM_INSTALLATION");
        order.verify(waits).endWaitOf(7L);
        order.verify(facade).issueEdgeCredential(8L, "ACTUATOR", 3L);
        order.verify(waits).endWaitOf(8L);
    }

    @Test
    void ifIamFailsTheCredentialIsUnchangedSoTheWaitIsNotEnded() {
        var facade = mock(IamContextFacade.class);
        var waits = mock(EdgeTableWaitService.class);
        var adapter = new ExternalIamService(facade, waits);
        doThrow(new IllegalStateException("iam down")).when(facade).revokeEdgeCredential(7L, null, "EDGE_RETIRED");
        when(facade.issueEdgeCredential(any(), any(), any())).thenThrow(new IllegalStateException("iam down"));

        assertThrows(IllegalStateException.class, () -> adapter.revokeEdgeCredential(7L, null, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.EDGE_RETIRED));
        assertThrows(IllegalStateException.class, () -> adapter.issueEdgeCredential(8L, EdgeType.ACTUATOR_EDGE, 3L));

        verifyNoInteractions(waits);
    }
}
