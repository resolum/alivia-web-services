package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent.Cause;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The rules of the devices of a household, applied by the registry on its own entries. */
class HouseholdDeviceRegistryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);
    private static final ContractedActuators PLAN = new ContractedActuators(Map.of(DeviceType.LIGHT, 2, DeviceType.DOOR, 1));

    private static Device device(long id, String type) {
        var device = new Device(String.format("AA:BB:CC:DD:EE:%02X", id), "Dispositivo " + id, type, 1L);
        ReflectionTestUtils.setField(device, "id", id);
        return device;
    }

    private static HouseholdDeviceRegistry registry() {
        return HouseholdDeviceRegistry.open(3L, 5L, 1L);
    }

    @SuppressWarnings("unchecked")
    private static List<HouseholdDevicesChangedEvent> events(HouseholdDeviceRegistry registry) {
        return ((Collection<Object>) ReflectionTestUtils.invokeMethod(registry, "domainEvents")).stream()
                .map(HouseholdDevicesChangedEvent.class::cast).toList();
    }

    private static void register(HouseholdDeviceRegistry registry, Device device, String room) {
        registry.register(device, room, "Función de " + device.getId(), 9L, NOW, PLAN);
    }

    // ---- opening ----------------------------------------------------------------------------------------------

    @Test
    void opensActiveAndEmptyAtVersionZero() {
        var registry = registry();

        assertTrue(registry.isActive());
        assertEquals(RegistryStatus.ACTIVE, registry.getStatus());
        assertEquals(5L, registry.getHouseholdId());
        assertEquals(3L, registry.getInstallationId().installationId());
        assertEquals(1L, registry.getBusinessId().businessId());
        assertEquals(0, registry.getTableVersion());
        assertTrue(registry.table().nodes().isEmpty());
        assertEquals(0, registry.table().version());
        assertTrue(events(registry).isEmpty(), "opening announces nothing: there is no table to send yet");
    }

    @Test
    void rejectsInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> HouseholdDeviceRegistry.open(3L, null, 1L));
        assertThrows(IllegalArgumentException.class, () -> HouseholdDeviceRegistry.open(3L, 0L, 1L));
        assertThrows(IllegalArgumentException.class, () -> HouseholdDeviceRegistry.open(0L, 5L, 1L));
        assertThrows(IllegalArgumentException.class, () -> HouseholdDeviceRegistry.open(3L, 5L, null));
    }

    // ---- placing a device ----------------------------------------------------------------------------------------

    @Test
    void placesAnAvailableDeviceAndLeavesItInUse() {
        var registry = registry();
        var light = device(7, "LIGHT");

        var entry = registry.register(light, " sala ", " Luz principal ", 9L, NOW, PLAN);

        assertEquals(AssetStatus.IN_USE, light.getAssetStatus());
        assertSame(light, entry.getDevice());
        assertEquals(3L, entry.getInstallationId());
        assertEquals(5L, entry.getHouseholdId());
        assertEquals("sala", entry.getRoomKey());
        assertEquals("Luz principal", entry.getFunction());
        assertEquals(9L, entry.getAssignedBy());
        assertTrue(entry.isActive());
        assertTrue(registry.hasActiveDevice(7L));
        assertEquals(1, registry.getTableVersion());
    }

    @Test
    void everyChangeAddsOneToTheVersionAndAnnouncesTheCompleteTableOfThatVersion() {
        var registry = registry();
        var light = device(7, "LIGHT");
        var door = device(8, "DOOR");

        register(registry, light, "sala");
        register(registry, door, "cuarto");
        light.updateMacAddress("AA:BB:CC:DD:EE:99");
        registry.deviceChanged(7L);
        registry.remove(8L, "Mudanza", 8L, NOW.plusDays(1));

        var events = events(registry);
        assertEquals(List.of(Cause.DEVICE_ASSIGNED, Cause.DEVICE_ASSIGNED, Cause.DEVICE_CHANGED, Cause.DEVICE_UNASSIGNED),
                events.stream().map(HouseholdDevicesChangedEvent::cause).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L), events.stream().map(event -> event.table().version()).toList());
        assertEquals(List.of(7L, 8L, 7L, 8L), events.stream().map(HouseholdDevicesChangedEvent::deviceId).toList());
        assertEquals(List.of(1, 2, 2, 1), events.stream().map(event -> event.table().nodes().size()).toList());
        assertEquals(4, registry.getTableVersion());
        assertTrue(events.stream().allMatch(event -> event.householdId().equals(5L) && event.installationId().equals(3L)));
    }

    @Test
    void anEventKeepsTheTableAsItWasEvenIfTheRegistryChangesAfterwards() {
        var registry = registry();
        var light = device(7, "LIGHT");
        register(registry, light, "sala");
        var firstTable = events(registry).get(0).table();

        register(registry, device(8, "DOOR"), "cuarto");
        light.updateMacAddress("AA:BB:CC:DD:EE:99");

        assertEquals(1, firstTable.nodes().size());
        assertEquals("AA:BB:CC:DD:EE:07", firstTable.nodes().get(0).macAddress());
        assertEquals(1, firstTable.version());
    }

    @Test
    void theTableListsEveryActiveDeviceOrderedByIdWithRoomAndFunction() {
        var registry = registry();
        register(registry, device(9, "DOOR"), "cuarto");
        register(registry, device(4, "LIGHT"), "sala");

        var table = registry.table();

        assertEquals(List.of(4L, 9L), table.nodes().stream().map(node -> node.deviceId()).toList());
        assertEquals("sala", table.nodes().get(0).roomKey());
        assertEquals("Función de 9", table.nodes().get(1).function());
        assertEquals(DeviceType.DOOR, table.nodes().get(1).deviceType());
        assertEquals(AssetStatus.IN_USE, table.nodes().get(0).status());
    }

    // ---- the plan's limits ---------------------------------------------------------------------------------------

    @Test
    void acceptsDevicesUpToTheContractedQuantityOfEachTypeAndNoMore() {
        var registry = registry();
        register(registry, device(1, "LIGHT"), "sala");
        register(registry, device(2, "LIGHT"), "cuarto");
        register(registry, device(3, "DOOR"), "sala");

        var extraLight = device(4, "LIGHT");
        var exception = assertThrows(DeviceIncompatibleWithPlan.class, () -> register(registry, extraLight, "sala"));

        assertTrue(exception.getMessage().contains("already assigned"));
        assertEquals(AssetStatus.AVAILABLE, extraLight.getAssetStatus());
        assertEquals(3, registry.getTableVersion());
        assertThrows(DeviceIncompatibleWithPlan.class, () -> register(registry, device(5, "DOOR"), "sala"));
    }

    @Test
    void rejectsATypeThePlanDoesNotInclude() {
        var registry = registry();
        var curtain = device(1, "CURTAIN");

        var exception = assertThrows(DeviceIncompatibleWithPlan.class, () -> register(registry, curtain, "sala"));

        assertTrue(exception.getMessage().contains("does not include CURTAIN"));
        assertEquals(AssetStatus.AVAILABLE, curtain.getAssetStatus());
        assertEquals(0, registry.getTableVersion());
        assertTrue(events(registry).isEmpty());
    }

    @Test
    void takingADeviceOutFreesItsPlaceInThePlan() {
        var registry = registry();
        register(registry, device(1, "DOOR"), "sala");
        assertThrows(DeviceIncompatibleWithPlan.class, () -> register(registry, device(2, "DOOR"), "cuarto"));

        registry.remove(1L, "Mudanza", 8L, NOW.plusDays(1));

        assertDoesNotThrow(() -> register(registry, device(2, "DOOR"), "cuarto"));
    }

    @Test
    void thePlanIncludesOneMicrophonePerRoom() {
        var registry = registry();
        register(registry, device(1, "MICROPHONE"), "sala");

        var second = device(2, "MICROPHONE");
        var exception = assertThrows(DeviceIncompatibleWithPlan.class, () -> register(registry, second, " sala "));

        assertTrue(exception.getMessage().contains("microphone"));
        assertEquals(AssetStatus.AVAILABLE, second.getAssetStatus());
        assertDoesNotThrow(() -> register(registry, device(3, "MICROPHONE"), "cuarto"), "another room has its own");
    }

    @Test
    void aRoomGetsItsMicrophoneBackWhenTheOldOneLeaves() {
        var registry = registry();
        register(registry, device(1, "MICROPHONE"), "sala");
        registry.remove(1L, "Falla", 8L, NOW.plusDays(1));

        assertDoesNotThrow(() -> register(registry, device(2, "MICROPHONE"), "sala"));
    }

    // ---- what is rejected leaves everything as it was ----------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = AssetStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsADeviceThatIsNotAvailableWithoutChangingAnything(AssetStatus status) {
        var registry = registry();
        var light = device(1, "LIGHT");
        ReflectionTestUtils.setField(light, "assetStatus", status);

        var exception = assertThrows(DeviceNotAvailable.class, () -> register(registry, light, "sala"));

        assertEquals(status, exception.getStatus());
        assertEquals(status, light.getAssetStatus());
        assertEquals(0, registry.getTableVersion());
        assertFalse(registry.hasActiveDevice(1L));
        assertTrue(events(registry).isEmpty());
    }

    @Test
    void invalidDataLeavesTheDeviceAndTheRegistryUntouched() {
        var registry = registry();
        var light = device(1, "LIGHT");

        assertThrows(IllegalArgumentException.class, () -> registry.register(light, " ", "Luz", 9L, NOW, PLAN));
        assertThrows(IllegalArgumentException.class, () -> registry.register(light, null, "Luz", 9L, NOW, PLAN));
        assertThrows(IllegalArgumentException.class, () -> registry.register(light, "sala", " ", 9L, NOW, PLAN));
        assertThrows(IllegalArgumentException.class, () -> registry.register(light, "a".repeat(61), "Luz", 9L, NOW, PLAN));
        assertThrows(IllegalArgumentException.class, () -> registry.register(light, "sala", "Luz", null, NOW, PLAN));
        assertThrows(IllegalArgumentException.class, () -> registry.register(light, "sala", "Luz", 9L, null, PLAN));

        assertEquals(AssetStatus.AVAILABLE, light.getAssetStatus());
        assertEquals(0, registry.getTableVersion());
        assertTrue(events(registry).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = RegistryStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void nothingIsPlacedInARegistryThatIsNotInService(RegistryStatus status) {
        var registry = registry();
        ReflectionTestUtils.setField(registry, "status", status);
        var light = device(1, "LIGHT");

        assertThrows(InstallationNotActive.class, () -> register(registry, light, "sala"));

        assertEquals(AssetStatus.AVAILABLE, light.getAssetStatus());
        assertEquals(0, registry.getTableVersion());
    }

    // ---- taking a device out ------------------------------------------------------------------------------------------

    @Test
    void takingADeviceOutClosesItsEntryAsHistoryAndLeavesItAvailable() {
        var registry = registry();
        var light = device(1, "LIGHT");
        register(registry, light, "sala");

        var closed = registry.remove(1L, " Reemplazo por falla ", 8L, NOW.plusDays(2));

        assertFalse(closed.isActive());
        assertEquals(8L, closed.getUnassignedBy());
        assertEquals("Reemplazo por falla", closed.getUnassignReason());
        assertEquals(NOW.plusDays(2), closed.getUnassignedAt());
        assertEquals("sala", closed.getRoomKey(), "the closed entry keeps where the device was");
        assertEquals(AssetStatus.AVAILABLE, light.getAssetStatus());
        assertFalse(registry.hasActiveDevice(1L));
        assertTrue(registry.table().nodes().isEmpty());
    }

    @Test
    void aDeviceThatBrokeWhilePlacedStaysBrokenWhenItLeaves() {
        var registry = registry();
        var light = device(1, "LIGHT");
        register(registry, light, "sala");
        light.changeStatusTo(AssetStatus.BROKEN);

        registry.remove(1L, "Se retira para revisión", 8L, NOW.plusDays(1));

        assertEquals(AssetStatus.BROKEN, light.getAssetStatus());
    }

    @Test
    void aDeviceThatIsNotInTheRegistryCannotBeTakenOut() {
        var registry = registry();
        register(registry, device(1, "LIGHT"), "sala");
        var version = registry.getTableVersion();

        assertThrows(DeviceNotAssigned.class, () -> registry.remove(99L, "Motivo", 8L, NOW));

        assertEquals(version, registry.getTableVersion());
        assertEquals(1, events(registry).size());
    }

    @Test
    void anInvalidRemovalLeavesTheEntryAndTheVersionAsTheyWere() {
        var registry = registry();
        var light = device(1, "LIGHT");
        register(registry, light, "sala");

        assertThrows(IllegalArgumentException.class, () -> registry.remove(1L, " ", 8L, NOW.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> registry.remove(1L, "Motivo", null, NOW.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> registry.remove(1L, "Motivo", 8L, NOW.minusDays(1)));

        assertTrue(registry.hasActiveDevice(1L));
        assertEquals(AssetStatus.IN_USE, light.getAssetStatus());
        assertEquals(1, registry.getTableVersion());
    }

    @Test
    void aDeviceCanBePlacedAgainAfterItLeaves() {
        var registry = registry();
        var light = device(1, "LIGHT");
        register(registry, light, "sala");
        registry.remove(1L, "Mudanza", 8L, NOW.plusDays(1));

        var again = registry.register(light, "cuarto", "Luz de noche", 9L, NOW.plusDays(2), PLAN);

        assertTrue(again.isActive());
        assertEquals("cuarto", registry.table().nodes().get(0).roomKey());
        assertEquals(3, registry.getTableVersion());
    }

    // ---- a registered device changes --------------------------------------------------------------------------------------

    @Test
    void whenARegisteredDeviceChangesTheTableGetsANewVersionWithTheNewData() {
        var registry = registry();
        var light = device(1, "LIGHT");
        register(registry, light, "sala");
        light.update("AA:BB:CC:DD:EE:99", "DOOR");
        light.changeStatusTo(AssetStatus.MAINTENANCE);

        registry.deviceChanged(1L);

        var event = events(registry).get(1);
        assertEquals(Cause.DEVICE_CHANGED, event.cause());
        assertEquals(2, event.table().version());
        var node = event.table().nodes().get(0);
        assertEquals("AA:BB:CC:DD:EE:99", node.macAddress());
        assertEquals(DeviceType.DOOR, node.deviceType());
        assertEquals(AssetStatus.MAINTENANCE, node.status());
    }

    @Test
    void aDeviceOutsideTheRegistryDoesNotChangeItsTable() {
        var registry = registry();
        register(registry, device(1, "LIGHT"), "sala");

        registry.deviceChanged(99L);

        assertEquals(1, registry.getTableVersion());
        assertEquals(1, events(registry).size());
    }
}
