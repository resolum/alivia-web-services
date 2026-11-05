package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.valueobjects.NetworkStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** The network of an installation, stored with its household and found by installation. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        // These tests provoke unique-constraint violations on purpose; Hibernate would log each one as a warning.
        "logging.level.org.hibernate.orm.jdbc.error=OFF",
        "spring.datasource.url=jdbc:h2:mem:networks;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class DeviceNetworkPersistenceTest {

    @Autowired
    private DeviceNetworkRepository networks;

    @Test
    void storesTheNetworkWithItsHouseholdAndFindsItByInstallation() {
        networks.saveAndFlush(new DeviceNetwork(3L, 5L, 1L));

        var found = networks.findByInstallationId(new InstallationId(3L)).orElseThrow();

        assertEquals(5L, found.getHouseholdId());
        assertEquals(1L, found.getBusinessId().businessId());
        assertEquals(NetworkStatus.ACTIVE, found.getNetworkStatus());
        assertTrue(networks.existsByInstallationId(new InstallationId(3L)));
        assertFalse(networks.existsByInstallationId(new InstallationId(4L)));
        assertTrue(networks.findByInstallationId(new InstallationId(4L)).isEmpty());
    }

    @Test
    void locksTheNetworkOfTheInstallationForUpdate() {
        networks.saveAndFlush(new DeviceNetwork(3L, 5L, 1L));

        assertTrue(networks.findByInstallationIdForUpdate(new InstallationId(3L)).isPresent());
        assertTrue(networks.findByInstallationIdForUpdate(new InstallationId(4L)).isEmpty());
    }

    @Test
    void knowsWhetherAHouseholdHasAnActiveNetwork() {
        networks.saveAndFlush(new DeviceNetwork(3L, 5L, 1L));

        assertTrue(networks.existsByHouseholdIdAndNetworkStatus(5L, NetworkStatus.ACTIVE));
        assertFalse(networks.existsByHouseholdIdAndNetworkStatus(5L, NetworkStatus.DEACTIVATED));
        assertFalse(networks.existsByHouseholdIdAndNetworkStatus(6L, NetworkStatus.ACTIVE));
    }

    @Test
    void allowsOnlyOneNetworkPerInstallation() {
        networks.saveAndFlush(new DeviceNetwork(3L, 5L, 1L));

        assertThrows(DataIntegrityViolationException.class,
                () -> networks.saveAndFlush(new DeviceNetwork(3L, 6L, 1L)));
    }
}
