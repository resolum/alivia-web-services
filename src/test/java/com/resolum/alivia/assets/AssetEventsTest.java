package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.InvalidAssetStatusTransition;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.events.AssetStatusChangedEvent;
import com.resolum.alivia.assets.domain.model.events.AssetUpdatedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceAssignedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceUnassignedEvent;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What the inventory announces to the edge services, and what it keeps quiet. */
class AssetEventsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 10, 0);

    private static Device device() {
        var device = new Device("AA:BB:CC:DD:EE:FF", "Luz", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        return device;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> eventsOf(AuditableAbstractAggregateRoot<?> aggregate) {
        return List.copyOf((Collection<Object>) ReflectionTestUtils.invokeMethod(aggregate, "domainEvents"));
    }

    @Test
    void aNewDeviceAnnouncesNothing() {
        assertTrue(eventsOf(device()).isEmpty());
    }

    @Test
    void aStatusChangeAnnouncesThePreviousAndTheNewStatus() {
        var device = device();

        device.changeStatusTo(AssetStatus.MAINTENANCE);
        device.updateAssetStatus("broken");

        var events = eventsOf(device);
        assertEquals(2, events.size());
        var first = assertInstanceOf(AssetStatusChangedEvent.class, events.get(0));
        assertSame(device, first.asset());
        assertEquals(AssetStatus.AVAILABLE, first.previousStatus());
        assertEquals(AssetStatus.MAINTENANCE, first.newStatus());
        var second = assertInstanceOf(AssetStatusChangedEvent.class, events.get(1));
        assertEquals(AssetStatus.MAINTENANCE, second.previousStatus());
        assertEquals(AssetStatus.BROKEN, second.newStatus());
    }

    @Test
    void aRejectedStatusChangeAnnouncesNothing() {
        var device = device();

        assertThrows(InvalidAssetStatusTransition.class, () -> device.changeStatusTo(AssetStatus.AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> device.updateAssetStatus("NOT_A_STATUS"));

        assertTrue(eventsOf(device).isEmpty());
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
    }

    @Test
    void anEdgeAnnouncesItsStatusChangesToo() {
        var edge = new Edge(1L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE");

        edge.updateAssetStatus("MAINTENANCE");

        assertInstanceOf(AssetStatusChangedEvent.class, eventsOf(edge).get(0));
    }

    @Test
    void anUpdateAnnouncesOnlyWhenTheMacOrTheTypeChanged() {
        var device = device();

        device.update("aa-bb-cc-dd-ee-ff", "light");
        assertTrue(eventsOf(device).isEmpty());

        device.update("AA:BB:CC:DD:EE:01", "LIGHT");
        device.update("AA:BB:CC:DD:EE:01", "DOOR");

        var events = eventsOf(device);
        assertEquals(2, events.size());
        assertSame(device, assertInstanceOf(AssetUpdatedEvent.class, events.get(0)).asset());
    }

    @Test
    void anInvalidUpdateAnnouncesNothing() {
        var device = device();

        assertThrows(IllegalArgumentException.class, () -> device.update("not-a-mac", "DOOR"));
        assertThrows(IllegalArgumentException.class, () -> device.update("AA:BB:CC:DD:EE:01", "TOASTER"));

        assertTrue(eventsOf(device).isEmpty());
    }

    @Test
    void anEdgeUpdateAnnouncesOnlyWhenSomethingChanged() {
        var edge = new Edge(1L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE");

        edge.update("aa:bb:cc:dd:ee:00", "actuator_edge");
        assertTrue(eventsOf(edge).isEmpty());

        edge.update("AA:BB:CC:DD:EE:00", "MICROPHONE_EDGE");
        assertInstanceOf(AssetUpdatedEvent.class, eventsOf(edge).get(0));
    }

    @Test
    void assigningAnnouncesThePlacementOnceAndNotTheStatusChangeItCauses() {
        var device = device();

        var assignment = device.assignTo(3L, 5L, "sala", "Luz", 9L, NOW);

        var events = eventsOf(assignment);
        assertEquals(1, events.size());
        assertSame(assignment, assertInstanceOf(DeviceAssignedEvent.class, events.get(0)).assignment());
        assertTrue(eventsOf(device).isEmpty());
        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
    }

    @Test
    void aFailedAssignmentAnnouncesNothing() {
        var device = device();
        device.assignTo(3L, 5L, "sala", "Luz", 9L, NOW);

        assertThrows(RuntimeException.class, () -> device.assignTo(3L, 5L, "cuarto", "Luz", 9L, NOW));
        assertThrows(IllegalArgumentException.class, () -> device().assignTo(3L, 5L, " ", "Luz", 9L, NOW));
    }

    @Test
    void unassigningAnnouncesTheClosedAssignmentOnceAndNotTheStatusChangeItCauses() {
        var device = device();
        var assignment = device.assignTo(3L, 5L, "sala", "Luz", 9L, NOW);
        ReflectionTestUtils.invokeMethod(assignment, "clearDomainEvents");

        assignment.unassign("Mudanza", 8L, NOW.plusDays(1));

        var events = eventsOf(assignment);
        assertEquals(1, events.size());
        assertSame(assignment, assertInstanceOf(DeviceUnassignedEvent.class, events.get(0)).assignment());
        assertTrue(eventsOf(device).isEmpty());
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
    }

    @Test
    void aRejectedUnassignmentAnnouncesNothing() {
        var device = device();
        var assignment = device.assignTo(3L, 5L, "sala", "Luz", 9L, NOW);
        ReflectionTestUtils.invokeMethod(assignment, "clearDomainEvents");

        assertThrows(IllegalArgumentException.class, () -> assignment.unassign(" ", 8L, NOW.plusDays(1)));

        assertTrue(eventsOf(assignment).isEmpty());
    }
}
