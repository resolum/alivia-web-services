package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.HouseholdDeviceRegistryCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.PlaceEdgeInInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RemoveEdgeFromInstallationCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeSyncStateRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Putting edges in an installation and taking them out: who is locked first, what is stored, who loses the credential. */
class InstallationEdgesCommandServiceTest {

    private static final InstallationId INSTALLATION = new InstallationId(3L);

    private HouseholdDeviceRegistryRepository registries;
    private EdgeRepository edges;
    private ExternalIamService iam;
    private EdgeSyncStateRepository syncStates;
    private HouseholdDeviceRegistryCommandServiceImpl service;
    private HouseholdDeviceRegistry registry;

    @BeforeEach
    void setUp() {
        registries = mock(HouseholdDeviceRegistryRepository.class);
        edges = mock(EdgeRepository.class);
        iam = mock(ExternalIamService.class);
        syncStates = mock(EdgeSyncStateRepository.class);
        service = new HouseholdDeviceRegistryCommandServiceImpl(registries, mock(ExternalBusinessService.class), edges, iam, syncStates);
        registry = HouseholdDeviceRegistry.open(3L, 5L, 1L);
        when(registries.findByInstallationIdForUpdate(INSTALLATION)).thenReturn(Optional.of(registry));
    }

    private Edge storedEdge(long id, String type) {
        var edge = new Edge(1L, String.format("AA:BB:CC:DD:EE:%02X", id), type);
        ReflectionTestUtils.setField(edge, "id", id);
        when(edges.findById(id)).thenReturn(Optional.of(edge));
        return edge;
    }

