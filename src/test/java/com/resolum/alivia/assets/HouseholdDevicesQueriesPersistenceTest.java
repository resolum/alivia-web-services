package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.queryservices.HouseholdDeviceRegistryQueryServiceImpl;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.queries.GetDevicesOfHouseholdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetHouseholdOfDeviceQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which devices a household has and where a device is, read with the queries Tracking asks through the facade. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:household_devices;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"})
class HouseholdDevicesQueriesPersistenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);
    private static final ContractedActuators PLAN = new ContractedActuators(Map.of(DeviceType.LIGHT, 3, DeviceType.DOOR, 1));

    @Autowired
    private HouseholdDeviceRegistryRepository registries;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EntityManager entityManager;

    private int nextMac = 0;

    private Device registered(String alias, String type) {
        return devices.saveAndFlush(new Device(String.format("AA:BB:CC:DD:EE:%02X", nextMac++), alias, type, 1L));
    }

    private HouseholdDeviceRegistry registry(long installationId, long householdId) {
        return registries.saveAndFlush(HouseholdDeviceRegistry.open(installationId, householdId, 1L));
    }

    private void place(HouseholdDeviceRegistry registry, Device device, String room) {
        registry.register(device, room, "Función", 9L, NOW, PLAN);
        devices.saveAndFlush(device);
        registries.saveAndFlush(registry);
    }

    private HouseholdDeviceRegistryQueryServiceImpl service() {
        return new HouseholdDeviceRegistryQueryServiceImpl(registries);
    }

    @Test
    void theDevicesOfAHouseholdAreTheOnesPlacedInItAndNoOthers() {
        var mine = registry(3L, 5L);
        var other = registry(4L, 6L);
        var light = registered("Luz de la sala", "LIGHT");
        var door = registered("Puerta principal", "DOOR");
        place(mine, light, "sala");
        place(mine, door, "entrada");
        place(other, registered("Luz ajena", "LIGHT"), "cuarto");
        entityManager.clear();

        var devicesOfMine = service().handle(new GetDevicesOfHouseholdQuery(5L));

        assertEquals(2, devicesOfMine.size());
        assertTrue(devicesOfMine.stream().anyMatch(d -> d.deviceId().equals(light.getId())
                && d.alias().equals("Luz de la sala") && d.type() == DeviceType.LIGHT && d.roomKey().equals("sala")));
        assertTrue(devicesOfMine.stream().anyMatch(d -> d.deviceId().equals(door.getId()) && d.roomKey().equals("entrada")));
        assertTrue(service().handle(new GetDevicesOfHouseholdQuery(99L)).isEmpty());
        assertTrue(service().handle(new GetDevicesOfHouseholdQuery(null)).isEmpty());
    }

    @Test
    void aDeviceTakenOutOfTheHouseholdIsNoLongerOfIt() {
        var registry = registry(3L, 5L);
        var light = registered("Luz", "LIGHT");
        place(registry, light, "sala");
        registry.remove(light.getId(), "Mudanza", 8L, NOW.plusDays(1));
        devices.saveAndFlush(light);
        registries.saveAndFlush(registry);
        entityManager.clear();

        assertTrue(service().handle(new GetDevicesOfHouseholdQuery(5L)).isEmpty());
        assertEquals(Optional.empty(), service().handle(new GetHouseholdOfDeviceQuery(light.getId())));
    }

    @Test
    void theHouseholdOfADeviceIsWhereItIsPlacedRightNow() {
        var mine = registry(3L, 5L);
        var other = registry(4L, 6L);
        var light = registered("Luz", "LIGHT");
        var otherLight = registered("Luz ajena", "LIGHT");
        place(mine, light, "sala");
        place(other, otherLight, "cuarto");
        entityManager.clear();

        assertEquals(Optional.of(5L), service().handle(new GetHouseholdOfDeviceQuery(light.getId())));
        assertEquals(Optional.of(6L), service().handle(new GetHouseholdOfDeviceQuery(otherLight.getId())));
        assertEquals(Optional.empty(), service().handle(new GetHouseholdOfDeviceQuery(registered("Sin colocar", "LIGHT").getId())));
        assertEquals(Optional.empty(), service().handle(new GetHouseholdOfDeviceQuery(9999L)));
        assertEquals(Optional.empty(), service().handle(new GetHouseholdOfDeviceQuery(null)));
    }

    @Test
    void aHouseholdWhoseInstallationIsNotActiveShowsNoDevices() {
        var registry = registry(3L, 5L);
        var light = registered("Luz", "LIGHT");
        place(registry, light, "sala");
        for (var status : new RegistryStatus[]{RegistryStatus.DEACTIVATED, RegistryStatus.DISMANTLED}) {
            entityManager.createQuery("update HouseholdDeviceRegistry r set r.status = :status where r.id = :id")
                    .setParameter("status", status).setParameter("id", registry.getId()).executeUpdate();
            entityManager.clear();

            assertTrue(service().handle(new GetDevicesOfHouseholdQuery(5L)).isEmpty(), status.name());
            assertEquals(Optional.empty(), service().handle(new GetHouseholdOfDeviceQuery(light.getId())), status.name());
        }
    }
}
