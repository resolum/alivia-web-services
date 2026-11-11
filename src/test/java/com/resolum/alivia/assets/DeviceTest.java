package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.InvalidAssetStatusTransition;
import com.resolum.alivia.assets.domain.exceptions.InvalidMacAddress;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DeviceTest {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    private static Device device() {
        return new Device(MAC, "Luz de la sala", "LIGHT", 1L);
    }

    @Test
    void registersAnAvailableDeviceWithItsPhysicalIdAliasAndType() {
        var device = device();

        assertEquals(MAC, device.getMacAddress().macAddress());
        assertEquals("Luz de la sala", device.getAlias());
        assertEquals(DeviceType.LIGHT, device.getDeviceType());
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        assertTrue(device.isAvailable());
        assertEquals(1L, device.getBusinessId().businessId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"aa:bb:cc:dd:ee:ff", "AA-BB-CC-DD-EE-FF", "aa-bb-cc-dd-ee-ff", "  Aa:bB:cC:Dd:eE:Ff  "})
    void normalizesEverySpellingOfTheSameMacAddress(String spelling) {
        assertEquals(new MacAddress(MAC), new MacAddress(spelling));
        assertEquals(MAC, new MacAddress(spelling).macAddress());
        assertEquals(MAC, new Device(spelling, "Luz", "LIGHT", 1L).getMacAddress().macAddress());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "AABBCCDDEEFF", "AA:BB:CC:DD:EE", "AA:BB:CC:DD:EE:FF:00",
            "AA:BB-CC:DD-EE:FF", "GG:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:F"})
    void rejectsMalformedMacAddresses(String macAddress) {
        assertThrows(InvalidMacAddress.class, () -> new MacAddress(macAddress));
        assertThrows(InvalidMacAddress.class, () -> new Device(macAddress, "Luz", "LIGHT", 1L));
    }

    @Test
    void rejectsANullMacAddressAsABadRequest() {
        var exception = assertThrows(InvalidMacAddress.class, () -> new MacAddress(null));

        assertInstanceOf(IllegalArgumentException.class, exception);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void requiresAnAlias(String alias) {
        assertThrows(IllegalArgumentException.class, () -> new Device(MAC, alias, "LIGHT", 1L));
        assertThrows(IllegalArgumentException.class, () -> new Device(MAC, null, "LIGHT", 1L));
    }

    @Test
    void trimsTheAliasAndLimitsItsLength() {
        assertEquals("Puerta principal", new Device(MAC, "  Puerta principal  ", "DOOR", 1L).getAlias());
        assertDoesNotThrow(() -> new Device(MAC, "a".repeat(Device.ALIAS_MAX_LENGTH), "DOOR", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Device(MAC, "a".repeat(Device.ALIAS_MAX_LENGTH + 1), "DOOR", 1L));
    }

    @Test
    void rejectsAnUnknownOrMissingDeviceType() {
        assertThrows(IllegalArgumentException.class, () -> new Device(MAC, "Luz", "TOASTER", 1L));
        assertThrows(IllegalArgumentException.class, () -> new Device(MAC, "Luz", null, 1L));
        assertThrows(IllegalArgumentException.class, () -> new Device(MAC, "Luz", " ", 1L));
        assertEquals(DeviceType.CURTAIN, new Device(MAC, "Cortina", "curtain", 1L).getDeviceType());
    }

    @Test
    void requiresAPositiveBusinessId() {
        assertThrows(IllegalArgumentException.class, () -> new Device(MAC, "Luz", "LIGHT", null));
        assertThrows(IllegalArgumentException.class, () -> new Device(MAC, "Luz", "LIGHT", 0L));
    }

    @Test
    void updateKeepsTheDeviceUnchangedWhenTheNewValuesAreInvalid() {
        var device = device();

        assertThrows(IllegalArgumentException.class, () -> device.update("aa:bb:cc:dd:ee:00", "TOASTER"));
        assertThrows(IllegalArgumentException.class, () -> device.update("not-a-mac", "DOOR"));

        assertEquals(MAC, device.getMacAddress().macAddress());
        assertEquals(DeviceType.LIGHT, device.getDeviceType());
        device.update("aa:bb:cc:dd:ee:00", "door");
        assertEquals("AA:BB:CC:DD:EE:00", device.getMacAddress().macAddress());
        assertEquals(DeviceType.DOOR, device.getDeviceType());
    }

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, IN_USE", "AVAILABLE, MAINTENANCE", "AVAILABLE, BROKEN", "AVAILABLE, RETIRED",
            "IN_USE, AVAILABLE", "IN_USE, MAINTENANCE", "IN_USE, BROKEN",
            "MAINTENANCE, AVAILABLE", "MAINTENANCE, BROKEN", "MAINTENANCE, RETIRED",
            "BROKEN, MAINTENANCE", "BROKEN, RETIRED"})
    void allowsTheValidStatusTransitions(AssetStatus from, AssetStatus to) {
        assertTrue(from.canChangeTo(to));
    }

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, AVAILABLE", "IN_USE, IN_USE", "IN_USE, RETIRED",
            "MAINTENANCE, IN_USE", "BROKEN, AVAILABLE", "BROKEN, IN_USE",
            "RETIRED, AVAILABLE", "RETIRED, IN_USE", "RETIRED, MAINTENANCE", "RETIRED, BROKEN", "RETIRED, RETIRED"})
    void rejectsTheInvalidStatusTransitions(AssetStatus from, AssetStatus to) {
        assertFalse(from.canChangeTo(to));
    }

    @Test
    void changesStatusAlongAValidPathAndStopsAtRetired() {
        var device = device();

        device.changeStatusTo(AssetStatus.IN_USE);
        assertFalse(device.isAvailable());
        device.updateAssetStatus("available");
        device.updateAssetStatus("RETIRED");

        assertEquals(AssetStatus.RETIRED, device.getAssetStatus());
        assertThrows(InvalidAssetStatusTransition.class, () -> device.changeStatusTo(AssetStatus.AVAILABLE));
        assertEquals(AssetStatus.RETIRED, device.getAssetStatus());
    }

    @Test
    void doesNotMutateTheDeviceWhenTheStatusTransitionIsInvalid() {
        var device = device();

        assertThrows(InvalidAssetStatusTransition.class, () -> device.changeStatusTo(AssetStatus.AVAILABLE));
        assertThrows(InvalidAssetStatusTransition.class, () -> device.updateAssetStatus("AVAILABLE"));
        var unknown = assertThrows(IllegalArgumentException.class, () -> device.updateAssetStatus(" assigned "));
        // The caller reads the message: it names the status, not an internal class.
        assertEquals("Unknown asset status: assigned.", unknown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> device.updateAssetStatus(null));
        assertThrows(IllegalArgumentException.class, () -> device.updateAssetStatus(" "));

        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
    }

    @Test
    void describesADuplicateMacAddressWithTheAddress() {
        var exception = new MacAddressAlreadyExists(MAC);

        assertTrue(exception.getMessage().contains(MAC));
    }

    @Test
    void validatesTheRegisterCommand() {
        assertDoesNotThrow(() -> new RegisterDeviceCommand(1L, MAC, "Luz", "LIGHT"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterDeviceCommand(null, MAC, "Luz", "LIGHT"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterDeviceCommand(0L, MAC, "Luz", "LIGHT"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterDeviceCommand(1L, " ", "Luz", "LIGHT"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterDeviceCommand(1L, MAC, null, "LIGHT"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterDeviceCommand(1L, MAC, "Luz", ""));
    }
}
