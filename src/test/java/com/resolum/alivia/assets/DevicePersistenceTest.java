package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** The physical identifier is unique in the database, not only in the command service. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:assets;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class DevicePersistenceTest {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EdgeRepository edges;

    @Test
    void persistsTheCanonicalMacAliasAndStatus() {
        var saved = devices.saveAndFlush(new Device("aa-bb-cc-dd-ee-ff", "Luz de la sala", "LIGHT", 1L));

        var found = devices.findById(saved.getId()).orElseThrow();

        assertEquals(MAC, found.getMacAddress().macAddress());
        assertEquals("Luz de la sala", found.getAlias());
        assertEquals(AssetStatus.AVAILABLE, found.getAssetStatus());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void findsADeviceByAnySpellingOfItsMacAddress() {
        devices.saveAndFlush(new Device(MAC, "Luz", "LIGHT", 1L));

        assertTrue(devices.existsDeviceByMacAddress(new MacAddress("aa:bb:cc:dd:ee:ff")));
        assertTrue(devices.findDeviceByMacAddress(new MacAddress("AA-BB-CC-DD-EE-FF")).isPresent());
        assertFalse(devices.existsDeviceByMacAddress(new MacAddress("AA:BB:CC:DD:EE:00")));
    }

    @Test
    void rejectsADuplicatePhysicalIdEvenWithAnotherFormatOrBusiness() {
        devices.saveAndFlush(new Device(MAC, "Luz", "LIGHT", 1L));

        assertThrows(DataIntegrityViolationException.class,
                () -> devices.saveAndFlush(new Device("aa-bb-cc-dd-ee-ff", "Otra luz", "DOOR", 2L)));
    }

    @Test
    void rejectsADuplicatePhysicalIdAmongEdges() {
        edges.saveAndFlush(new Edge(1L, MAC, "ACTUATOR_EDGE"));

        assertThrows(DataIntegrityViolationException.class,
                () -> edges.saveAndFlush(new Edge(1L, "aa:bb:cc:dd:ee:ff", "MICROPHONE_EDGE")));
    }
}
