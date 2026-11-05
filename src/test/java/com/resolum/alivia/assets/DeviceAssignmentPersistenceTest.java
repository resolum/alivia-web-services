package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** The counts that decide whether a device fits the plan only see active assignments. */
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
        assertEquals(5L, found.getHouseholdId());
        assertEquals("sala", found.getRoomKey());
        assertEquals("Luz principal", found.getFunction());
        assertEquals(9L, found.getAssignedBy());
        assertEquals(NOW, found.getAssignedAt());
        assertTrue(found.isActive());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void countsActiveAssignmentsByHouseholdAndDeviceType() {
        assignments.saveAndFlush(registered("LIGHT").assignTo(3L, 5L, "sala", "Luz", 9L, NOW));
        assignments.saveAndFlush(registered("LIGHT").assignTo(3L, 5L, "cuarto", "Luz", 9L, NOW));
        assignments.saveAndFlush(registered("DOOR").assignTo(3L, 5L, "sala", "Puerta", 9L, NOW));
        assignments.saveAndFlush(registered("LIGHT").assignTo(3L, 6L, "sala", "Luz", 9L, NOW));

        assertEquals(2, assignments.countActiveByHouseholdIdAndDeviceType(5L, DeviceType.LIGHT));
        assertEquals(1, assignments.countActiveByHouseholdIdAndDeviceType(5L, DeviceType.DOOR));
        assertEquals(0, assignments.countActiveByHouseholdIdAndDeviceType(5L, DeviceType.WINDOW));
        assertEquals(1, assignments.countActiveByHouseholdIdAndDeviceType(6L, DeviceType.LIGHT));
    }

    @Test
    void countsActiveAssignmentsByRoom() {
        assignments.saveAndFlush(registered("MICROPHONE").assignTo(3L, 5L, "sala", "Escucha", 9L, NOW));
        assignments.saveAndFlush(registered("LIGHT").assignTo(3L, 5L, "sala", "Luz", 9L, NOW));

        assertEquals(1, assignments.countActiveByHouseholdIdAndRoomKeyAndDeviceType(5L, "sala", DeviceType.MICROPHONE));
        assertEquals(0, assignments.countActiveByHouseholdIdAndRoomKeyAndDeviceType(5L, "cuarto", DeviceType.MICROPHONE));
        assertEquals(0, assignments.countActiveByHouseholdIdAndRoomKeyAndDeviceType(6L, "sala", DeviceType.MICROPHONE));
    }

    @Test
    void doesNotCountClosedAssignments() {
        var closed = assignments.saveAndFlush(registered("LIGHT").assignTo(3L, 5L, "sala", "Luz", 9L, NOW));
        ReflectionTestUtils.setField(closed, "unassignedAt", NOW.plusDays(1));
        assignments.saveAndFlush(closed);
        var microphone = assignments.saveAndFlush(registered("MICROPHONE").assignTo(3L, 5L, "sala", "Escucha", 9L, NOW));
        ReflectionTestUtils.setField(microphone, "unassignedAt", NOW.plusDays(1));
        assignments.saveAndFlush(microphone);

        assertFalse(closed.isActive());
        assertEquals(0, assignments.countActiveByHouseholdIdAndDeviceType(5L, DeviceType.LIGHT));
        assertEquals(0, assignments.countActiveByHouseholdIdAndRoomKeyAndDeviceType(5L, "sala", DeviceType.MICROPHONE));
    }
}
