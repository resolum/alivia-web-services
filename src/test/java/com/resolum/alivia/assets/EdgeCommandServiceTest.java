package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeStillAssigned;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EdgeCommandServiceTest {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    private EdgeRepository edges;
    private DeviceRepository devices;
    private HouseholdDeviceRegistryRepository registries;
    private ExternalIamService iam;
    private ExternalBusinessService businesses;
    private EdgeCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        edges = mock(EdgeRepository.class);
        devices = mock(DeviceRepository.class);
        registries = mock(HouseholdDeviceRegistryRepository.class);
        iam = mock(ExternalIamService.class);
        businesses = mock(ExternalBusinessService.class);
        when(businesses.existsBusiness(1L)).thenReturn(true);
        service = new EdgeCommandServiceImpl(edges, devices, businesses, registries, iam);
        when(edges.saveAndFlush(any(Edge.class))).thenAnswer(invocation -> {
            var edge = invocation.<Edge>getArgument(0);
            ReflectionTestUtils.setField(edge, "id", 3L);
            return edge;
        });
    }

    private static Edge storedEdge() {
        var edge = new Edge(1L, MAC, "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(edge, "id", 3L);
        return edge;
    }

    @Test
    void registersAnEdgeAndReturnsItsId() {
        assertEquals(3L, service.handle(new RegisterEdgeCommand(1L, "aa-bb-cc-dd-ee-ff", "MICROPHONE_EDGE")));

        verify(edges).saveAndFlush(any(Edge.class));
    }

    @Test
    void rejectsAMacAddressUsedByAnotherEdgeOrByADevice() {
        when(edges.existsEdgeByMacAddress(new MacAddress(MAC))).thenReturn(true);
        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE")));

        when(edges.existsEdgeByMacAddress(new MacAddress(MAC))).thenReturn(false);
        when(devices.existsDeviceByMacAddress(new MacAddress(MAC))).thenReturn(true);
        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE")));

        verify(edges, never()).saveAndFlush(any());
    }

    @Test
    void translatesAConcurrentDuplicateAndDoesNotReturnAnEmptyId() {
        when(edges.saveAndFlush(any(Edge.class))).thenThrow(new DataIntegrityViolationException("uk_edges_mac_address"));

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE")));
    }

    @Test
    void doesNotHideOtherPersistenceFailures() {
        when(edges.saveAndFlush(any(Edge.class))).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class,
                () -> service.handle(new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE")));
    }

    @Test
    void updatesTheTypeKeepingTheSameMacWithoutCheckingDuplicates() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));

        assertEquals(3L, service.handle(new UpdateEdgeCommand(3L, "aa-bb-cc-dd-ee-ff", "microphone_edge")));

        assertEquals(EdgeType.MICROPHONE_EDGE, edge.getEdgeType());
        verify(edges, never()).existsEdgeByMacAddress(any());
        verify(edges).saveAndFlush(edge);
    }

    @Test
    void rejectsUpdatingToAMacAddressOfAnotherAsset() {
        when(edges.findById(3L)).thenReturn(Optional.of(storedEdge()));
        when(devices.existsDeviceByMacAddress(new MacAddress("AA:BB:CC:DD:EE:00"))).thenReturn(true);

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new UpdateEdgeCommand(3L, "aa:bb:cc:dd:ee:00", "ACTUATOR_EDGE")));

        verify(edges, never()).saveAndFlush(any());
    }

    @Test
    void failsToUpdateAMissingEdge() {
        when(edges.findById(9L)).thenReturn(Optional.empty());

        assertThrows(EdgeNotFound.class, () -> service.handle(new UpdateEdgeCommand(9L, MAC, "ACTUATOR_EDGE")));
    }

    @Test
    void changesStatusAlongAValidTransition() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));

        service.handle(new ChangeEdgeStatusCommand(3L, "MAINTENANCE"));

        assertEquals(AssetStatus.MAINTENANCE, edge.getAssetStatus());
    }

    // --- placement in an installation, and the credential ---

    private static HouseholdDeviceRegistry registry() {
        return HouseholdDeviceRegistry.open(3L, 5L, 1L);
    }

    @Test
    void anEdgeThatChangesItsTypeLosesItsCredential() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));

        service.handle(new UpdateEdgeCommand(3L, MAC, "MICROPHONE_EDGE"));

        verify(iam).revokeEdgeCredential(3L, null, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.EDGE_TYPE_CHANGED);
    }

    @Test
    void keepingTheTypeKeepsTheCredential() {
        when(edges.findById(3L)).thenReturn(Optional.of(storedEdge()));

        service.handle(new UpdateEdgeCommand(3L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE"));

        verify(iam, never()).revokeEdgeCredential(any(), any(), any());
    }

    @Test
    void theTypeOfAnEdgeThatIsPlacedInAnInstallationCannotChange() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));
        when(registries.findByAssignedEdgeIdForUpdate(3L)).thenReturn(Optional.of(registry()));

        assertThrows(EdgeStillAssigned.class, () -> service.handle(new UpdateEdgeCommand(3L, MAC, "MICROPHONE_EDGE")));

        verify(edges, never()).saveAndFlush(any());
        verify(iam, never()).revokeEdgeCredential(any(), any(), any());
        assertEquals(EdgeType.ACTUATOR_EDGE, edge.getEdgeType());
    }

    @Test
    void itsMacAddressCanStillChangeWhilePlaced() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));
        when(registries.findByAssignedEdgeIdForUpdate(3L)).thenReturn(Optional.of(registry()));

        service.handle(new UpdateEdgeCommand(3L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE"));

        verify(edges).saveAndFlush(edge);
    }

    @Test
    void theRegistryThatHoldsTheEdgeIsLockedBeforeTheEdgeIsRead() {
        when(edges.findById(3L)).thenReturn(Optional.of(storedEdge()));

        service.handle(new UpdateEdgeCommand(3L, MAC, "ACTUATOR_EDGE"));
        service.handle(new ChangeEdgeStatusCommand(3L, "MAINTENANCE"));

        var order = inOrder(registries, edges);
        order.verify(registries).findByAssignedEdgeIdForUpdate(3L);
        order.verify(edges).findById(3L);
        order.verify(edges).saveAndFlush(any(Edge.class));
        order.verify(registries).findByAssignedEdgeIdForUpdate(3L);
        order.verify(edges).findById(3L);
    }

    @Test
    void retiringAnEdgeRevokesItsCredential() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));

        service.handle(new ChangeEdgeStatusCommand(3L, "RETIRED"));

        assertEquals(AssetStatus.RETIRED, edge.getAssetStatus());
        verify(iam).revokeEdgeCredential(3L, null, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.EDGE_RETIRED);
    }

    @Test
    void otherStatusChangesKeepTheCredential() {
        when(edges.findById(3L)).thenReturn(Optional.of(storedEdge()));

        service.handle(new ChangeEdgeStatusCommand(3L, "BROKEN"));

        verify(iam, never()).revokeEdgeCredential(any(), any(), any());
    }

    @Test
    void anEdgePlacedInAnInstallationCannotBecomeAvailableOrRetiredUntilItIsRemoved() {
        for (var target : new String[]{"AVAILABLE", "RETIRED"}) {
            var edge = storedEdge();
            // MAINTENANCE can go to both AVAILABLE and RETIRED.
            edge.changeStatusTo(AssetStatus.MAINTENANCE);
            when(edges.findById(3L)).thenReturn(Optional.of(edge));
            when(registries.findByAssignedEdgeIdForUpdate(3L)).thenReturn(Optional.of(registry()));

            assertThrows(EdgeStillAssigned.class, () -> service.handle(new ChangeEdgeStatusCommand(3L, target)), target);
        }

        verify(edges, never()).saveAndFlush(any());
        verify(iam, never()).revokeEdgeCredential(any(), any(), any());
    }

    @Test
    void aPlacedEdgeMayStillGoToMaintenanceOrBroken() {
        var edge = storedEdge();
        edge.changeStatusTo(AssetStatus.IN_USE);
        when(edges.findById(3L)).thenReturn(Optional.of(edge));
        when(registries.findByAssignedEdgeIdForUpdate(3L)).thenReturn(Optional.of(registry()));

        service.handle(new ChangeEdgeStatusCommand(3L, "BROKEN"));

        assertEquals(AssetStatus.BROKEN, edge.getAssetStatus());
        verify(edges).saveAndFlush(edge);
    }

    // --- registering in the inventory ---

    @Test
    void aRegisteredEdgeIsOnlyInTheInventory() {
        var stored = ArgumentCaptor.forClass(Edge.class);

        service.handle(new RegisterEdgeCommand(1L, MAC, "MICROPHONE_EDGE"));

        verify(edges).saveAndFlush(stored.capture());
        assertEquals(AssetStatus.AVAILABLE, stored.getValue().getAssetStatus());
        assertEquals(EdgeType.MICROPHONE_EDGE, stored.getValue().getEdgeType());
        // It belongs to no installation and has no credential until an installation work places it.
        verifyNoInteractions(registries, iam);
    }

    @Test
    void anUnknownBusinessCannotOwnAnEdge() {
        when(businesses.existsBusiness(2L)).thenReturn(false);

        assertThrows(BusinessNotFound.class, () -> service.handle(new RegisterEdgeCommand(2L, MAC, "ACTUATOR_EDGE")));

        verify(edges, never()).saveAndFlush(any());
    }

    @Test
    void anInvalidTypeOrMacIsRejectedBeforeAnythingIsLookedUp() {
        assertThrows(IllegalArgumentException.class, () -> service.handle(new RegisterEdgeCommand(1L, MAC, "CAMERA")));
        assertThrows(RuntimeException.class, () -> service.handle(new RegisterEdgeCommand(1L, "not-a-mac", "ACTUATOR_EDGE")));

        verifyNoInteractions(businesses);
        verify(edges, never()).saveAndFlush(any());
    }

    // --- status: IN_USE only by placing ---

    @Test
    void anEdgeCannotBeMovedToInUseByChangingItsStatus() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));

        assertThrows(IllegalArgumentException.class, () -> service.handle(new ChangeEdgeStatusCommand(3L, "in_use")));

        assertEquals(AssetStatus.AVAILABLE, edge.getAssetStatus());
        verify(edges, never()).saveAndFlush(any());
    }

    @Test
    void anUnknownOrBlankStatusIsRejected() {
        when(edges.findById(3L)).thenReturn(Optional.of(storedEdge()));

        assertThrows(IllegalArgumentException.class, () -> service.handle(new ChangeEdgeStatusCommand(3L, "SLEEPING")));
        assertThrows(IllegalArgumentException.class, () -> service.handle(new ChangeEdgeStatusCommand(3L, " ")));

        verify(edges, never()).saveAndFlush(any());
    }

    @Test
    void aRetiredEdgeCannotChangeStatusAnymore() {
        var edge = storedEdge();
        edge.changeStatusTo(AssetStatus.RETIRED);
        when(edges.findById(3L)).thenReturn(Optional.of(edge));

        assertThrows(com.resolum.alivia.assets.domain.exceptions.InvalidAssetStatusTransition.class,
                () -> service.handle(new ChangeEdgeStatusCommand(3L, "AVAILABLE")));
    }
}
