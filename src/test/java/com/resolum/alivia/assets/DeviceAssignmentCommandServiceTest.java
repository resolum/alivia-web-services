package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.DeviceAssignmentCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToHouseholdCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeviceAssignmentCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC);
    private static final AssignDeviceToHouseholdCommand COMMAND =
            new AssignDeviceToHouseholdCommand(7L, 5L, " sala ", "Luz principal", 9L);

    private DeviceRepository devices;
    private DeviceAssignmentRepository assignments;
    private ExternalSubscriptionsService subscriptions;
    private DeviceAssignmentCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceRepository.class);
        assignments = mock(DeviceAssignmentRepository.class);
        subscriptions = mock(ExternalSubscriptionsService.class);
        service = new DeviceAssignmentCommandServiceImpl(devices, assignments, subscriptions, CLOCK);
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
    void assignsACompatibleDeviceAndStoresTheActiveAssignment() {
        var device = storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));
        when(assignments.countActiveByHouseholdIdAndDeviceType(5L, DeviceType.LIGHT)).thenReturn(1L);

        var assignment = service.handle(COMMAND);

        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        assertSame(device, assignment.getDevice());
        assertEquals(5L, assignment.getHouseholdId());
        assertEquals("sala", assignment.getRoomKey());
        assertEquals("Luz principal", assignment.getFunction());
        assertEquals(9L, assignment.getAssignedBy());
        assertEquals(LocalDateTime.of(2026, 11, 4, 15, 0), assignment.getAssignedAt());
        assertTrue(assignment.isActive());
        verify(devices).save(device);
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
                new AssignDeviceToHouseholdCommand(7L, 5L, "a".repeat(61), "Luz", 9L)));

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
    void asksTheHouseholdOnceWithItsNumericId() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));
        var captor = ArgumentCaptor.forClass(Long.class);

        service.handle(COMMAND);

        verify(subscriptions, times(1)).fetchContractedActuators(captor.capture());
        assertEquals(5L, captor.getValue());
        verify(assignments, never()).countActiveByHouseholdIdAndRoomKeyAndDeviceType(any(), eq("sala"), any());
    }
}
