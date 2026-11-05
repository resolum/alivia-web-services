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
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
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

/** Who may place a device, in which order the checks run, and that nothing is stored when one fails. */
class DeviceAssignmentCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC);
    private static final InstallationId INSTALLATION = new InstallationId(3L);
    /** A caregiver who manages household 5. */
    private static final AssignDeviceToInstallationCommand COMMAND =
            new AssignDeviceToInstallationCommand(3L, 7L, " sala ", "Luz principal", 9L, CallerAuthority.HOUSEHOLD_MEMBER);

    private DeviceRepository devices;
    private HouseholdDeviceRegistryRepository registries;
    private ExternalSubscriptionsService subscriptions;
    private ExternalHouseholdsService households;
    private DeviceAssignmentCommandServiceImpl service;
    private HouseholdDeviceRegistry registry;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceRepository.class);
        registries = mock(HouseholdDeviceRegistryRepository.class);
        subscriptions = mock(ExternalSubscriptionsService.class);
        households = mock(ExternalHouseholdsService.class);
        service = new DeviceAssignmentCommandServiceImpl(devices, registries, subscriptions, households, CLOCK);
        registry = HouseholdDeviceRegistry.open(3L, 5L, 1L);
        when(registries.findByInstallationIdForUpdate(INSTALLATION)).thenReturn(Optional.of(registry));
        when(households.canManageHousehold(5L, 9L)).thenReturn(true);
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
        verify(registries, never()).saveAndFlush(any());
        assertEquals(0, registry.getTableVersion());
    }

    @Test
    void placesACompatibleDeviceInTheRegistryOfTheInstallation() {
        var device = storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

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
        assertTrue(registry.hasActiveDevice(7L));
        assertEquals(1, registry.getTableVersion());
        verify(devices).save(device);
        verify(registries).saveAndFlush(registry);
    }

    @Test
    void locksTheRegistryBeforeLoadingTheDeviceAndStoringAnything() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        service.handle(COMMAND);

        InOrder order = inOrder(registries, devices);
        order.verify(registries).findByInstallationIdForUpdate(INSTALLATION);
        order.verify(devices).findById(7L);
        order.verify(registries).saveAndFlush(registry);
    }

    @Test
    void failsWhenTheInstallationDoesNotExistBeforeAnythingElse() {
        when(registries.findByInstallationIdForUpdate(INSTALLATION)).thenReturn(Optional.empty());

        assertThrows(InstallationNotFound.class, () -> service.handle(COMMAND));

        verifyNoInteractions(households, subscriptions, devices);
        verify(registries, never()).saveAndFlush(any());
    }

    @Test
    void deniesAnAccountThatDoesNotManageTheHouseholdBeforeLookingAtTheDevice() {
        when(households.canManageHousehold(5L, 9L)).thenReturn(false);

        assertThrows(InstallationAccessDenied.class, () -> service.handle(COMMAND));

        verifyNoInteractions(devices, subscriptions);
        assertNothingWasStored();
    }

    @Test
    void theAdministratorAndAnAuthorizedContextAreNotAskedWhetherTheyManageTheHousehold() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        var byAdministrator = service.handle(new AssignDeviceToInstallationCommand(3L, 7L, "sala", "Luz", 8L, CallerAuthority.BUSINESS_ADMINISTRATOR));
        assertEquals(8L, byAdministrator.getAssignedBy());

        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        var byContext = service.handle(new AssignDeviceToInstallationCommand(3L, 7L, "cuarto", "Luz", 6L, CallerAuthority.AUTHORIZED_BY_CONTEXT));
        assertEquals(6L, byContext.getAssignedBy());

        verifyNoInteractions(households);
    }

    @Test
    void nobodyPlacesDevicesInARegistryThatIsNotInService() {
        ReflectionTestUtils.setField(registry, "status", RegistryStatus.DISMANTLED);

        for (var authority : CallerAuthority.values()) {
            assertThrows(InstallationNotActive.class, () -> service.handle(
                    new AssignDeviceToInstallationCommand(3L, 7L, "sala", "Luz", 9L, authority)), authority.name());
        }
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
    void theRegistryAppliesThePlanLimitsAndNothingIsStoredWhenItRejects() {
        var device = storedDevice("CURTAIN", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        assertThrows(DeviceIncompatibleWithPlan.class, () -> service.handle(COMMAND));

        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        assertNothingWasStored();
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
        when(registries.saveAndFlush(any(HouseholdDeviceRegistry.class)))
                .thenThrow(new DataIntegrityViolationException("uk_device_assignments_active_device"));

        var exception = assertThrows(DeviceNotAvailable.class, () -> service.handle(COMMAND));

        assertTrue(exception.isAlreadyAssigned());
    }

    @Test
    void doesNotHideOtherPersistenceFailures() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));
        when(registries.saveAndFlush(any(HouseholdDeviceRegistry.class))).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class, () -> service.handle(COMMAND));
    }

    @Test
    void asksSubscriptionsForTheHouseholdOfTheInstallationNotForTheCaller() {
        storedDevice("LIGHT", AssetStatus.AVAILABLE);
        contracted(Map.of(DeviceType.LIGHT, 2));

        service.handle(COMMAND);

        verify(subscriptions, times(1)).fetchContractedActuators(5L);
    }
}
