package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.queryservices.HouseholdDeviceRegistryQueryServiceImpl;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeTableVersionByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The table of devices as an edge service reads it: only for an active installation, from a single snapshot. */
class EdgeDeviceTableQueryServiceTest {

    private HouseholdDeviceRegistryRepository registries;
    private HouseholdDeviceRegistryQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        registries = mock(HouseholdDeviceRegistryRepository.class);
        service = new HouseholdDeviceRegistryQueryServiceImpl(registries);
    }

    private static HouseholdDeviceRegistry registryWithALight() {
        var registry = HouseholdDeviceRegistry.open(3L, 5L, 1L);
        var light = new Device("AA:BB:CC:DD:EE:01", "Luz", "LIGHT", 1L);
        ReflectionTestUtils.setField(light, "id", 1L);
        registry.register(light, "sala", "Luz principal", 9L, LocalDateTime.of(2026, 11, 5, 10, 0),
                new ContractedActuators(Map.of(DeviceType.LIGHT, 1)));
        return registry;
    }

    @Test
    void theTableOfTheInstallationWhereTheEdgeIsAssignedCarriesItsVersionAndEntries() {
        when(registries.findByAssignedEdgeId(7L)).thenReturn(Optional.of(registryWithALight()));

        var table = service.handle(new GetEdgeAccessTableByEdgeIdQuery(7L)).orElseThrow();

        assertEquals(3L, table.installationId());
        assertEquals(5L, table.householdId());
        assertEquals(1L, table.version());
        assertEquals(1, table.nodes().size());
        assertEquals("sala", table.nodes().get(0).roomKey());
    }

    @Test
    void thereIsNoTableForAnEdgeWithoutRegistryOrForOneThatIsNotInService() {
        when(registries.findByAssignedEdgeId(8L)).thenReturn(Optional.empty());
        assertTrue(service.handle(new GetEdgeAccessTableByEdgeIdQuery(8L)).isEmpty());

        for (var status : new RegistryStatus[]{RegistryStatus.DEACTIVATED, RegistryStatus.DISMANTLED}) {
            var registry = registryWithALight();
            ReflectionTestUtils.setField(registry, "status", status);
            when(registries.findByAssignedEdgeId(9L)).thenReturn(Optional.of(registry));

            assertTrue(service.handle(new GetEdgeAccessTableByEdgeIdQuery(9L)).isEmpty(), status.name());
        }
    }

    @Test
    void theVersionIsReadWithoutLoadingTheRegistry() {
        when(registries.findActiveTableVersionByAssignedEdgeId(7L)).thenReturn(Optional.of(12L));
        when(registries.findActiveTableVersionByAssignedEdgeId(8L)).thenReturn(Optional.empty());

        assertEquals(Optional.of(12L), service.handle(new GetEdgeTableVersionByEdgeIdQuery(7L)));
        assertTrue(service.handle(new GetEdgeTableVersionByEdgeIdQuery(8L)).isEmpty());

        verify(registries, never()).findByAssignedEdgeId(anyLong());
    }

    @Test
    void theTableIsReadFromOneSnapshotInAReadOnlyTransaction() throws NoSuchMethodException {
        var method = HouseholdDeviceRegistryQueryServiceImpl.class
                .getMethod("handle", GetEdgeAccessTableByEdgeIdQuery.class);
        var transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
    }
}
