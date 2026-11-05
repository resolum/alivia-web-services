package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** Two requests that change the same asset from the same state cannot both be stored. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:assetversion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class DeviceVersionPersistenceTest {

    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EdgeRepository edges;
    @Autowired
    private EntityManager entityManager;

    @Test
    void theVersionStartsAtZeroAndGrowsWithEveryStoredChange() {
        var device = devices.saveAndFlush(new Device("AA:BB:CC:DD:EE:01", "Luz", "LIGHT", 1L));
        assertEquals(0, device.getVersion());

        device.changeStatusTo(AssetStatus.MAINTENANCE);
        devices.saveAndFlush(device);

        assertEquals(1, device.getVersion());
    }

    @Test
    void theSecondOfTwoChangesMadeFromTheSameStateFailsInsteadOfOverwritingTheFirst() {
        var id = devices.saveAndFlush(new Device("AA:BB:CC:DD:EE:02", "Luz", "LIGHT", 1L)).getId();
        entityManager.clear();
        // `second` was read before the first change was stored, so it still holds the old version.
        var second = devices.findById(id).orElseThrow();
        entityManager.detach(second);
        var first = devices.findById(id).orElseThrow();

        first.changeStatusTo(AssetStatus.MAINTENANCE);
        devices.saveAndFlush(first);
        second.changeStatusTo(AssetStatus.RETIRED);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> devices.saveAndFlush(second));
        entityManager.clear();
        assertEquals(AssetStatus.MAINTENANCE, devices.findById(id).orElseThrow().getAssetStatus());
    }

    @Test
    void edgesAreVersionedToo() {
        var id = edges.saveAndFlush(new Edge(1L, "AA:BB:CC:DD:EE:03", "ACTUATOR_EDGE")).getId();
        entityManager.clear();
        // `second` was read before the first change was stored, so it still holds the old version.
        var second = edges.findById(id).orElseThrow();
        entityManager.detach(second);
        var first = edges.findById(id).orElseThrow();

        first.changeStatusTo(AssetStatus.MAINTENANCE);
        edges.saveAndFlush(first);
        second.changeStatusTo(AssetStatus.BROKEN);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> edges.saveAndFlush(second));
    }
}
