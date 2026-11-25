package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.acl.AssetsContextFacadeImpl;
import com.resolum.alivia.assets.application.internal.queryservices.HouseholdDeviceRegistryQueryServiceImpl;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeScopeByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Where an edge service is: its kind, installation, household and the devices placed there. */
class EdgeScopeQueryServiceTest {

    private HouseholdDeviceRegistryRepository registries;
    private HouseholdDeviceRegistryQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        registries = mock(HouseholdDeviceRegistryRepository.class);
        service = new HouseholdDeviceRegistryQueryServiceImpl(registries);
    }

    private static Edge edge(long id, String type) {
        var edge = new Edge(1L, String.format("AA:BB:CC:DD:EE:%02X", id), type);
        ReflectionTestUtils.setField(edge, "id", id);
        return edge;
    }

    private static Device device(long id, String alias, String type) {
        var device = new Device(String.format("AA:BB:CC:DD:FF:%02X", id), alias, type, 1L);
        ReflectionTestUtils.setField(device, "id", id);
        return device;
    }

    private static HouseholdDeviceRegistry registry() {
        var registry = HouseholdDeviceRegistry.open(3L, 5L, 1L);
        registry.placeEdge(edge(7L, "MICROPHONE_EDGE"));
        registry.placeEdge(edge(8L, "ACTUATOR_EDGE"));
        var plan = new ContractedActuators(Map.of(DeviceType.LIGHT, 2, DeviceType.DOOR, 1));
        var at = LocalDateTime.of(2026, 11, 5, 10, 0);
        registry.register(device(1L, "Luz de la sala", "LIGHT"), "sala", "Luz principal", 9L, at, plan);
        registry.register(device(2L, "Puerta principal", "DOOR"), "entrada", "Puerta", 9L, at, plan);
        return registry;
    }

    @Test
    void bothEdgesOfAnInstallationSeeTheSameHomeAndTheirOwnKind() {
        var registry = registry();
        when(registries.findByAssignedEdgeId(7L)).thenReturn(Optional.of(registry));
        when(registries.findByAssignedEdgeId(8L)).thenReturn(Optional.of(registry));

        var microphone = service.handle(new GetEdgeScopeByEdgeIdQuery(7L)).orElseThrow();
        var actuators = service.handle(new GetEdgeScopeByEdgeIdQuery(8L)).orElseThrow();

        assertEquals(EdgeType.MICROPHONE_EDGE, microphone.edgeType());
        assertEquals(EdgeType.ACTUATOR_EDGE, actuators.edgeType());
        assertEquals(3L, microphone.installationId());
        assertEquals(5L, microphone.householdId());
        assertEquals(7L, microphone.edgeId());
        assertEquals(microphone.devices(), actuators.devices());
        assertEquals(2, microphone.devices().size());
        var light = microphone.devices().get(0);
        assertEquals(1L, light.deviceId());
        assertEquals("Luz de la sala", light.alias());
        assertEquals(DeviceType.LIGHT, light.type());
        assertEquals(AssetStatus.IN_USE, light.status());
        assertEquals("sala", light.roomKey());
    }

    @Test
    void aDeviceThatWasTakenOutOfTheInstallationIsNotInItsScope() {
        var registry = registry();
        registry.remove(1L, "reemplazo", 9L, LocalDateTime.of(2026, 11, 6, 10, 0));
        when(registries.findByAssignedEdgeId(7L)).thenReturn(Optional.of(registry));

        var scope = service.handle(new GetEdgeScopeByEdgeIdQuery(7L)).orElseThrow();

        assertEquals(1, scope.devices().size());
        assertEquals(2L, scope.devices().get(0).deviceId());
    }

    @Test
    void thereIsNoScopeForAnEdgeWithoutRegistryOrOneThatIsNotInService() {
        when(registries.findByAssignedEdgeId(9L)).thenReturn(Optional.empty());
        assertTrue(service.handle(new GetEdgeScopeByEdgeIdQuery(9L)).isEmpty());

        for (var status : new RegistryStatus[]{RegistryStatus.DEACTIVATED, RegistryStatus.DISMANTLED}) {
            var registry = registry();
            ReflectionTestUtils.setField(registry, "status", status);
            when(registries.findByAssignedEdgeId(7L)).thenReturn(Optional.of(registry));

            assertTrue(service.handle(new GetEdgeScopeByEdgeIdQuery(7L)).isEmpty(), status.name());
        }
    }

    @Test
    void theFacadeAnswersTheDevicesAndTheHouseholdWithPrimitives() {
        var registry = registry();
        when(registries.findActiveByHouseholdId(5L)).thenReturn(Optional.of(registry));
        when(registries.findActiveByHouseholdId(6L)).thenReturn(Optional.empty());
        when(registries.findActiveHouseholdIdByDeviceId(1L)).thenReturn(Optional.of(5L));
        when(registries.findActiveHouseholdIdByDeviceId(2L)).thenReturn(Optional.empty());
        var facade = new AssetsContextFacadeImpl(mock(DeviceQueryService.class), mock(DeviceCommandService.class),
                mock(EdgeCommandService.class), mock(EdgeQueryService.class),
                mock(HouseholdDeviceRegistryCommandService.class), mock(DeviceAssignmentCommandService.class), service);

        var devices = facade.fetchDevicesOfHousehold(5L);

        assertEquals(2, devices.size());
        assertEquals("LIGHT", devices.get(0).type());
        assertEquals("IN_USE", devices.get(0).status());
        assertEquals("Luz de la sala", devices.get(0).alias());
        assertEquals("sala", devices.get(0).roomKey());
        assertTrue(facade.fetchDevicesOfHousehold(6L).isEmpty());
        assertEquals(Optional.of(5L), facade.fetchHouseholdOfDevice(1L));
        assertEquals(Optional.empty(), facade.fetchHouseholdOfDevice(2L));
    }

    @Test
    void theDevicesOfAHouseholdAreReadFromOneSnapshot() throws Exception {
        var method = HouseholdDeviceRegistryQueryServiceImpl.class.getMethod("handle",
                com.resolum.alivia.assets.domain.model.queries.GetDevicesOfHouseholdQuery.class);

        var transactional = method.getAnnotation(Transactional.class);

        assertTrue(transactional.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
    }

    @Test
    void theRegistryAndItsEntriesAreReadFromOneSnapshot() throws Exception {
        var method = HouseholdDeviceRegistryQueryServiceImpl.class.getMethod("handle", GetEdgeScopeByEdgeIdQuery.class);

        var transactional = method.getAnnotation(Transactional.class);

        assertTrue(transactional.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
    }

    @Test
    void theFacadeAnswersWithPrimitivesAndTheKindsTheCredentialsUse() {
        var registry = registry();
        when(registries.findByAssignedEdgeId(7L)).thenReturn(Optional.of(registry));
        when(registries.findByAssignedEdgeId(8L)).thenReturn(Optional.of(registry));
        when(registries.findByAssignedEdgeId(9L)).thenReturn(Optional.empty());
        var facade = new AssetsContextFacadeImpl(mock(DeviceQueryService.class), mock(DeviceCommandService.class),
                mock(EdgeCommandService.class), mock(EdgeQueryService.class),
                mock(HouseholdDeviceRegistryCommandService.class), mock(DeviceAssignmentCommandService.class), service);

        var microphone = facade.fetchEdgeScope(7L).orElseThrow();

        assertEquals("MICROPHONE", microphone.edgeKind());
        assertEquals("ACTUATOR", facade.fetchEdgeScope(8L).orElseThrow().edgeKind());
        assertEquals(3L, microphone.installationId());
        assertEquals(5L, microphone.householdId());
        assertEquals("LIGHT", microphone.devices().get(0).type());
        assertEquals("IN_USE", microphone.devices().get(0).status());
        assertEquals("Luz de la sala", microphone.devices().get(0).alias());
        assertTrue(facade.fetchEdgeScope(9L).isEmpty());
    }
}
