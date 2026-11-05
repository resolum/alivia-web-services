package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToHouseholdCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeviceAssignmentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 10, 0);

    private static Device device() {
        var device = new Device("AA:BB:CC:DD:EE:FF", "Luz de la sala", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        return device;
    }

    private static Device deviceIn(AssetStatus status) {
        var device = device();
        ReflectionTestUtils.setField(device, "assetStatus", status);
        return device;
    }

    @Test
    void assignsAnAvailableDeviceToAHouseholdRoomAndLeavesItInUse() {
        var device = device();

        var assignment = device.assignTo(5L, "  sala ", " Luz principal ", 9L, NOW);

        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        assertSame(device, assignment.getDevice());
        assertEquals(5L, assignment.getHouseholdId());
        assertEquals("sala", assignment.getRoomKey());
        assertEquals("Luz principal", assignment.getFunction());
        assertEquals(9L, assignment.getAssignedBy());
        assertEquals(NOW, assignment.getAssignedAt());
        assertTrue(assignment.isActive());
        assertNull(assignment.getUnassignedAt());
    }

    @ParameterizedTest
    @EnumSource(value = AssetStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryStatusThatIsNotAvailableWithoutChangingTheDevice(AssetStatus status) {
        var device = deviceIn(status);

        var exception = assertThrows(DeviceNotAvailable.class, () -> device.assignTo(5L, "sala", "Luz", 9L, NOW));

        assertEquals(status, exception.getStatus());
        assertEquals(status == AssetStatus.IN_USE, exception.isAlreadyAssigned());
        assertEquals(status, device.getAssetStatus());
    }

    @Test
    void cannotBeAssignedTwiceInARow() {
        var device = device();
        device.assignTo(5L, "sala", "Luz", 9L, NOW);

        var exception = assertThrows(DeviceNotAvailable.class, () -> device.assignTo(6L, "cuarto", "Luz", 9L, NOW));

        assertTrue(exception.isAlreadyAssigned());
    }

    @Test
    void doesNotChangeTheDeviceWhenTheAssignmentDataIsInvalid() {
        var device = device();

        assertThrows(IllegalArgumentException.class, () -> device.assignTo(null, "sala", "Luz", 9L, NOW));
        assertThrows(IllegalArgumentException.class, () -> device.assignTo(0L, "sala", "Luz", 9L, NOW));
        assertThrows(IllegalArgumentException.class, () -> device.assignTo(5L, " ", "Luz", 9L, NOW));
        assertThrows(IllegalArgumentException.class, () -> device.assignTo(5L, "sala", null, 9L, NOW));
        assertThrows(IllegalArgumentException.class, () -> device.assignTo(5L, "a".repeat(61), "Luz", 9L, NOW));
        assertThrows(IllegalArgumentException.class, () -> device.assignTo(5L, "sala", "a".repeat(81), 9L, NOW));
        assertThrows(IllegalArgumentException.class, () -> device.assignTo(5L, "sala", "Luz", null, NOW));
        assertThrows(IllegalArgumentException.class, () -> device.assignTo(5L, "sala", "Luz", 9L, null));

        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        assertTrue(device.isAvailable());
    }

    @Test
    void validatesTheAssignCommand() {
        assertDoesNotThrow(() -> new AssignDeviceToHouseholdCommand(7L, 5L, "sala", "Luz", 9L));
        assertThrows(IllegalArgumentException.class, () -> new AssignDeviceToHouseholdCommand(null, 5L, "sala", "Luz", 9L));
        assertThrows(IllegalArgumentException.class, () -> new AssignDeviceToHouseholdCommand(7L, 0L, "sala", "Luz", 9L));
        assertThrows(IllegalArgumentException.class, () -> new AssignDeviceToHouseholdCommand(7L, 5L, "", "Luz", 9L));
        assertThrows(IllegalArgumentException.class, () -> new AssignDeviceToHouseholdCommand(7L, 5L, "sala", " ", 9L));
        assertThrows(IllegalArgumentException.class, () -> new AssignDeviceToHouseholdCommand(7L, 5L, "sala", "Luz", null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void countsAContractedTypeThatIsMissingAsZero(int lights) {
        var contracted = new ContractedActuators(lights == 0 ? Map.of() : Map.of(DeviceType.LIGHT, lights));

        assertEquals(lights, contracted.quantityOf(DeviceType.LIGHT));
        assertEquals(0, contracted.quantityOf(DeviceType.DOOR));
    }
}
