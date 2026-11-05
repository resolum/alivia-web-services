package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.RegistryStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** The edge services of an installation as they are stored. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        // These tests provoke unique-constraint violations on purpose; Hibernate would log each one as a warning.
        "logging.level.org.hibernate.orm.jdbc.error=OFF",
        "spring.datasource.url=jdbc:h2:mem:installationedges;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class InstallationEdgesPersistenceTest {

    @Autowired
    private HouseholdDeviceRegistryRepository registries;
    @Autowired
    private EdgeRepository edges;
    @Autowired
    private EntityManager entityManager;

    private Edge edge(int n, String type) {
        return edges.saveAndFlush(new Edge(1L, String.format("AA:BB:CC:DD:EE:%02X", n), type));
    }

    private HouseholdDeviceRegistry registry(long installationId, long householdId) {
        return registries.saveAndFlush(HouseholdDeviceRegistry.open(installationId, householdId, 1L));
    }

    @Test
    void theEdgesOfAnInstallationAreStoredAndFoundFromEitherPlace() {
        var microphone = edge(1, "MICROPHONE_EDGE");
        var actuator = edge(2, "ACTUATOR_EDGE");
        var registry = registry(3, 5);
        registry.placeEdge(microphone);
        registry.placeEdge(actuator);
        registries.saveAndFlush(registry);
        entityManager.clear();

        assertEquals(registry.getId(), registries.findByAssignedEdgeIdForUpdate(microphone.getId()).orElseThrow().getId());
        assertEquals(registry.getId(), registries.findByAssignedEdgeIdForUpdate(actuator.getId()).orElseThrow().getId());
        assertTrue(registries.findByAssignedEdgeIdForUpdate(999L).isEmpty());
        var loaded = registries.findById(registry.getId()).orElseThrow();
        assertEquals(microphone.getId(), loaded.getMicrophoneEdge().getId());
        assertEquals(AssetStatus.IN_USE, loaded.getActuatorEdge().getAssetStatus());
    }

    @Test
    void aRemovedEdgeIsNoLongerFoundAndIsAvailableAgain() {
        var actuator = edge(2, "ACTUATOR_EDGE");
        var registry = registry(3, 5);
        registry.placeEdge(actuator);
        registries.saveAndFlush(registry);

        registry.removeEdge(actuator.getId());
        edges.save(actuator);
        registries.saveAndFlush(registry);
        entityManager.clear();

        assertTrue(registries.findByAssignedEdgeIdForUpdate(actuator.getId()).isEmpty());
        assertEquals(AssetStatus.AVAILABLE, edges.findById(actuator.getId()).orElseThrow().getAssetStatus());
    }

    @Test
    void anEdgeBelongsToOneInstallationOnly() {
        var shared = edge(2, "ACTUATOR_EDGE");
        var first = registry(3, 5);
        first.placeEdge(shared);
        registries.saveAndFlush(first);
        var second = registry(4, 6);
        // Bypass the status rule on purpose: the database must refuse it as well.
        org.springframework.test.util.ReflectionTestUtils.setField(second, "actuatorEdge", shared);

        assertThrows(DataIntegrityViolationException.class, () -> registries.saveAndFlush(second));
    }

    @Test
    void theEdgeIsReadUnderLock() {
        var actuator = edge(2, "ACTUATOR_EDGE");
        entityManager.clear();

        assertTrue(edges.findByIdForUpdate(actuator.getId()).isPresent());
        assertTrue(edges.findByIdForUpdate(999L).isEmpty());
    }

    @Test
    void theTableVersionOfTheActiveInstallationOfAnEdgeIsFoundWithoutLoadingTheRegistry() {
        var microphone = edge(1, "MICROPHONE_EDGE");
        var actuator = edge(2, "ACTUATOR_EDGE");
        var loose = edge(3, "ACTUATOR_EDGE");
        var registry = registry(3, 5);
        registry.placeEdge(microphone);
        registry.placeEdge(actuator);
        org.springframework.test.util.ReflectionTestUtils.setField(registry, "tableVersion", 12L);
        registries.saveAndFlush(registry);
        entityManager.clear();

        assertEquals(12L, registries.findActiveTableVersionByAssignedEdgeId(microphone.getId()).orElseThrow().version());
        assertEquals(3L, registries.findActiveTableVersionByAssignedEdgeId(microphone.getId()).orElseThrow().installationId());
        assertEquals(12L, registries.findActiveTableVersionByAssignedEdgeId(actuator.getId()).orElseThrow().version());
        assertTrue(registries.findActiveTableVersionByAssignedEdgeId(loose.getId()).isEmpty());
        assertEquals(registry.getId(), registries.findByAssignedEdgeId(actuator.getId()).orElseThrow().getId());
        assertTrue(registries.findByAssignedEdgeId(loose.getId()).isEmpty());
    }

    @Test
    void aRegistryThatIsNotInServiceHasNoActiveTableVersion() {
        var actuator = edge(2, "ACTUATOR_EDGE");
        var registry = registry(3, 5);
        registry.placeEdge(actuator);
        org.springframework.test.util.ReflectionTestUtils.setField(registry, "status", RegistryStatus.DEACTIVATED);
        registries.saveAndFlush(registry);
        entityManager.clear();

        assertTrue(registries.findActiveTableVersionByAssignedEdgeId(actuator.getId()).isEmpty());
        // The registry itself is still there; the query service filters it out.
        assertTrue(registries.findByAssignedEdgeId(actuator.getId()).isPresent());
    }
}
