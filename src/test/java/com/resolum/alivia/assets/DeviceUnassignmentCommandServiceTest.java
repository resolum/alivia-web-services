package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.DeviceAssignmentCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAccessDenied;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceUnassignmentCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T21:30:00Z"), ZoneOffset.UTC);
    /** A caregiver who manages household 5. */
    private static final UnassignDeviceCommand BY_CAREGIVER = new UnassignDeviceCommand(7L, " Reemplazo por falla ", 9L, CallerAuthority.HOUSEHOLD_MEMBER);

    private DeviceRepository devices;
    private DeviceAssignmentRepository assignments;
    private ExternalHouseholdsService households;
    private DeviceAssignmentCommandServiceImpl service;
    private Device device;
    private DeviceAssignment assignment;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceRepository.class);
        assignments = mock(DeviceAssignmentRepository.class);
        households = mock(ExternalHouseholdsService.class);
        service = new DeviceAssignmentCommandServiceImpl(devices, mock(DeviceNetworkRepository.class), assignments,
                mock(ExternalSubscriptionsService.class), households, CLOCK);
        device = new Device("AA:BB:CC:DD:EE:FF", "Luz de la sala", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        assignment = device.assignTo(3L, 5L, "sala", "Luz principal", 4L, LocalDateTime.of(2026, 11, 4, 10, 0));
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(assignments.findActiveByDeviceIdForUpdate(7L)).thenReturn(Optional.of(assignment));
        when(households.canManageHousehold(5L, 9L)).thenReturn(true);
        when(assignments.saveAndFlush(any(DeviceAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void closesTheAssignmentKeepsItAsHistoryAndLeavesTheDeviceAvailable() {
        var closed = service.handle(BY_CAREGIVER);

        assertSame(assignment, closed);
        assertFalse(closed.isActive());
        assertEquals(LocalDateTime.of(2026, 11, 20, 21, 30), closed.getUnassignedAt());
        assertEquals(9L, closed.getUnassignedBy());
        assertEquals("Reemplazo por falla", closed.getUnassignReason());
        assertEquals(5L, closed.getHouseholdId());
        assertEquals("sala", closed.getRoomKey());
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        verify(devices).save(device);
        verify(assignments).saveAndFlush(assignment);
    }

    @Test
    void neverDeletesTheHistory() {
        service.handle(BY_CAREGIVER);

        verify(assignments, never()).delete(any());
        verify(assignments, never()).deleteById(any());
        verify(assignments, never()).deleteAll();
        verify(assignments, never()).deleteAllInBatch();
    }

    @Test
    void locksTheAssignmentBeforeClosingIt() {
        service.handle(BY_CAREGIVER);

        InOrder order = inOrder(assignments);
        order.verify(assignments).findActiveByDeviceIdForUpdate(7L);
        order.verify(assignments).saveAndFlush(assignment);
    }

    @Test
    void anotherContextThatAlreadyAuthorizedTheCallerCanUnassignWithoutAssetsAskingAgain() {
        var closed = service.handle(new UnassignDeviceCommand(7L, "Fin de la visita", 6L, CallerAuthority.AUTHORIZED_BY_CONTEXT));

        assertEquals(6L, closed.getUnassignedBy());
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        verifyNoInteractions(households);
    }

    @Test
    void anAuthorizedContextIsToldWhenTheDeviceIsNotAssigned() {
        when(assignments.findActiveByDeviceIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotAssigned.class,
                () -> service.handle(new UnassignDeviceCommand(7L, "Motivo", 6L, CallerAuthority.AUTHORIZED_BY_CONTEXT)));
    }

    @Test
    void failsWhenTheDeviceDoesNotExist() {
        when(devices.findById(7L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFound.class, () -> service.handle(BY_CAREGIVER));

        verifyNoInteractions(assignments, households);
    }

    @Test
    void deniesAnAccountThatDoesNotManageTheHouseholdWithoutChangingAnything() {
        when(households.canManageHousehold(5L, 9L)).thenReturn(false);

        assertThrows(InstallationAccessDenied.class, () -> service.handle(BY_CAREGIVER));

        assertTrue(assignment.isActive());
        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        verify(devices, never()).save(any());
        verify(assignments, never()).saveAndFlush(any());
    }

    @Test
    void theBusinessAdministratorMayUnassignWithoutBelongingToTheHousehold() {
        when(households.canManageHousehold(5L, 8L)).thenReturn(false);

        var closed = service.handle(new UnassignDeviceCommand(7L, "Baja del servicio", 8L, CallerAuthority.BUSINESS_ADMINISTRATOR));

        assertEquals(8L, closed.getUnassignedBy());
        verifyNoInteractions(households);
    }

    @Test
    void answersNotAssignedToTheBusinessAdministratorWhenThereIsNoActiveAssignment() {
        when(assignments.findActiveByDeviceIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotAssigned.class, () -> service.handle(new UnassignDeviceCommand(7L, "Baja", 8L, CallerAuthority.BUSINESS_ADMINISTRATOR)));

        verify(assignments, never()).saveAndFlush(any());
    }

    @Test
    void doesNotRevealTheStateOfTheInventoryToAnAccountThatCannotManageAnything() {
        when(assignments.findActiveByDeviceIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(InstallationAccessDenied.class, () -> service.handle(BY_CAREGIVER));

        verifyNoInteractions(households);
    }

    @Test
    void doesNotRepairADeviceThatBrokeWhileAssigned() {
        device.changeStatusTo(AssetStatus.BROKEN);

        service.handle(BY_CAREGIVER);

        assertFalse(assignment.isActive());
        assertEquals(AssetStatus.BROKEN, device.getAssetStatus());
    }

    @Test
    void rejectsAnInvalidReasonWithoutChangingAnything() {
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(7L, " ", 9L, CallerAuthority.HOUSEHOLD_MEMBER));
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(7L, null, 9L, CallerAuthority.HOUSEHOLD_MEMBER));
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(null, "Motivo", 9L, CallerAuthority.HOUSEHOLD_MEMBER));
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(7L, "Motivo", null, CallerAuthority.HOUSEHOLD_MEMBER));
        assertThrows(IllegalArgumentException.class, () -> service.handle(
                new UnassignDeviceCommand(7L, "a".repeat(DeviceAssignment.UNASSIGN_REASON_MAX_LENGTH + 1), 9L, CallerAuthority.HOUSEHOLD_MEMBER)));

        assertTrue(assignment.isActive());
        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        verify(devices, never()).save(any());
    }
}
