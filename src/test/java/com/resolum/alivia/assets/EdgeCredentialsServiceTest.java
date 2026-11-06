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
        when(iam.issueEdgeCredential(7L, EdgeType.MICROPHONE_EDGE)).thenReturn("7.the-secret");

        var issued = service.handle(new IssueEdgeCredentialCommand(7L));

        assertEquals(7L, issued.edgeId());
        assertEquals(EdgeType.MICROPHONE_EDGE, issued.edgeType());
        assertEquals("7.the-secret", issued.credential());
        assertFalse(issued.toString().contains("the-secret"));
    }

    @Test
    void theEdgeIsReadUnderLockBeforeIamStoresTheCredential() {
        storedEdge(7, "ACTUATOR_EDGE");

        service.handle(new IssueEdgeCredentialCommand(7L));

        var order = inOrder(edges, iam);
        order.verify(edges).findByIdForUpdate(7L);
        order.verify(iam).issueEdgeCredential(7L, EdgeType.ACTUATOR_EDGE);
    }

    @Test
    void anEdgeInAnyStatusButRetiredMayReceiveACredential() {
        for (var status : new AssetStatus[]{AssetStatus.AVAILABLE, AssetStatus.IN_USE, AssetStatus.MAINTENANCE, AssetStatus.BROKEN}) {
            var edge = storedEdge(7, "ACTUATOR_EDGE");
            ReflectionTestUtils.setField(edge, "assetStatus", status);

            assertDoesNotThrow(() -> service.handle(new IssueEdgeCredentialCommand(7L)), status.name());
        }
    }

    @Test
    void aRetiredEdgeCannotReceiveACredential() {
        var edge = storedEdge(7, "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(edge, "assetStatus", AssetStatus.RETIRED);

        assertThrows(EdgeRetired.class, () -> service.handle(new IssueEdgeCredentialCommand(7L)));

        verifyNoInteractions(iam);
    }

    @Test
    void aMissingEdgeHasNoCredentialToIssueOrRevoke() {
        when(edges.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        when(edges.existsById(9L)).thenReturn(false);

        assertThrows(EdgeNotFound.class, () -> service.handle(new IssueEdgeCredentialCommand(9L)));
        assertThrows(EdgeNotFound.class, () -> service.handle(new RevokeEdgeCredentialCommand(9L)));

        verifyNoInteractions(iam);
    }

    @Test
    void revokesTheCredentialOfAnExistingEdge() {
        when(edges.existsById(7L)).thenReturn(true);

        service.handle(new RevokeEdgeCredentialCommand(7L));

        verify(iam).revokeEdgeCredential(7L);
    }

    @Test
    void theCommandsRejectInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new IssueEdgeCredentialCommand(null));
        assertThrows(IllegalArgumentException.class, () -> new IssueEdgeCredentialCommand(0L));
        assertThrows(IllegalArgumentException.class, () -> new RevokeEdgeCredentialCommand(-1L));
    }

    // --- the adapter towards IAM ---

    @Test
    void theAdapterNamesTheKindOfEachTypeOfEdgeForIam() {
        var facade = mock(IamContextFacade.class);
        var adapter = new ExternalIamService(facade, mock(EdgeTableWaitService.class));
        when(facade.issueEdgeCredential(any(), any())).thenReturn("1.secret");

        assertEquals("1.secret", adapter.issueEdgeCredential(1L, EdgeType.MICROPHONE_EDGE));
        adapter.issueEdgeCredential(2L, EdgeType.ACTUATOR_EDGE);
        adapter.revokeEdgeCredential(2L);

        verify(facade).issueEdgeCredential(1L, "MICROPHONE");
        verify(facade).issueEdgeCredential(2L, "ACTUATOR");
        verify(facade).revokeEdgeCredential(2L);
    }

    // --- the wait of an edge ends when its credential changes ---

    @Test
    void revokingOrReplacingTheCredentialEndsTheWaitThatBeganWithTheOldOne() {
        var facade = mock(IamContextFacade.class);
        var waits = mock(EdgeTableWaitService.class);
        var adapter = new ExternalIamService(facade, waits);
        when(facade.issueEdgeCredential(any(), any())).thenReturn("1.secret");

        adapter.revokeEdgeCredential(7L);
        adapter.issueEdgeCredential(8L, EdgeType.ACTUATOR_EDGE);

        var order = inOrder(facade, waits);
        order.verify(facade).revokeEdgeCredential(7L);
        order.verify(waits).endWaitOf(7L);
        order.verify(facade).issueEdgeCredential(8L, "ACTUATOR");
        order.verify(waits).endWaitOf(8L);
    }

    @Test
    void ifIamFailsTheCredentialIsUnchangedSoTheWaitIsNotEnded() {
        var facade = mock(IamContextFacade.class);
        var waits = mock(EdgeTableWaitService.class);
        var adapter = new ExternalIamService(facade, waits);
        doThrow(new IllegalStateException("iam down")).when(facade).revokeEdgeCredential(7L);
        when(facade.issueEdgeCredential(any(), any())).thenThrow(new IllegalStateException("iam down"));

        assertThrows(IllegalStateException.class, () -> adapter.revokeEdgeCredential(7L));
        assertThrows(IllegalStateException.class, () -> adapter.issueEdgeCredential(8L, EdgeType.ACTUATOR_EDGE));

        verifyNoInteractions(waits);
    }
}
