package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The registry of a household as it is stored: its entries, its history, its locks and its events. */
@DataJpaTest
@RecordApplicationEvents
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        // These tests provoke unique-constraint violations on purpose; Hibernate would log each one as a warning.
        "logging.level.org.hibernate.orm.jdbc.error=OFF",
        "spring.datasource.url=jdbc:h2:mem:registries;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class HouseholdDeviceRegistryPersistenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);
    private static final ContractedActuators PLAN = new ContractedActuators(Map.of(DeviceType.LIGHT, 2));

    @Autowired
    private HouseholdDeviceRegistryRepository registries;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private DeviceAssignmentRepository assignments;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ApplicationEvents applicationEvents;

    private int nextMac = 0;

    private Device registered(String type) {
        return devices.saveAndFlush(new Device(String.format("AA:BB:CC:DD:EE:%02X", nextMac++), "Dispositivo", type, 1L));
    }

    private HouseholdDeviceRegistry opened() {
        return registries.saveAndFlush(HouseholdDeviceRegistry.open(3L, 5L, 1L));
    }

    private void place(HouseholdDeviceRegistry registry, Device device, String room) {
        registry.register(device, room, "Función", 9L, NOW, PLAN);
        devices.saveAndFlush(device);
        registries.saveAndFlush(registry);
    }

    @Test
    void storesTheRegistryWithItsEntriesAndFindsItByInstallation() {
        var registry = opened();
        var light = registered("LIGHT");
        place(registry, light, "sala");
        entityManager.clear();

        var found = registries.findByInstallationId(new InstallationId(3L)).orElseThrow();

        assertEquals(5L, found.getHouseholdId());
        assertEquals(1L, found.getBusinessId().businessId());
        assertEquals(RegistryStatus.ACTIVE, found.getStatus());
        assertEquals(1, found.getTableVersion());
        assertEquals(1, found.getEntries().size());
        var entry = found.getEntries().get(0);
        assertEquals(light.getId(), entry.getDevice().getId());
        assertEquals("sala", entry.getRoomKey());
        assertEquals(3L, entry.getInstallationId());
        assertEquals(found.getId(), entry.getRegistry().getId());
        assertEquals(AssetStatus.IN_USE, entry.getDevice().getAssetStatus());
        assertEquals(1, found.table().nodes().size());
        assertTrue(registries.existsByInstallationId(new InstallationId(3L)));
        assertTrue(registries.existsByHouseholdId(5L));
        assertFalse(registries.existsByHouseholdId(6L));
    }

    @Test
    void allowsOnlyOneRegistryPerHouseholdAndPerInstallation() {
        opened();

        assertThrows(DataIntegrityViolationException.class,
                () -> registries.saveAndFlush(HouseholdDeviceRegistry.open(4L, 5L, 1L)));
    }

    @Test
    void allowsOnlyOneRegistryPerInstallation() {
        opened();

        assertThrows(DataIntegrityViolationException.class,
                () -> registries.saveAndFlush(HouseholdDeviceRegistry.open(3L, 6L, 1L)));
    }

    @Test
    void anEntryThatIsClosedStaysAsHistoryButIsNoLongerLoadedWithTheRegistry() {
        var registry = opened();
        var light = registered("LIGHT");
        place(registry, light, "sala");
        registry.remove(light.getId(), "Mudanza", 8L, NOW.plusDays(1));
        devices.saveAndFlush(light);
        registries.saveAndFlush(registry);
        entityManager.clear();

        var found = registries.findByInstallationId(new InstallationId(3L)).orElseThrow();

        assertTrue(found.getEntries().isEmpty(), "only the active entries are loaded");
        assertTrue(found.table().nodes().isEmpty());
        assertEquals(2, found.getTableVersion());
        var history = assignments.findHistoryByDeviceId(light.getId());
        assertEquals(1, history.size(), "the closed row is still there");
        assertFalse(history.get(0).isActive());
        assertEquals(8L, history.get(0).getUnassignedBy());
        assertEquals("Mudanza", history.get(0).getUnassignReason());
        assertEquals("sala", history.get(0).getRoomKey());
        assertEquals(found.getId(), history.get(0).getRegistry().getId());
    }

    @Test
    void aDevicePlacedAgainKeepsBothRowsInItsHistoryAndOneActiveEntryInTheRegistry() {
        var registry = opened();
        var light = registered("LIGHT");
        place(registry, light, "sala");
        registry.remove(light.getId(), "Mudanza", 8L, NOW.plusDays(1));
        devices.saveAndFlush(light);
        registries.saveAndFlush(registry);
        place(registry, light, "cuarto");
        entityManager.clear();

        var found = registries.findByInstallationId(new InstallationId(3L)).orElseThrow();

        assertEquals(1, found.getEntries().size());
        assertEquals("cuarto", found.getEntries().get(0).getRoomKey());
        assertEquals(2, assignments.findHistoryByDeviceId(light.getId()).size());
        assertEquals(found.getEntries().get(0).getId(), assignments.findActiveByDeviceId(light.getId()).orElseThrow().getId());
        assertEquals(3, found.getTableVersion());
    }

    @Test
    void locksTheRegistryOfAnInstallationAndTheOneThatHoldsADevice() {
        var registry = opened();
        var light = registered("LIGHT");
        var free = registered("LIGHT");
        place(registry, light, "sala");

        assertEquals(registry.getId(), registries.findByInstallationIdForUpdate(new InstallationId(3L)).orElseThrow().getId());
        assertTrue(registries.findByInstallationIdForUpdate(new InstallationId(4L)).isEmpty());
        assertEquals(registry.getId(), registries.findByActiveDeviceIdForUpdate(light.getId()).orElseThrow().getId());
        assertTrue(registries.findByActiveDeviceIdForUpdate(free.getId()).isEmpty());

        registry.remove(light.getId(), "Mudanza", 8L, NOW.plusDays(1));
        devices.saveAndFlush(light);
        registries.saveAndFlush(registry);

        assertTrue(registries.findByActiveDeviceIdForUpdate(light.getId()).isEmpty(), "a closed entry no longer holds the device");
    }

    @Test
    void savingAChangeAnnouncesTheTableOnceAndOpeningAnnouncesNothing() {
        var registry = opened();
        assertEquals(0, applicationEvents.stream(HouseholdDevicesChangedEvent.class).count());
        var light = registered("LIGHT");

        place(registry, light, "sala");

        var events = applicationEvents.stream(HouseholdDevicesChangedEvent.class).toList();
        assertEquals(1, events.size());
        assertEquals(HouseholdDevicesChangedEvent.Cause.DEVICE_ASSIGNED, events.get(0).cause());
        assertEquals(1, events.get(0).table().version());
        assertEquals(light.getId(), events.get(0).table().nodes().get(0).deviceId());
    }

    @Test
    void theEntriesOfDifferentHouseholdsStayApart() {
        var mine = opened();
        var other = registries.saveAndFlush(HouseholdDeviceRegistry.open(4L, 6L, 1L));
        place(mine, registered("LIGHT"), "sala");
        place(other, registered("LIGHT"), "sala");
        place(other, registered("LIGHT"), "cuarto");
        entityManager.clear();

        assertEquals(1, registries.findByInstallationId(new InstallationId(3L)).orElseThrow().getEntries().size());
        assertEquals(2, registries.findByInstallationId(new InstallationId(4L)).orElseThrow().getEntries().size());
    }
}
