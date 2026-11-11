package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.EdgeNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/** The edge services of a household: where they go, who leaves and what happens to the table of devices. */
class HouseholdDeviceRegistryEdgesTest {

    private static Edge edge(long id, String type) {
        var edge = new Edge(1L, String.format("AA:BB:CC:DD:EE:%02X", id), type);
        ReflectionTestUtils.setField(edge, "id", id);
        return edge;
    }

    private static HouseholdDeviceRegistry registry() {
        return HouseholdDeviceRegistry.open(3L, 5L, 1L);
    }

    @SuppressWarnings("unchecked")
    private static int eventCount(HouseholdDeviceRegistry registry) {
        return ((Collection<Object>) ReflectionTestUtils.invokeMethod(registry, "domainEvents")).size();
    }

    @Test
    void eachTypeOfEdgeTakesItsOwnPlaceAndIsLeftInUse() {
        var registry = registry();
        var microphone = edge(1, "MICROPHONE_EDGE");
        var actuator = edge(2, "ACTUATOR_EDGE");

        assertTrue(registry.placeEdge(microphone).isEmpty());
        assertTrue(registry.placeEdge(actuator).isEmpty());

        assertSame(microphone, registry.getMicrophoneEdge());
        assertSame(actuator, registry.getActuatorEdge());
        assertEquals(AssetStatus.IN_USE, microphone.getAssetStatus());
        assertEquals(AssetStatus.IN_USE, actuator.getAssetStatus());
    }

    @Test
    void anEdgeInThePlaceOfItsTypeIsReplacedAndGoesBackToTheInventory() {
        var registry = registry();
        var old = edge(1, "ACTUATOR_EDGE");
        var replacement = edge(2, "ACTUATOR_EDGE");
        registry.placeEdge(old);

        var left = registry.placeEdge(replacement);

        assertSame(old, left.orElseThrow());
        assertSame(replacement, registry.getActuatorEdge());
        assertEquals(AssetStatus.AVAILABLE, old.getAssetStatus());
        assertEquals(AssetStatus.IN_USE, replacement.getAssetStatus());
    }

    @Test
    void anEdgeThatWasBrokenKeepsItsStatusWhenItIsReplaced() {
        var registry = registry();
        var old = edge(1, "ACTUATOR_EDGE");
        registry.placeEdge(old);
        old.changeStatusTo(AssetStatus.BROKEN);

        registry.placeEdge(edge(2, "ACTUATOR_EDGE"));

        assertEquals(AssetStatus.BROKEN, old.getAssetStatus());
    }

    @Test
    void placingTheEdgeThatAlreadyHoldsItsPlaceChangesNothing() {
        var registry = registry();
        var actuator = edge(1, "ACTUATOR_EDGE");
        registry.placeEdge(actuator);

        assertTrue(registry.placeEdge(actuator).isEmpty());

        assertSame(actuator, registry.getActuatorEdge());
        assertEquals(AssetStatus.IN_USE, actuator.getAssetStatus());
    }

    @ParameterizedTest
    @EnumSource(value = AssetStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void onlyAnAvailableEdgeCanBePlacedAndNothingChangesOtherwise(AssetStatus status) {
        var registry = registry();
        var current = edge(1, "ACTUATOR_EDGE");
        registry.placeEdge(current);
        var other = edge(2, "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(other, "assetStatus", status);

        var failure = assertThrows(EdgeNotAvailable.class, () -> registry.placeEdge(other));

        assertEquals(status == AssetStatus.IN_USE, failure.isAlreadyAssigned());
        assertSame(current, registry.getActuatorEdge());
        assertEquals(AssetStatus.IN_USE, current.getAssetStatus());
        assertEquals(status, other.getAssetStatus());
    }

    @ParameterizedTest
    @EnumSource(value = RegistryStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void nothingIsPlacedInARegistryThatIsNotInService(RegistryStatus status) {
        var registry = registry();
        ReflectionTestUtils.setField(registry, "status", status);
        var microphone = edge(1, "MICROPHONE_EDGE");

        assertThrows(InstallationNotActive.class, () -> registry.placeEdge(microphone));

        assertNull(registry.getMicrophoneEdge());
        assertEquals(AssetStatus.AVAILABLE, microphone.getAssetStatus());
    }

    @Test
    void edgesAreNotEntriesOfTheTable() {
        var registry = registry();

        registry.placeEdge(edge(1, "MICROPHONE_EDGE"));
        registry.placeEdge(edge(2, "ACTUATOR_EDGE"));
        registry.placeEdge(edge(3, "ACTUATOR_EDGE"));
        registry.removeEdge(1L);

        assertEquals(0, registry.getTableVersion());
        assertEquals(0, eventCount(registry));
        assertTrue(registry.table().nodes().isEmpty());
    }

    @Test
    void removingAnEdgeLeavesItsPlaceFreeAndTheEdgeAvailable() {
        var registry = registry();
        var microphone = edge(1, "MICROPHONE_EDGE");
        var actuator = edge(2, "ACTUATOR_EDGE");
        registry.placeEdge(microphone);
        registry.placeEdge(actuator);

        var removed = registry.removeEdge(1L);

        assertSame(microphone, removed);
        assertNull(registry.getMicrophoneEdge());
        assertSame(actuator, registry.getActuatorEdge());
        assertEquals(AssetStatus.AVAILABLE, microphone.getAssetStatus());
    }

    @Test
    void removingAnEdgeInMaintenanceKeepsItInMaintenance() {
        var registry = registry();
        var actuator = edge(2, "ACTUATOR_EDGE");
        registry.placeEdge(actuator);
        actuator.changeStatusTo(AssetStatus.MAINTENANCE);

        registry.removeEdge(2L);

        assertEquals(AssetStatus.MAINTENANCE, actuator.getAssetStatus());
    }

    @Test
    void anEdgeOfAnotherInstallationCannotBeRemoved() {
        var registry = registry();
        registry.placeEdge(edge(1, "MICROPHONE_EDGE"));

        assertThrows(EdgeNotAssigned.class, () -> registry.removeEdge(9L));

        assertNotNull(registry.getMicrophoneEdge());
    }

    @Test
    void aDeactivatedRegistryCanStillBeCleanedUp() {
        var registry = registry();
        registry.placeEdge(edge(1, "MICROPHONE_EDGE"));
        ReflectionTestUtils.setField(registry, "status", RegistryStatus.DEACTIVATED);

        assertDoesNotThrow(() -> registry.removeEdge(1L));
    }
}
