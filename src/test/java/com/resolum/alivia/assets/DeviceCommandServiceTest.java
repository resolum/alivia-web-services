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
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
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
    private HouseholdDeviceRegistryRepository registries;
    private ExternalBusinessService companies;
    private DeviceCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceRepository.class);
        edges = mock(EdgeRepository.class);
        assignments = mock(DeviceAssignmentRepository.class);
        registries = mock(HouseholdDeviceRegistryRepository.class);
        companies = mock(ExternalBusinessService.class);
        service = new DeviceCommandServiceImpl(devices, companies, edges, assignments, registries);
        when(companies.existsBusiness(1L)).thenReturn(true);
        when(devices.saveAndFlush(any(Device.class))).thenAnswer(invocation -> {
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
        verify(devices).saveAndFlush(captor.capture());
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
        verify(devices, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAMacAddressAlreadyUsedByAnEdge() {
        when(edges.existsEdgeByMacAddress(new MacAddress(MAC))).thenReturn(true);

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterDeviceCommand(1L, MAC, "Luz", "LIGHT")));

        verify(devices, never()).saveAndFlush(any());
    }

    @Test
    void translatesAConcurrentDuplicateDetectedByTheDatabase() {
        when(devices.saveAndFlush(any(Device.class))).thenThrow(new DataIntegrityViolationException("uk_devices_mac_address"));

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterDeviceCommand(1L, MAC, "Luz", "LIGHT")));
    }

    @Test
    void doesNotHideOtherPersistenceFailures() {
        when(devices.saveAndFlush(any(Device.class))).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class,
                () -> service.handle(new RegisterDeviceCommand(1L, MAC, "Luz", "LIGHT")));
    }

    @Test
    void rejectsAnUnknownBusinessWithoutSaving() {
        when(companies.existsBusiness(99L)).thenReturn(false);

        assertThrows(BusinessNotFound.class,
                () -> service.handle(new RegisterDeviceCommand(99L, MAC, "Luz", "LIGHT")));

        verify(devices, never()).saveAndFlush(any());
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
        verify(devices, never()).saveAndFlush(any());
    }

    @Test
    void updatesTheTypeKeepingTheSameMacWithoutCheckingDuplicates() {
        when(devices.findById(7L)).thenReturn(Optional.of(storedDevice()));

        assertEquals(7L, service.handle(new UpdateDeviceCommand(7L, "aa-bb-cc-dd-ee-ff", "door")));

        var captor = ArgumentCaptor.forClass(Device.class);
        verify(devices).saveAndFlush(captor.capture());
        assertEquals(DeviceType.DOOR, captor.getValue().getDeviceType());
        verify(devices, never()).existsDeviceByMacAddress(any());
    }

    @Test
    void rejectsUpdatingToAMacAddressOfAnotherAsset() {
        when(devices.findById(7L)).thenReturn(Optional.of(storedDevice()));
        when(devices.existsDeviceByMacAddress(new MacAddress("AA:BB:CC:DD:EE:00"))).thenReturn(true);

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new UpdateDeviceCommand(7L, "aa:bb:cc:dd:ee:00", "LIGHT")));

        verify(devices, never()).saveAndFlush(any());
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
        verify(devices, times(1)).saveAndFlush(device);
    }

    @Test
    void theTypeOfAnAssignedDeviceCannotChangeUntilItIsUnassigned() {
        var device = storedDevice();
        device.changeStatusTo(AssetStatus.IN_USE);
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.of(mock(DeviceAssignment.class)));

        var exception = assertThrows(DeviceStillAssigned.class,
                () -> service.handle(new UpdateDeviceCommand(7L, MAC, "door")));

        assertTrue(exception.getMessage().contains("unassign it first"));
        assertEquals(DeviceType.LIGHT, device.getDeviceType());
        verify(devices, never()).saveAndFlush(any());
    }

    @Test
    void anAssignedDeviceMayStillChangeItsMacOrRepeatItsType() {
        var device = storedDevice();
        device.changeStatusTo(AssetStatus.IN_USE);
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.of(mock(DeviceAssignment.class)));

        service.handle(new UpdateDeviceCommand(7L, "aa:bb:cc:dd:ee:00", "light"));

        assertEquals("AA:BB:CC:DD:EE:00", device.getMacAddress().macAddress());
        assertEquals(DeviceType.LIGHT, device.getDeviceType());
        verify(devices).saveAndFlush(device);
    }

    @Test
    void aDeviceWithoutAssignmentMayChangeItsType() {
        var device = storedDevice();
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.empty());

        service.handle(new UpdateDeviceCommand(7L, MAC, "door"));

        assertEquals(DeviceType.DOOR, device.getDeviceType());
    }

    @Test
    void anInvalidTypeIsRejectedBeforeLookingAtTheAssignment() {
        when(devices.findById(7L)).thenReturn(Optional.of(storedDevice()));

        assertThrows(IllegalArgumentException.class, () -> service.handle(new UpdateDeviceCommand(7L, MAC, "TOASTER")));

        verifyNoInteractions(assignments);
    }

    @Test
    void aDeviceBecomesInUseOnlyByBeingAssigned() {
        var device = storedDevice();
        when(devices.findById(7L)).thenReturn(Optional.of(device));

        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.handle(new ChangeDeviceStatusCommand(7L, "IN_USE")));

        assertTrue(exception.getMessage().contains("assigned"));
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        verify(devices, never()).saveAndFlush(any());
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
        verify(devices, never()).saveAndFlush(any());
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

    @Test
    void changingARegisteredDeviceGivesTheTableOfItsHouseholdANewVersion() {
        var device = storedDevice();
        var registry = com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry.open(3L, 5L, 1L);
        registry.register(device, "sala", "Luz", 9L, java.time.LocalDateTime.of(2026, 11, 5, 10, 0),
                new com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators(java.util.Map.of(DeviceType.LIGHT, 1)));
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.of(mock(DeviceAssignment.class)));
        when(registries.findByActiveDeviceIdForUpdate(7L)).thenReturn(Optional.of(registry));

        service.handle(new UpdateDeviceCommand(7L, "aa:bb:cc:dd:ee:00", "light"));
        service.handle(new ChangeDeviceStatusCommand(7L, "MAINTENANCE"));

        assertEquals(3, registry.getTableVersion());
        verify(registries, times(2)).saveAndFlush(registry);
        assertEquals("AA:BB:CC:DD:EE:00", registry.table().nodes().get(0).macAddress());
        assertEquals(AssetStatus.MAINTENANCE, registry.table().nodes().get(0).status());
    }

    @Test
    void changingADeviceThatIsInNoHouseholdTouchesNoRegistry() {
        var device = storedDevice();
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(registries.findByActiveDeviceIdForUpdate(7L)).thenReturn(Optional.empty());

        service.handle(new UpdateDeviceCommand(7L, "aa:bb:cc:dd:ee:00", "door"));

        verify(registries, never()).saveAndFlush(any());
    }

    @Test
    void locksTheRegistryOfTheDeviceBeforeWritingTheDevice() {
        var device = storedDevice();
        var registry = com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry.open(3L, 5L, 1L);
        registry.register(device, "sala", "Luz", 9L, java.time.LocalDateTime.of(2026, 11, 5, 10, 0),
                new com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators(java.util.Map.of(DeviceType.LIGHT, 1)));
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.of(mock(DeviceAssignment.class)));
        when(registries.findByActiveDeviceIdForUpdate(7L)).thenReturn(Optional.of(registry));

        // Unassigning takes the registry first and the device second; the same order here keeps two requests on
        // one device from waiting for each other.
        service.handle(new UpdateDeviceCommand(7L, "aa:bb:cc:dd:ee:00", "light"));

        var update = inOrder(registries, devices);
        update.verify(registries).findByActiveDeviceIdForUpdate(7L);
        update.verify(devices).findById(7L); // read after the lock, so a change that was waiting for it is visible
        update.verify(devices).saveAndFlush(device);
        update.verify(registries).saveAndFlush(registry);

        clearInvocations(registries, devices);
        service.handle(new ChangeDeviceStatusCommand(7L, "MAINTENANCE"));

        var status = inOrder(registries, devices);
        status.verify(registries).findByActiveDeviceIdForUpdate(7L);
        status.verify(devices).findById(7L);
        status.verify(devices).saveAndFlush(device);
        status.verify(registries).saveAndFlush(registry);
    }

    @Test
    void doesNotWriteTheDeviceWhenTheRuleRejectsTheChangeButHasAlreadyLockedItsRegistry() {
        var device = storedDevice();
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.of(mock(DeviceAssignment.class)));
        when(registries.findByActiveDeviceIdForUpdate(7L)).thenReturn(Optional.of(
                com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry.open(3L, 5L, 1L)));

        assertThrows(DeviceStillAssigned.class, () -> service.handle(new ChangeDeviceStatusCommand(7L, "RETIRED")));

        verify(registries).findByActiveDeviceIdForUpdate(7L);
        verify(devices, never()).saveAndFlush(any());
        verify(registries, never()).saveAndFlush(any());
    }
}
