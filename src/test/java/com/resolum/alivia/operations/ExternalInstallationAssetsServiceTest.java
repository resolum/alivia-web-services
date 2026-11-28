package com.resolum.alivia.operations;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade.InventoryAsset;
import com.resolum.alivia.assistance.interfaces.acl.AssistanceContextFacade;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalInstallationAssetsService;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.domain.exceptions.InstallationAssetNotFound;
import com.resolum.alivia.operations.domain.exceptions.InstallationAssetTypeMismatch;
import com.resolum.alivia.operations.domain.exceptions.InstallationContextMissing;
import com.resolum.alivia.operations.domain.exceptions.RequiredInstallationEdgeMissing;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDeviceType;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalInstallationAssetsServiceTest {
    private final AssetsContextFacade assets = mock(AssetsContextFacade.class);
    private final AssistanceContextFacade assistance = mock(AssistanceContextFacade.class);
    private final ExternalSubscriptionsService subscriptions = mock(ExternalSubscriptionsService.class);
    private final ExternalInstallationAssetsService service = new ExternalInstallationAssetsService(assets, assistance, subscriptions);
    private final Appointment order = mock(Appointment.class);

    private final InstalledDevice door = new InstalledDevice("Puerta principal", "8E:94:DF:4C:E5:2D", InstalledDeviceType.DOOR);
    private final InstalledDevice edge = new InstalledDevice("Edge actuadores", "36:DC:23:1C:73:C0", InstalledDeviceType.ACTUATOR_EDGE);

    ExternalInstallationAssetsServiceTest() {
        when(order.getId()).thenReturn(44L);
        when(order.getDraftId()).thenReturn("draft");
        when(order.getTechnicianId()).thenReturn(7L);
        when(subscriptions.fetchInstallationContext("draft"))
                .thenReturn(Optional.of(new SubscriptionsContextFacade.InstallationContext(3L, 1L)));
        when(assistance.fetchHouseholdIdOfAssistedPerson(3L)).thenReturn(Optional.of(9L));
        when(assets.ensureInstallation(44L, 9L, 1L)).thenReturn(44L);
    }

    private void inInventory(InstalledDevice device, long id, long businessId, String status) {
        when(assets.fetchInventoryAssetByMacAddress(device.macAddress())).thenReturn(Optional.of(new InventoryAsset(
                id, businessId, device.macAddress(), device.type().name(), status, device.type().isEdge())));
    }

    @Test
    void rejectsMacsOutsideTheBusinessInventory() {
        assertThrows(InstallationAssetNotFound.class, () -> service.validateAndAssign(order, door, "habitacion-1"));
        inInventory(door, 22L, 2L, "AVAILABLE");
        assertThrows(InstallationAssetNotFound.class, () -> service.validateAndAssign(order, door, "habitacion-1"));
        verify(assets, never()).assignDevice(anyLong(), anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void rejectsTheWrongTypeAndAssetsThatAreNotAvailable() {
        when(assets.fetchInventoryAssetByMacAddress(door.macAddress())).thenReturn(Optional.of(
                new InventoryAsset(22L, 1L, door.macAddress(), "WINDOW", "AVAILABLE", false)));
        assertThrows(InstallationAssetTypeMismatch.class, () -> service.validateAndAssign(order, door, "habitacion-1"));
        inInventory(door, 22L, 1L, "IN_USE");
        assertThrows(DeviceNotAvailable.class, () -> service.validateAndAssign(order, door, "habitacion-1"));
    }

    @Test
    void asksForTheRoomOfADeviceAndForAValidType() {
        assertThrows(IllegalArgumentException.class, () -> service.validateAndAssign(order, door, " "));
        var legacy = new InstalledDevice("Hub", "00:1A:2B:3C:4D:5E", InstalledDeviceType.HUB);
        assertThrows(IllegalArgumentException.class, () -> service.validateAndAssign(order, legacy, "habitacion-1"));
    }

    @Test
    void needsTheOrderLinkedToItsHousehold() {
        when(assistance.fetchHouseholdIdOfAssistedPerson(3L)).thenReturn(Optional.empty());
        assertThrows(InstallationContextMissing.class, () -> service.validateAndAssign(order, door, "habitacion-1"));
    }

    @Test
    void requiresTheMatchingEdgeBeforeAssigningADevice() {
        inInventory(door, 22L, 1L, "AVAILABLE");
        assertThrows(RequiredInstallationEdgeMissing.class, () -> service.validateAndAssign(order, door, "habitacion-1"));
        verify(assets, never()).assignDevice(anyLong(), anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void placesEdgesThenAssignsDevicesToTheirRoom() {
        inInventory(edge, 2L, 1L, "AVAILABLE");
        service.validateAndAssign(order, edge, null);
        verify(assets).placeEdge(44L, 2L);

        inInventory(door, 30L, 1L, "AVAILABLE");
        when(assets.installationHasEdgeType(44L, "ACTUATOR_EDGE")).thenReturn(true);
        service.validateAndAssign(order, door, " habitacion-1 ");
        verify(assets).assignDevice(44L, 30L, "habitacion-1", "Puerta principal", 7L);
    }

    @Test
    void releasesWhatARemovedDeviceHeld() {
        inInventory(door, 30L, 1L, "IN_USE");
        service.release(order, door);
        verify(assets).unassignDevice(30L, "Quitado de la orden técnica 44", 7L);

        inInventory(edge, 2L, 1L, "IN_USE");
        service.release(order, edge);
        verify(assets).removeEdge(44L, 2L);
    }

    @Test
    void releaseIgnoresAvailableUnknownAndLegacyAssets() {
        inInventory(door, 30L, 1L, "AVAILABLE");
        service.release(order, door);
        service.release(order, new InstalledDevice("Luz", "8E:94:DF:4C:E5:30", InstalledDeviceType.LIGHT));
        service.release(order, new InstalledDevice("Hub", "00:1A:2B:3C:4D:5E", InstalledDeviceType.HUB));
        verify(assets, never()).unassignDevice(anyLong(), anyString(), anyLong());
        verify(assets, never()).removeEdge(anyLong(), anyLong());
        verify(assets, never()).fetchInventoryAssetByMacAddress("00:1A:2B:3C:4D:5E");
        verify(assets, never()).placeEdge(anyLong(), any());
    }
}
