package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.DeviceCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.DeviceStillAssigned;
import com.resolum.alivia.assets.domain.exceptions.InvalidAssetStatusTransition;
import com.resolum.alivia.assets.domain.exceptions.InvalidMacAddress;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.commands.ChangeDeviceStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceCommandServiceTest {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    private DeviceRepository devices;
    private EdgeRepository edges;
    private DeviceAssignmentRepository assignments;
    private ExternalBusinessService companies;
    private DeviceCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceRepository.class);
        edges = mock(EdgeRepository.class);
        assignments = mock(DeviceAssignmentRepository.class);
        companies = mock(ExternalBusinessService.class);
        service = new DeviceCommandServiceImpl(devices, companies, edges, assignments);
        when(companies.existsBusiness(1L)).thenReturn(true);
        when(devices.save(any(Device.class))).thenAnswer(invocation -> {
            var device = invocation.<Device>getArgument(0);
            ReflectionTestUtils.setField(device, "id", 7L);
            return device;
        });
    }

    private static Device storedDevice() {
        var device = new Device(MAC, "Luz de la sala", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        return device;
    }

    @Test
    void registersAnAvailableDeviceWithTheCanonicalMac() {
        var id = service.handle(new RegisterDeviceCommand(1L, "aa-bb-cc-dd-ee-ff", "  Luz de la sala ", "light"));

        assertEquals(7L, id);
        var captor = ArgumentCaptor.forClass(Device.class);
        verify(devices).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(MAC, saved.getMacAddress().macAddress());
        assertEquals("Luz de la sala", saved.getAlias());
        assertEquals(DeviceType.LIGHT, saved.getDeviceType());
        assertEquals(AssetStatus.AVAILABLE, saved.getAssetStatus());
        assertEquals(1L, saved.getBusinessId().businessId());
    }

    @Test
    void rejectsADuplicateMacAddressOfAnotherDeviceEvenInAnotherFormat() {
        when(devices.existsDeviceByMacAddress(new MacAddress(MAC))).thenReturn(true);

        var exception = assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterDeviceCommand(1L, "aa:bb:cc:dd:ee:ff", "Luz", "LIGHT")));

        assertTrue(exception.getMessage().contains(MAC));
        verify(devices, never()).save(any());
    }

    @Test
    void rejectsAMacAddressAlreadyUsedByAnEdge() {
        when(edges.existsEdgeByMacAddress(new MacAddress(MAC))).thenReturn(true);

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterDeviceCommand(1L, MAC, "Luz", "LIGHT")));

        verify(devices, never()).save(any());
    }

    @Test
    void translatesAConcurrentDuplicateDetectedByTheDatabase() {
        when(devices.save(any(Device.class))).thenThrow(new DataIntegrityViolationException("uk_devices_mac_address"));

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterDeviceCommand(1L, MAC, "Luz", "LIGHT")));
    }

    @Test
    void doesNotHideOtherPersistenceFailures() {
        when(devices.save(any(Device.class))).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class,
                () -> service.handle(new RegisterDeviceCommand(1L, MAC, "Luz", "LIGHT")));
    }

    @Test
    void rejectsAnUnknownBusinessWithoutSaving() {
        when(companies.existsBusiness(99L)).thenReturn(false);

        assertThrows(BusinessNotFound.class,
                () -> service.handle(new RegisterDeviceCommand(99L, MAC, "Luz", "LIGHT")));

        verify(devices, never()).save(any());
    }

    @Test
    void rejectsInvalidDataBeforeLookingAnythingUp() {
        assertThrows(InvalidMacAddress.class,
                () -> service.handle(new RegisterDeviceCommand(1L, "not-a-mac", "Luz", "LIGHT")));
        assertThrows(IllegalArgumentException.class,
                () -> service.handle(new RegisterDeviceCommand(1L, MAC, "a".repeat(81), "LIGHT")));
        var unknownType = assertThrows(IllegalArgumentException.class,
                () -> service.handle(new RegisterDeviceCommand(1L, MAC, "Luz", "TOASTER")));

        assertEquals("Unknown device type: TOASTER.", unknownType.getMessage());
        verifyNoInteractions(companies, edges);
        verify(devices, never()).existsDeviceByMacAddress(any());
        verify(devices, never()).save(any());
    }

    @Test
    void updatesTheTypeKeepingTheSameMacWithoutCheckingDuplicates() {
        when(devices.findById(7L)).thenReturn(Optional.of(storedDevice()));

        assertEquals(7L, service.handle(new UpdateDeviceCommand(7L, "aa-bb-cc-dd-ee-ff", "door")));

        var captor = ArgumentCaptor.forClass(Device.class);
        verify(devices).save(captor.capture());
        assertEquals(DeviceType.DOOR, captor.getValue().getDeviceType());
        verify(devices, never()).existsDeviceByMacAddress(any());
    }

    @Test
    void rejectsUpdatingToAMacAddressOfAnotherAsset() {
        when(devices.findById(7L)).thenReturn(Optional.of(storedDevice()));
        when(devices.existsDeviceByMacAddress(new MacAddress("AA:BB:CC:DD:EE:00"))).thenReturn(true);

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new UpdateDeviceCommand(7L, "aa:bb:cc:dd:ee:00", "LIGHT")));

        verify(devices, never()).save(any());
    }

    @Test
    void failsToUpdateAMissingDevice() {
        when(devices.findById(8L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFound.class, () -> service.handle(new UpdateDeviceCommand(8L, MAC, "LIGHT")));
    }

    @Test
    void changesStatusOnlyAlongAValidTransition() {
        var device = storedDevice();
        when(devices.findById(7L)).thenReturn(Optional.of(device));

        service.handle(new ChangeDeviceStatusCommand(7L, "RETIRED"));

        assertEquals(AssetStatus.RETIRED, device.getAssetStatus());
        assertThrows(InvalidAssetStatusTransition.class,
                () -> service.handle(new ChangeDeviceStatusCommand(7L, "AVAILABLE")));
        assertEquals(AssetStatus.RETIRED, device.getAssetStatus());
        verify(devices, times(1)).save(device);
    }

    @Test
    void aDeviceBecomesInUseOnlyByBeingAssigned() {
        var device = storedDevice();
        when(devices.findById(7L)).thenReturn(Optional.of(device));

        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.handle(new ChangeDeviceStatusCommand(7L, "IN_USE")));

        assertTrue(exception.getMessage().contains("assigned"));
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        verify(devices, never()).save(any());
    }

    @Test
    void anAssignedDeviceCannotBecomeAvailableOrRetiredUntilItIsUnassigned() {
        var device = storedDevice();
        device.changeStatusTo(AssetStatus.IN_USE);
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.of(mock(DeviceAssignment.class)));

        assertThrows(DeviceStillAssigned.class, () -> service.handle(new ChangeDeviceStatusCommand(7L, "AVAILABLE")));
        assertThrows(DeviceStillAssigned.class, () -> service.handle(new ChangeDeviceStatusCommand(7L, "retired")));

        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        verify(devices, never()).save(any());
    }

    @Test
    void anAssignedDeviceMayGoToMaintenanceOrBrokenBecauseTheEquipmentCanFailInTheHousehold() {
        var device = storedDevice();
        device.changeStatusTo(AssetStatus.IN_USE);
        when(devices.findById(7L)).thenReturn(Optional.of(device));

        service.handle(new ChangeDeviceStatusCommand(7L, "MAINTENANCE"));

        assertEquals(AssetStatus.MAINTENANCE, device.getAssetStatus());
        verify(assignments, never()).findActiveByDeviceId(any());
    }

    @Test
    void aDeviceWithoutAssignmentChangesStatusFreely() {
        var device = storedDevice();
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.empty());

        service.handle(new ChangeDeviceStatusCommand(7L, "RETIRED"));

        assertEquals(AssetStatus.RETIRED, device.getAssetStatus());
    }
}
