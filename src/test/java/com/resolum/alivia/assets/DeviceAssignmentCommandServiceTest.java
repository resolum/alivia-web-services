package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.DeviceAssignmentCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAccessDenied;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.NetworkStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceAssignmentCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC);
    private static final InstallationId INSTALLATION = new InstallationId(3L);
    /** A caregiver who manages household 5. */
    private static final AssignDeviceToInstallationCommand COMMAND =
            new AssignDeviceToInstallationCommand(3L, 7L, " sala ", "Luz principal", 9L, CallerAuthority.HOUSEHOLD_MEMBER);

    private DeviceRepository devices;
    private DeviceNetworkRepository networks;
    private DeviceAssignmentRepository assignments;
    private ExternalSubscriptionsService subscriptions;
    private ExternalHouseholdsService households;
    private DeviceAssignmentCommandServiceImpl service;
    private DeviceNetwork network;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceRepository.class);
        networks = mock(DeviceNetworkRepository.class);
        assignments = mock(DeviceAssignmentRepository.class);
        subscriptions = mock(ExternalSubscriptionsService.class);
        households = mock(ExternalHouseholdsService.class);
        service = new DeviceAssignmentCommandServiceImpl(devices, networks, assignments, subscriptions, households, CLOCK);
        network = new DeviceNetwork(3L, 5L, 1L);
        when(networks.findByInstallationIdForUpdate(INSTALLATION)).thenReturn(Optional.of(network));
        when(households.canManageHousehold(5L, 9L)).thenReturn(true);
        when(assignments.saveAndFlush(any(DeviceAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Device storedDevice(String type, AssetStatus status) {
        var device = new Device("AA:BB:CC:DD:EE:FF", "Dispositivo", type, 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        ReflectionTestUtils.setField(device, "assetStatus", status);
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        return device;
    }

    private void contracted(Map<DeviceType, Integer> quantities) {
        when(subscriptions.fetchContractedActuators(5L)).thenReturn(Optional.of(new ContractedActuators(quantities)));
    }

    private void assertNothingWasStored() {
        verify(devices, never()).save(any());
        verify(assignments, never()).saveAndFlush(any());
    }

    @Test
    void assignsACompatibleDeviceToTheHouseholdOfTheInstallation() {
        var device = storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));
        when(assignments.countActiveByHouseholdIdAndDeviceType(5L, DeviceType.LIGHT)).thenReturn(1L);

        var assignment = service.handle(COMMAND);

        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        assertSame(device, assignment.getDevice());
        assertEquals(3L, assignment.getInstallationId());
        assertEquals(5L, assignment.getHouseholdId());
        assertEquals("sala", assignment.getRoomKey());
        assertEquals("Luz principal", assignment.getFunction());
        assertEquals(9L, assignment.getAssignedBy());
        assertEquals(LocalDateTime.of(2026, 11, 4, 15, 0), assignment.getAssignedAt());
        assertTrue(assignment.isActive());
        verify(devices).save(device);
    }

    @Test
    void locksTheInstallationBeforeCountingWhatIsAssigned() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        service.handle(COMMAND);

        InOrder order = inOrder(networks, assignments);
        order.verify(networks).findByInstallationIdForUpdate(INSTALLATION);
        order.verify(assignments).countActiveByHouseholdIdAndDeviceType(5L, DeviceType.LIGHT);
        order.verify(assignments).saveAndFlush(any(DeviceAssignment.class));
    }

    @Test
    void failsWhenTheInstallationDoesNotExistBeforeAnythingElse() {
        when(networks.findByInstallationIdForUpdate(INSTALLATION)).thenReturn(Optional.empty());

        assertThrows(InstallationNotFound.class, () -> service.handle(COMMAND));

        verifyNoInteractions(households, subscriptions, devices);
        assertNothingWasStored();
    }

    @Test
    void treatsANetworkWithoutHouseholdAsAnInstallationThatDoesNotExist() {
        var legacy = new DeviceNetwork();
        when(networks.findByInstallationIdForUpdate(INSTALLATION)).thenReturn(Optional.of(legacy));

        assertThrows(InstallationNotFound.class, () -> service.handle(COMMAND));
    }

    @Test
    void deniesAnAccountThatDoesNotManageTheHouseholdBeforeLookingAtTheDevice() {
        when(households.canManageHousehold(5L, 9L)).thenReturn(false);

        assertThrows(InstallationAccessDenied.class, () -> service.handle(COMMAND));

        verifyNoInteractions(devices, subscriptions);
        assertNothingWasStored();
    }

    @Test
    void lettsTheBusinessAdministratorAssignWithoutBelongingToTheHousehold() {
        when(households.canManageHousehold(5L, 8L)).thenReturn(false);
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 1));

        var assignment = service.handle(new AssignDeviceToInstallationCommand(3L, 7L, "sala", "Luz", 8L, CallerAuthority.BUSINESS_ADMINISTRATOR));

        assertEquals(8L, assignment.getAssignedBy());
        verifyNoInteractions(households);
    }

    @Test
    void anotherContextThatAlreadyAuthorizedTheCallerCanPlaceDevicesWithoutAssetsAskingAgain() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        var assignment = service.handle(new AssignDeviceToInstallationCommand(3L, 7L, "sala", "Luz", 6L,
                CallerAuthority.AUTHORIZED_BY_CONTEXT));

        assertEquals(6L, assignment.getAssignedBy());
        verifyNoInteractions(households);
    }

    @Test
    void nobodyPlacesDevicesInAClosedInstallationNotEvenTheAdministratorOrAnAuthorizedContext() {
        ReflectionTestUtils.setField(network, "networkStatus", NetworkStatus.DISMANTLED);

        for (var authority : new CallerAuthority[]{CallerAuthority.BUSINESS_ADMINISTRATOR, CallerAuthority.AUTHORIZED_BY_CONTEXT,
                CallerAuthority.HOUSEHOLD_MEMBER}) {
            assertThrows(InstallationNotActive.class,
                    () -> service.handle(new AssignDeviceToInstallationCommand(3L, 7L, "sala", "Luz", 9L, authority)), authority.name());
        }
        assertNothingWasStored();
    }

    @Test
    void rejectsAnInstallationThatIsNotActive() {
        ReflectionTestUtils.setField(network, "networkStatus", NetworkStatus.DEACTIVATED);

        assertThrows(InstallationNotActive.class, () -> service.handle(COMMAND));

        verifyNoInteractions(devices, subscriptions);
        assertNothingWasStored();
    }

    @Test
    void failsWhenTheDeviceDoesNotExist() {
        when(devices.findById(7L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFound.class, () -> service.handle(COMMAND));

        verifyNoInteractions(subscriptions);
        assertNothingWasStored();
    }

    @Test
    void rejectsADeviceThatIsNotAvailableBeforeLookingAtThePlan() {
        for (var status : new AssetStatus[]{AssetStatus.IN_USE, AssetStatus.MAINTENANCE, AssetStatus.BROKEN, AssetStatus.RETIRED}) {
            var device = storedDevice("LIGHT", status);

            var exception = assertThrows(DeviceNotAvailable.class, () -> service.handle(COMMAND));

            assertEquals(status, exception.getStatus());
            assertEquals(status, device.getAssetStatus());
        }
        verifyNoInteractions(subscriptions);
        assertNothingWasStored();
    }

    @Test
    void rejectsAHouseholdWithoutAnActiveSubscription() {
        var device = storedDevice("LIGHT", AssetStatus.AVAILABLE);
        when(subscriptions.fetchContractedActuators(5L)).thenReturn(Optional.empty());

        var exception = assertThrows(DeviceIncompatibleWithPlan.class, () -> service.handle(COMMAND));

        assertTrue(exception.getMessage().contains("no active subscription"));
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        assertNothingWasStored();
    }

    @Test
    void rejectsADeviceTypeTheHouseholdDidNotContract() {
        var device = storedDevice("CURTAIN", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        assertThrows(DeviceIncompatibleWithPlan.class, () -> service.handle(COMMAND));

        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        verify(assignments, never()).countActiveByHouseholdIdAndDeviceType(any(), any());
        assertNothingWasStored();
    }

    @Test
    void rejectsADeviceBeyondTheContractedQuantity() {
        var device = storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));
        when(assignments.countActiveByHouseholdIdAndDeviceType(5L, DeviceType.LIGHT)).thenReturn(2L);

        var exception = assertThrows(DeviceIncompatibleWithPlan.class, () -> service.handle(COMMAND));

        assertTrue(exception.getMessage().contains("already assigned"));
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        assertNothingWasStored();
    }

    @Test
    void acceptsEveryActuatorTypeUpToItsContractedQuantity() {
        contracted(Map.of(DeviceType.LIGHT, 1, DeviceType.DOOR, 1, DeviceType.WINDOW, 1, DeviceType.CURTAIN, 1));
        for (var type : new String[]{"LIGHT", "DOOR", "WINDOW", "CURTAIN"}) {
            storedDevice(type, AssetStatus.AVAILABLE);

            assertDoesNotThrow(() -> service.handle(COMMAND), type);
        }
    }

    @Test
    void acceptsTheMicrophoneOfARoomOnlyOnce() {
        var device = storedDevice("MICROPHONE", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 1));
        when(assignments.countActiveByHouseholdIdAndRoomKeyAndDeviceType(5L, "sala", DeviceType.MICROPHONE)).thenReturn(0L);

        assertDoesNotThrow(() -> service.handle(COMMAND));
        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());

        var second = storedDevice("MICROPHONE", AssetStatus.AVAILABLE);
        when(assignments.countActiveByHouseholdIdAndRoomKeyAndDeviceType(5L, "sala", DeviceType.MICROPHONE)).thenReturn(1L);

        var exception = assertThrows(DeviceIncompatibleWithPlan.class, () -> service.handle(COMMAND));

        assertTrue(exception.getMessage().contains("microphone"));
        assertEquals(AssetStatus.AVAILABLE, second.getAssetStatus());
    }

    @Test
    void doesNotChangeTheDeviceWhenTheAssignmentDataIsInvalid() {
        var device = storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        assertThrows(IllegalArgumentException.class, () -> service.handle(
                new AssignDeviceToInstallationCommand(3L, 7L, "a".repeat(61), "Luz", 9L, CallerAuthority.HOUSEHOLD_MEMBER)));

        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        assertNothingWasStored();
    }

    @Test
    void translatesAConcurrentAssignmentOfTheSameDeviceToAConflict() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));
        when(assignments.saveAndFlush(any(DeviceAssignment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_device_assignments_active_device"));

        var exception = assertThrows(DeviceNotAvailable.class, () -> service.handle(COMMAND));

        assertTrue(exception.isAlreadyAssigned());
    }

    @Test
    void doesNotHideOtherPersistenceFailures() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));
        when(assignments.saveAndFlush(any(DeviceAssignment.class))).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class, () -> service.handle(COMMAND));
    }

    @Test
    void asksSubscriptionsForTheHouseholdOfTheInstallationNotForTheCaller() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        service.handle(COMMAND);

        verify(subscriptions, times(1)).fetchContractedActuators(5L);
        verify(assignments, never()).countActiveByHouseholdIdAndRoomKeyAndDeviceType(any(), any(), any());
    }
}
