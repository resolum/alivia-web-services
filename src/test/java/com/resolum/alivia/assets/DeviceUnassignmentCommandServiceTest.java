package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.DeviceAssignmentCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAccessDenied;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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

class DeviceUnassignmentCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T21:30:00Z"), ZoneOffset.UTC);
    /** A caregiver who manages household 5. */
    private static final UnassignDeviceCommand BY_CAREGIVER =
            new UnassignDeviceCommand(7L, " Reemplazo por falla ", 9L, CallerAuthority.HOUSEHOLD_MEMBER);

    private DeviceRepository devices;
    private HouseholdDeviceRegistryRepository registries;
    private ExternalHouseholdsService households;
    private DeviceAssignmentCommandServiceImpl service;
    private HouseholdDeviceRegistry registry;
    private Device device;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceRepository.class);
        registries = mock(HouseholdDeviceRegistryRepository.class);
        households = mock(ExternalHouseholdsService.class);
        service = new DeviceAssignmentCommandServiceImpl(devices, registries, mock(ExternalSubscriptionsService.class), households, CLOCK);
        device = new Device("AA:BB:CC:DD:EE:FF", "Luz de la sala", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        registry = HouseholdDeviceRegistry.open(3L, 5L, 1L);
        registry.register(device, "sala", "Luz principal", 4L, LocalDateTime.of(2026, 11, 4, 10, 0),
                new ContractedActuators(Map.of(DeviceType.LIGHT, 2)));
        when(devices.findById(7L)).thenReturn(Optional.of(device));
        when(registries.findByActiveDeviceIdForUpdate(7L)).thenReturn(Optional.of(registry));
        when(households.canManageHousehold(5L, 9L)).thenReturn(true);
    }

    @Test
    void closesTheEntryKeepsItAsHistoryAndLeavesTheDeviceAvailable() {
        var closed = service.handle(BY_CAREGIVER);

        assertFalse(closed.isActive());
        assertEquals(LocalDateTime.of(2026, 11, 20, 21, 30), closed.getUnassignedAt());
        assertEquals(9L, closed.getUnassignedBy());
        assertEquals("Reemplazo por falla", closed.getUnassignReason());
        assertEquals(5L, closed.getHouseholdId());
        assertEquals("sala", closed.getRoomKey());
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        assertFalse(registry.hasActiveDevice(7L));
        assertEquals(2, registry.getTableVersion());
        verify(devices).save(device);
        verify(registries).saveAndFlush(registry);
    }

    @Test
    void neverDeletesTheHistory() {
        service.handle(BY_CAREGIVER);

        verify(registries, never()).delete(any());
        verify(registries, never()).deleteById(any());
        verify(registries, never()).deleteAll();
        verify(registries, never()).deleteAllInBatch();
    }

    @Test
    void locksTheRegistryThatHoldsTheDeviceBeforeClosingIt() {
        service.handle(BY_CAREGIVER);

        InOrder order = inOrder(registries, devices);
        order.verify(registries).findByActiveDeviceIdForUpdate(7L);
        order.verify(devices).findById(7L); // read after the lock, so a change that was waiting for it is visible
        order.verify(devices).save(device);
        order.verify(registries).saveAndFlush(registry);
    }

    @Test
    void failsWhenTheDeviceDoesNotExist() {
        when(devices.findById(7L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFound.class, () -> service.handle(BY_CAREGIVER));

        verifyNoInteractions(households);
        verify(registries, never()).saveAndFlush(any());
        verify(devices, never()).save(any());
    }

    @Test
    void deniesAnAccountThatDoesNotManageTheHouseholdWithoutChangingAnything() {
        when(households.canManageHousehold(5L, 9L)).thenReturn(false);

        assertThrows(InstallationAccessDenied.class, () -> service.handle(BY_CAREGIVER));

        assertTrue(registry.hasActiveDevice(7L));
        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        assertEquals(1, registry.getTableVersion());
        verify(devices, never()).save(any());
        verify(registries, never()).saveAndFlush(any());
    }

    @Test
    void theAdministratorAndAnAuthorizedContextAreNotAskedWhetherTheyManageTheHousehold() {
        var closed = service.handle(new UnassignDeviceCommand(7L, "Baja del servicio", 8L, CallerAuthority.BUSINESS_ADMINISTRATOR));
        assertEquals(8L, closed.getUnassignedBy());

        registry.register(device, "cuarto", "Luz", 4L, LocalDateTime.of(2026, 11, 10, 10, 0),
                new ContractedActuators(Map.of(DeviceType.LIGHT, 2)));
        var byContext = service.handle(new UnassignDeviceCommand(7L, "Fin de la visita", 6L, CallerAuthority.AUTHORIZED_BY_CONTEXT));
        assertEquals(6L, byContext.getUnassignedBy());

        verifyNoInteractions(households);
    }

    @Test
    void anAdministratorOrAnAuthorizedContextIsToldWhenTheDeviceIsNotAssigned() {
        when(registries.findByActiveDeviceIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotAssigned.class,
                () -> service.handle(new UnassignDeviceCommand(7L, "Motivo", 8L, CallerAuthority.BUSINESS_ADMINISTRATOR)));
        assertThrows(DeviceNotAssigned.class,
                () -> service.handle(new UnassignDeviceCommand(7L, "Motivo", 6L, CallerAuthority.AUTHORIZED_BY_CONTEXT)));
        verify(registries, never()).saveAndFlush(any());
    }

    @Test
    void doesNotRevealTheStateOfTheInventoryToAnAccountThatCannotManageAnything() {
        when(registries.findByActiveDeviceIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(InstallationAccessDenied.class, () -> service.handle(BY_CAREGIVER));

        verifyNoInteractions(households);
    }

    @Test
    void doesNotRepairADeviceThatBrokeWhileAssigned() {
        device.changeStatusTo(AssetStatus.BROKEN);

        service.handle(BY_CAREGIVER);

        assertFalse(registry.hasActiveDevice(7L));
        assertEquals(AssetStatus.BROKEN, device.getAssetStatus());
    }

    @Test
    void rejectsAnInvalidReasonWithoutChangingAnything() {
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(7L, " ", 9L, CallerAuthority.HOUSEHOLD_MEMBER));
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(7L, null, 9L, CallerAuthority.HOUSEHOLD_MEMBER));
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(null, "Motivo", 9L, CallerAuthority.HOUSEHOLD_MEMBER));
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(7L, "Motivo", null, CallerAuthority.HOUSEHOLD_MEMBER));
        assertThrows(IllegalArgumentException.class, () -> new UnassignDeviceCommand(7L, "Motivo", 9L, null));
        assertThrows(IllegalArgumentException.class, () -> service.handle(new UnassignDeviceCommand(7L,
                "a".repeat(300 + 1), 9L, CallerAuthority.HOUSEHOLD_MEMBER)));

        assertTrue(registry.hasActiveDevice(7L));
        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        assertEquals(1, registry.getTableVersion());
        verify(devices, never()).save(any());
    }
}
