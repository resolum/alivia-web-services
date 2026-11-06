package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.queryservices.DeviceQueryServiceImpl;
import com.resolum.alivia.assets.application.internal.queryservices.EdgeQueryServiceImpl;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceInventoryItemByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDevicesQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeInventoryItemByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgesQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceInventoryItem;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeInventoryItem;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The inventory lists as the database answers them: filters, business, order, pages and where each asset is. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:inventoryqueries;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class InventoryQueriesPersistenceTest {

    private static final ContractedActuators PLAN = new ContractedActuators(Map.of(DeviceType.LIGHT, 5, DeviceType.DOOR, 5));

    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EdgeRepository edges;
    @Autowired
    private DeviceAssignmentRepository assignments;
    @Autowired
    private HouseholdDeviceRegistryRepository registries;
    @Autowired
    private EntityManager entityManager;

    private DeviceQueryServiceImpl deviceQueries;
    private EdgeQueryServiceImpl edgeQueries;
    private int sequence = 0;

    @BeforeEach
    void setUp() {
        deviceQueries = new DeviceQueryServiceImpl(devices, assignments);
        edgeQueries = new EdgeQueryServiceImpl(edges, registries);
    }

    private Device device(long business, String type) {
        return devices.saveAndFlush(new Device(String.format("DD:00:00:00:00:%02X", ++sequence), "Dispositivo " + sequence, type, business));
    }

    private Edge edge(long business, String type) {
        return edges.saveAndFlush(new Edge(business, String.format("EE:00:00:00:00:%02X", ++sequence), type));
    }

    private static PageRequest page(int page, int size) {
        return PageRequest.of(page, size, Sort.by("id"));
    }

    // ---------------------------------------------------------------- devices

    @Test
    void theDevicesOfTheBusinessComeInOrderOfIdInPages() {
        var first = device(1, "LIGHT");
        var second = device(1, "DOOR");
        var third = device(1, "LIGHT");
        device(2, "LIGHT"); // another business

        var firstPage = deviceQueries.handle(new GetDevicesQuery(1L, null, null, page(0, 2)));
        var secondPage = deviceQueries.handle(new GetDevicesQuery(1L, null, null, page(1, 2)));

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(List.of(first.getId(), second.getId()), firstPage.map(item -> item.device().getId()).getContent());
        assertEquals(List.of(third.getId()), secondPage.map(item -> item.device().getId()).getContent());
        assertTrue(deviceQueries.handle(new GetDevicesQuery(1L, null, null, page(5, 2))).getContent().isEmpty());
    }

    @Test
    void theDevicesCanBeFilteredByStatusAndByTypeAndByBoth() {
        var light = device(1, "LIGHT");
        var door = device(1, "DOOR");
        var brokenLight = device(1, "LIGHT");
        brokenLight.changeStatusTo(AssetStatus.BROKEN);
        devices.saveAndFlush(brokenLight);

        assertEquals(List.of(brokenLight.getId()), ids(deviceQueries.handle(new GetDevicesQuery(1L, AssetStatus.BROKEN, null, page(0, 10)))));
        assertEquals(List.of(light.getId(), brokenLight.getId()),
                ids(deviceQueries.handle(new GetDevicesQuery(1L, null, DeviceType.LIGHT, page(0, 10)))));
        assertEquals(List.of(door.getId()), ids(deviceQueries.handle(new GetDevicesQuery(1L, AssetStatus.AVAILABLE, DeviceType.DOOR, page(0, 10)))));
        assertTrue(deviceQueries.handle(new GetDevicesQuery(1L, AssetStatus.BROKEN, DeviceType.DOOR, page(0, 10))).isEmpty());
    }

    private static List<Long> ids(org.springframework.data.domain.Page<DeviceInventoryItem> page) {
        return page.map(item -> item.device().getId()).getContent();
    }

    @Test
    void aDeviceShowsTheInstallationWhereItIsAssignedAndNothingWhenItIsNot() {
        var placed = device(1, "LIGHT");
        var free = device(1, "DOOR");
        var registry = registries.saveAndFlush(HouseholdDeviceRegistry.open(30L, 50L, 1L));
        registry.register(devices.findById(placed.getId()).orElseThrow(), "sala", "Luz", 9L, LocalDateTime.of(2026, 11, 5, 10, 0), PLAN);
        registries.saveAndFlush(registry);
        entityManager.clear();

        var page = deviceQueries.handle(new GetDevicesQuery(1L, null, null, page(0, 10)));

        assertEquals(30L, page.getContent().get(0).installationId());
        assertNull(page.getContent().get(1).installationId());
        assertEquals(free.getId(), page.getContent().get(1).device().getId());
        assertEquals(30L, deviceQueries.handle(new GetDeviceInventoryItemByIdQuery(placed.getId())).orElseThrow().installationId());
        assertNull(deviceQueries.handle(new GetDeviceInventoryItemByIdQuery(free.getId())).orElseThrow().installationId());
    }

    @Test
    void aDeviceThatLeftItsInstallationShowsNoneAndTheHistoryIsIgnored() {
        var light = device(1, "LIGHT");
        var registry = registries.saveAndFlush(HouseholdDeviceRegistry.open(30L, 50L, 1L));
        registry.register(devices.findById(light.getId()).orElseThrow(), "sala", "Luz", 9L, LocalDateTime.of(2026, 11, 5, 10, 0), PLAN);
        registries.saveAndFlush(registry);
        registry.remove(light.getId(), "cambio", 9L, LocalDateTime.of(2026, 11, 5, 11, 0));
        registries.saveAndFlush(registry);
        entityManager.clear();

        assertNull(deviceQueries.handle(new GetDeviceInventoryItemByIdQuery(light.getId())).orElseThrow().installationId());
        assertNull(deviceQueries.handle(new GetDevicesQuery(1L, null, null, page(0, 10))).getContent().get(0).installationId());
    }

    @Test
    void anOldAssignmentWithoutItsOwnInstallationShowsTheOneOfItsRegistryAndNeverBreaksTheList() {
        var viaRegistry = device(1, "LIGHT");
        var orphan = device(1, "DOOR");
        var registry = registries.saveAndFlush(HouseholdDeviceRegistry.open(30L, 50L, 1L));
        registry.register(devices.findById(viaRegistry.getId()).orElseThrow(), "sala", "Luz", 9L, LocalDateTime.of(2026, 11, 5, 10, 0), PLAN);
        registry.register(devices.findById(orphan.getId()).orElseThrow(), "puerta", "Puerta", 9L, LocalDateTime.of(2026, 11, 5, 10, 5), PLAN);
        registries.saveAndFlush(registry);
        // What the rows made before the installation was recorded on them look like: no installation of their own, and
        // the second one never got linked to a registry either.
        entityManager.createNativeQuery("update device_assignments set installation_id = null").executeUpdate();
        entityManager.createNativeQuery("update device_assignments set registry_id = null where device_id = :id")
                .setParameter("id", orphan.getId()).executeUpdate();
        entityManager.clear();

        var page = deviceQueries.handle(new GetDevicesQuery(1L, null, null, page(0, 10)));

        assertEquals(30L, page.getContent().get(0).installationId());
        assertNull(page.getContent().get(1).installationId());
        assertEquals(30L, deviceQueries.handle(new GetDeviceInventoryItemByIdQuery(viaRegistry.getId())).orElseThrow().installationId());
    }

    @Test
    void aMissingDeviceIsEmptyAndAnEmptyInventoryIsAnEmptyPage() {
        assertTrue(deviceQueries.handle(new GetDeviceInventoryItemByIdQuery(999L)).isEmpty());

        var empty = deviceQueries.handle(new GetDevicesQuery(1L, null, null, page(0, 10)));
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.getTotalElements());
    }

    // ---------------------------------------------------------------- edges

    @Test
    void theEdgesOfTheBusinessAreFilteredByStatusAndTypeAndShowTheirInstallation() {
        var microphone = edge(1, "MICROPHONE_EDGE");
        var actuator = edge(1, "ACTUATOR_EDGE");
        var spare = edge(1, "ACTUATOR_EDGE");
        edge(2, "ACTUATOR_EDGE"); // another business
        var registry = registries.saveAndFlush(HouseholdDeviceRegistry.open(30L, 50L, 1L));
        registry.placeEdge(edges.findById(microphone.getId()).orElseThrow());
        registry.placeEdge(edges.findById(actuator.getId()).orElseThrow());
        registries.saveAndFlush(registry);
        entityManager.clear();

        var all = edgeQueries.handle(new GetEdgesQuery(1L, null, null, page(0, 10)));
        assertEquals(3, all.getTotalElements());
        assertEquals(List.of(30L, 30L), all.getContent().stream().limit(2).map(EdgeInventoryItem::installationId).toList());
        assertNull(all.getContent().get(2).installationId());

        assertEquals(List.of(spare.getId()), edgeQueries.handle(new GetEdgesQuery(1L, AssetStatus.AVAILABLE, EdgeType.ACTUATOR_EDGE, page(0, 10)))
                .map(item -> item.edge().getId()).getContent());
        assertEquals(List.of(microphone.getId()), edgeQueries.handle(new GetEdgesQuery(1L, null, EdgeType.MICROPHONE_EDGE, page(0, 10)))
                .map(item -> item.edge().getId()).getContent());
        assertEquals(2, edgeQueries.handle(new GetEdgesQuery(1L, AssetStatus.IN_USE, null, page(0, 10))).getTotalElements());
        assertEquals(1, edgeQueries.handle(new GetEdgesQuery(1L, null, null, page(1, 2))).getContent().size());
    }

    @Test
    void oneEdgeShowsItsInstallationAndAMissingOneIsEmpty() {
        var actuator = edge(1, "ACTUATOR_EDGE");
        var registry = registries.saveAndFlush(HouseholdDeviceRegistry.open(30L, 50L, 1L));
        registry.placeEdge(edges.findById(actuator.getId()).orElseThrow());
        registries.saveAndFlush(registry);
        entityManager.clear();

        assertEquals(30L, edgeQueries.handle(new GetEdgeInventoryItemByIdQuery(actuator.getId())).orElseThrow().installationId());
        assertTrue(edgeQueries.handle(new GetEdgeInventoryItemByIdQuery(999L)).isEmpty());
    }
}
