package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.eventhandlers.AssetSynchronizationEventHandler;
import com.resolum.alivia.assets.application.internal.queryservices.DeviceNetworkQueryServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage.Type;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSynchronizationService;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * The events the aggregates raise reach the handler when the repositories save them, and the edge services
 * are told only once the change is stored.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED) // the events are delivered when the save commits
@Import({AssetSynchronizationEventHandler.class, DeviceNetworkQueryServiceImpl.class, ClockConfiguration.class,
        JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:assetsync;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class AssetSynchronizationIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 10, 0);

    @Autowired
    private DeviceRepository devices;
    @Autowired
    private DeviceAssignmentRepository assignments;
    @Autowired
    private DeviceNetworkRepository networks;

    @MockitoBean
    private EdgeSynchronizationService sync;

    @BeforeEach
    void registerTheInstallation() {
        networks.saveAndFlush(new DeviceNetwork(3L, 5L, 1L));
    }

    @AfterEach
    void cleanDatabase() {
        assignments.deleteAllInBatch();
        devices.deleteAllInBatch();
        networks.deleteAllInBatch();
    }

    private List<EdgeSyncMessage> messages(int expected) {
        var captor = ArgumentCaptor.forClass(EdgeSyncMessage.class);
        verify(sync, times(expected)).publish(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void savingAnAssignmentTellsTheInstallationAndSavingItsClosureTellsItAgain() {
        var device = devices.save(new Device("AA:BB:CC:DD:EE:01", "Luz", "LIGHT", 1L));
        assertEquals(0, mockingDetails(sync).getInvocations().size(), "registering a device announces nothing");

        var assignment = device.assignTo(3L, 5L, "sala", "Luz principal", 9L, NOW);
        devices.save(device);
        assignments.saveAndFlush(assignment);

        var assigned = messages(1).get(0);
        assertEquals(Type.DEVICE_ASSIGNED, assigned.type());
        assertEquals(3L, assigned.installationId());
        assertEquals(device.getId(), assigned.assetId());
        assertEquals("IN_USE", assigned.status());
        assertEquals(List.of(device.getId()), assigned.table().nodes().stream().map(node -> node.deviceId()).toList());

        clearInvocations(sync);
        assignment.unassign("Mudanza", 8L, NOW.plusDays(1));
        devices.save(device);
        assignments.saveAndFlush(assignment);

        var unassigned = messages(1).get(0);
        assertEquals(Type.DEVICE_UNASSIGNED, unassigned.type());
        assertEquals("AVAILABLE", unassigned.status());
        assertEquals(0, unassigned.table().nodes().size(), "the table the edge receives no longer lists the device");
    }

    @Test
    void savingAStatusChangeOfAnAssignedDeviceTellsItsInstallationButAnUnassignedOneTellsNobody() {
        var device = devices.save(new Device("AA:BB:CC:DD:EE:02", "Luz", "LIGHT", 1L));
        device.changeStatusTo(AssetStatus.MAINTENANCE);
        devices.save(device);
        verifyNoInteractions(sync);

        device.changeStatusTo(AssetStatus.AVAILABLE);
        devices.save(device);
        var assignment = device.assignTo(3L, 5L, "sala", "Luz", 9L, NOW);
        devices.save(device);
        assignments.saveAndFlush(assignment);
        clearInvocations(sync);

        device.changeStatusTo(AssetStatus.BROKEN);
        devices.save(device);

        var message = messages(1).get(0);
        assertEquals(Type.ASSET_STATUS_CHANGED, message.type());
        assertEquals(3L, message.installationId());
        assertEquals("BROKEN", message.status());
        assertEquals("BROKEN", message.table().nodes().get(0).status().name());
    }

    @Test
    void anUpdateOfAnAssignedDeviceTellsItsInstallation() {
        var device = devices.save(new Device("AA:BB:CC:DD:EE:03", "Luz", "LIGHT", 1L));
        var assignment = device.assignTo(3L, 5L, "sala", "Luz", 9L, NOW);
        devices.save(device);
        assignments.saveAndFlush(assignment);
        clearInvocations(sync);

        device.update("AA:BB:CC:DD:EE:04", "DOOR");
        devices.saveAndFlush(device);

        var message = messages(1).get(0);
        assertEquals(Type.ASSET_UPDATED, message.type());
        assertEquals("AA:BB:CC:DD:EE:04", message.macAddress());
        assertEquals("DOOR", message.assetType());
    }
}