    @Test
    void placesAnEdgeAndReportsTheEdgesOfTheInstallation() {
        var microphone = storedEdge(1, "MICROPHONE_EDGE");

        var result = service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));

        assertEquals(1L, result.microphoneEdgeId());
        assertNull(result.actuatorEdgeId());
        assertNull(result.replacedEdgeId());
        assertEquals(AssetStatus.IN_USE, microphone.getAssetStatus());
        verify(registries).saveAndFlush(registry);
        verifyNoInteractions(iam);
    }

    @Test
    void theRegistryIsLockedBeforeTheEdgeIsReadAndStoredAfterwards() {
        storedEdge(1, "MICROPHONE_EDGE");

        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));

        var order = inOrder(registries, edges);
        order.verify(registries).findByInstallationIdForUpdate(INSTALLATION);
        order.verify(edges).findById(1L);
        order.verify(edges).save(any(Edge.class));
        order.verify(registries).saveAndFlush(registry);
    }

    @Test
    void theEdgeThatIsReplacedLosesItsCredentialAndGoesBackToTheInventory() {
        var old = storedEdge(1, "ACTUATOR_EDGE");
        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));
        var replacement = storedEdge(2, "ACTUATOR_EDGE");
        clearInvocations(edges);

        var result = service.handle(new PlaceEdgeInInstallationCommand(3L, 2L));

        assertEquals(2L, result.actuatorEdgeId());
        assertEquals(1L, result.replacedEdgeId());
        assertEquals(AssetStatus.AVAILABLE, old.getAssetStatus());
        assertEquals(AssetStatus.IN_USE, replacement.getAssetStatus());
        verify(edges).save(old);
        verify(iam).revokeEdgeCredential(1L, null, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.EDGE_REPLACED_IN_INSTALLATION);
        verify(iam, never()).revokeEdgeCredential(eq(2L), any(), any());
    }

    @Test
    void placingTheEdgeThatIsAlreadyThereChangesNothingAndRevokesNothing() {
        storedEdge(1, "ACTUATOR_EDGE");
        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));

        var result = service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));

        assertEquals(1L, result.actuatorEdgeId());
        assertNull(result.replacedEdgeId());
        verifyNoInteractions(iam);
    }

    @Test
    void failsWhenTheInstallationOrTheEdgeDoNotExist() {
        when(registries.findByInstallationIdForUpdate(new InstallationId(99L))).thenReturn(Optional.empty());
        assertThrows(InstallationNotFound.class, () -> service.handle(new PlaceEdgeInInstallationCommand(99L, 1L)));
        assertThrows(InstallationNotFound.class, () -> service.handle(new RemoveEdgeFromInstallationCommand(99L, 1L)));
        verifyNoInteractions(edges);

        when(edges.findById(7L)).thenReturn(Optional.empty());
        assertThrows(EdgeNotFound.class, () -> service.handle(new PlaceEdgeInInstallationCommand(3L, 7L)));
    }

    @Test
    void anUnavailableEdgeOrAnInactiveInstallationStoreNothing() {
        var taken = storedEdge(1, "ACTUATOR_EDGE");
        taken.changeStatusTo(AssetStatus.IN_USE);
        assertThrows(EdgeNotAvailable.class, () -> service.handle(new PlaceEdgeInInstallationCommand(3L, 1L)));

        ReflectionTestUtils.setField(registry, "status", RegistryStatus.DEACTIVATED);
        storedEdge(2, "MICROPHONE_EDGE");
        assertThrows(InstallationNotActive.class, () -> service.handle(new PlaceEdgeInInstallationCommand(3L, 2L)));

        verify(registries, never()).saveAndFlush(any());
        verifyNoInteractions(iam);
    }

    @Test
    void anEdgeTakenByAnotherInstallationAtTheSameTimeIsReportedAsAlreadyAssigned() {
        storedEdge(1, "ACTUATOR_EDGE");
        when(registries.saveAndFlush(registry)).thenThrow(new DataIntegrityViolationException("uk_household_device_registries_actuator_edge"));

        var failure = assertThrows(EdgeNotAvailable.class, () -> service.handle(new PlaceEdgeInInstallationCommand(3L, 1L)));

        assertTrue(failure.isAlreadyAssigned());
        verifyNoInteractions(iam);
    }

    @Test
    void removingAnEdgeRevokesItsCredentialAndFreesIt() {
        var microphone = storedEdge(1, "MICROPHONE_EDGE");
        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));
        reset(edges, registries, iam);
        when(registries.findByInstallationIdForUpdate(INSTALLATION)).thenReturn(Optional.of(registry));

        var result = service.handle(new RemoveEdgeFromInstallationCommand(3L, 1L));

        assertNull(result.microphoneEdgeId());
        assertEquals(1L, result.replacedEdgeId());
        assertEquals(AssetStatus.AVAILABLE, microphone.getAssetStatus());
        verify(edges).save(microphone);
        verify(registries).saveAndFlush(registry);
        verify(iam).revokeEdgeCredential(1L, null, com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialRevocationReason.EDGE_REMOVED_FROM_INSTALLATION);
    }

    @Test
    void removingAnEdgeThatIsNotInTheInstallationFailsWithoutRevokingAnything() {
        assertThrows(EdgeNotAssigned.class, () -> service.handle(new RemoveEdgeFromInstallationCommand(3L, 9L)));

        verifyNoInteractions(iam);
        verify(registries, never()).saveAndFlush(any());
    }

    // --- what the edges confirmed before is forgotten when they arrive or leave ---

    @Test
    void anEdgeThatArrivesStartsWithoutAnythingConfirmed() {
        storedEdge(1, "MICROPHONE_EDGE");

        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));

        verify(syncStates).deleteByEdgeId(1L);
    }

    @Test
    void placingTheEdgeThatIsAlreadyThereKeepsWhatItConfirmed() {
        storedEdge(1, "ACTUATOR_EDGE");
        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));
        clearInvocations(syncStates);

        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));

        verifyNoInteractions(syncStates);
    }

    @Test
    void theEdgeThatIsReplacedAndTheOneThatArrivesBothStartOver() {
        storedEdge(1, "ACTUATOR_EDGE");
        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));
        storedEdge(2, "ACTUATOR_EDGE");
        clearInvocations(syncStates);

        service.handle(new PlaceEdgeInInstallationCommand(3L, 2L));

        verify(syncStates).deleteByEdgeId(1L);
        verify(syncStates).deleteByEdgeId(2L);
    }

    @Test
    void anEdgeThatLeavesLosesWhatItConfirmed() {
        storedEdge(1, "MICROPHONE_EDGE");
        service.handle(new PlaceEdgeInInstallationCommand(3L, 1L));
        clearInvocations(syncStates);

        service.handle(new RemoveEdgeFromInstallationCommand(3L, 1L));

        verify(syncStates).deleteByEdgeId(1L);
    }

    @Test
    void nothingIsForgottenWhenThePlacementFails() {
        var taken = storedEdge(1, "ACTUATOR_EDGE");
        taken.changeStatusTo(AssetStatus.IN_USE);

        assertThrows(EdgeNotAvailable.class, () -> service.handle(new PlaceEdgeInInstallationCommand(3L, 1L)));
        assertThrows(EdgeNotAssigned.class, () -> service.handle(new RemoveEdgeFromInstallationCommand(3L, 9L)));

        verifyNoInteractions(syncStates);
    }
}
