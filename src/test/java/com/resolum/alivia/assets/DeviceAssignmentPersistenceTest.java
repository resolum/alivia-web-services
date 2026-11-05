package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** The history of the placements of a device, as it is stored. The active ones are changed through the registry. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:assignments;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class DeviceAssignmentPersistenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 10, 0);

    @Autowired
    private DeviceRepository devices;
    @Autowired
    private DeviceAssignmentRepository assignments;
    @Autowired
    private EntityManager entityManager;

    private int nextMac = 0;

    private Device registered(String type) {
        return devices.saveAndFlush(new Device(String.format("AA:BB:CC:DD:EE:%02X", nextMac++), "Dispositivo", type, 1L));
    }

    @Test
    void storesTheAssignmentWithItsRoomFunctionAndActor() {
        var device = registered("LIGHT");

        var saved = assignments.saveAndFlush(device.assignTo(3L, 5L, "sala", "Luz principal", 9L, NOW));

        var found = assignments.findById(saved.getId()).orElseThrow();
        assertEquals(device.getId(), found.getDevice().getId());
        assertEquals(3L, found.getInstallationId());
        assertEquals(5L, found.getHouseholdId());
        assertEquals("sala", found.getRoomKey());
        assertEquals("Luz principal", found.getFunction());
        assertEquals(9L, found.getAssignedBy());
        assertEquals(NOW, found.getAssignedAt());
        assertTrue(found.isActive());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void storesWhoClosedTheAssignmentWhenAndWhyAndLeavesTheDeviceAvailable() {
        var device = registered("LIGHT");
        var saved = assignments.saveAndFlush(device.assignTo(3L, 5L, "sala", "Luz principal", 9L, NOW));
        saved.unassign("Reemplazo por falla del equipo", 8L, NOW.plusDays(16));
        devices.saveAndFlush(device);
        assignments.saveAndFlush(saved);
        entityManager.clear();

        var found = assignments.findById(saved.getId()).orElseThrow();

        assertFalse(found.isActive());
        assertEquals(NOW.plusDays(16), found.getUnassignedAt());
        assertEquals(8L, found.getUnassignedBy());
        assertEquals("Reemplazo por falla del equipo", found.getUnassignReason());
        assertEquals(3L, found.getInstallationId());
        assertEquals(5L, found.getHouseholdId());
        assertEquals("sala", found.getRoomKey());
        assertEquals("Luz principal", found.getFunction());
        assertEquals(9L, found.getAssignedBy());
        assertEquals(NOW, found.getAssignedAt());
        assertEquals(AssetStatus.AVAILABLE, devices.findById(device.getId()).orElseThrow().getAssetStatus());
    }

    @Test
    void findsTheActiveAssignmentOfADeviceOnlyWhileItIsOpen() {
        var device = registered("LIGHT");
        var saved = assignments.saveAndFlush(device.assignTo(3L, 5L, "sala", "Luz", 9L, NOW));

        assertEquals(saved.getId(), assignments.findActiveByDeviceId(device.getId()).orElseThrow().getId());

        saved.unassign("Mudanza", 8L, NOW.plusDays(1));
        assignments.saveAndFlush(saved);

        assertTrue(assignments.findActiveByDeviceId(device.getId()).isEmpty());
        assertTrue(assignments.findActiveByDeviceId(device.getId() + 100).isEmpty());
    }

    @Test
    void keepsEveryPlacementOfADeviceInItsHistoryMostRecentFirst() {
        var device = registered("LIGHT");
        var first = assignments.saveAndFlush(device.assignTo(3L, 5L, "sala", "Luz", 9L, NOW));
        first.unassign("Mudanza", 8L, NOW.plusDays(10));
        devices.saveAndFlush(device);
        assignments.saveAndFlush(first);
        var second = assignments.saveAndFlush(device.assignTo(4L, 6L, "cuarto", "Luz de noche", 9L, NOW.plusDays(12)));
        devices.saveAndFlush(device);

        var history = assignments.findHistoryByDeviceId(device.getId());

        assertEquals(2, history.size());
        assertEquals(second.getId(), history.get(0).getId());
        assertTrue(history.get(0).isActive());
        assertEquals(first.getId(), history.get(1).getId());
        assertFalse(history.get(1).isActive());
        assertEquals("Mudanza", history.get(1).getUnassignReason());
        assertEquals(6L, history.get(0).getHouseholdId());
        assertEquals(5L, history.get(1).getHouseholdId());
        assertEquals(2, assignments.count());
    }
}
