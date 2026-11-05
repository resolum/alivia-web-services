package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.DeviceAssignmentCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.commandservices.DeviceCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.eventhandlers.AssetSynchronizationEventHandler;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSynchronizationService;
import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.ChangeDeviceStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateDeviceCommand;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent.Cause;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The real services over a real database: every change of a household's devices reaches the edge services once,
 * after it is committed, with the complete table and a version that grows by one.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED) // the events are delivered when the services commit
@Import({AssetSynchronizationEventHandler.class, DeviceAssignmentCommandServiceImpl.class, DeviceCommandServiceImpl.class,
        ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        // One test makes the transport fail on purpose; the handler would log it as an error.
        "logging.level.com.resolum.alivia.assets.application.internal.eventhandlers=OFF",
        "spring.datasource.url=jdbc:h2:mem:assetsync;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class AssetSynchronizationIntegrationTest {

    @Autowired
    private DeviceAssignmentCommandServiceImpl assignmentService;
    @Autowired
    private DeviceCommandServiceImpl deviceService;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private DeviceAssignmentRepository assignments;
    @Autowired
    private HouseholdDeviceRegistryRepository registries;

    @MockitoBean
    private EdgeSynchronizationService sync;
    @MockitoBean
    private ExternalSubscriptionsService subscriptions;
    @MockitoBean
    private ExternalHouseholdsService households;
    @MockitoBean
    private ExternalBusinessService businesses;

    private int nextMac = 1;

    @BeforeEach
    void openTheHouseholdRegistry() {
        registries.saveAndFlush(HouseholdDeviceRegistry.open(3L, 5L, 1L));
        when(subscriptions.fetchContractedActuators(5L)).thenReturn(Optional.of(
                new ContractedActuators(Map.of(DeviceType.LIGHT, 2, DeviceType.DOOR, 1))));
    }

    @AfterEach
    void cleanDatabase() {
        assignments.deleteAllInBatch();
        registries.deleteAllInBatch();
        devices.deleteAllInBatch();
    }

    private Device registered(String type) {
        return devices.saveAndFlush(new Device(String.format("AA:BB:CC:DD:EE:%02X", nextMac++), "Dispositivo", type, 1L));
    }

    private void assign(Device device, String room) {
        assignmentService.handle(new AssignDeviceToInstallationCommand(3L, device.getId(), room, "Función", 9L,
                CallerAuthority.BUSINESS_ADMINISTRATOR));
    }

    private List<EdgeSyncMessage> sent() {
        var captor = ArgumentCaptor.forClass(EdgeSyncMessage.class);
        verify(sync, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void everyChangeOfTheTableIsAnnouncedOnceAfterTheCommitWithTheNextVersionAndTheWholeTable() {
        var first = registered("LIGHT");
        var second = registered("LIGHT");
        var free = registered("DOOR");

        assign(first, "sala");
        assign(second, "cuarto");
        deviceService.handle(new UpdateDeviceCommand(second.getId(), "AA:BB:CC:DD:EE:99", "LIGHT"));
        deviceService.handle(new ChangeDeviceStatusCommand(second.getId(), "MAINTENANCE"));
        deviceService.handle(new UpdateDeviceCommand(free.getId(), "AA:BB:CC:DD:EE:98", "DOOR")); // not in the household
        assignmentService.handle(new UnassignDeviceCommand(first.getId(), "Mudanza", 8L, CallerAuthority.BUSINESS_ADMINISTRATOR));

        var messages = sent();
        assertEquals(List.of(Cause.DEVICE_ASSIGNED, Cause.DEVICE_ASSIGNED, Cause.DEVICE_CHANGED, Cause.DEVICE_CHANGED,
                Cause.DEVICE_UNASSIGNED), messages.stream().map(EdgeSyncMessage::cause).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), messages.stream().map(message -> message.table().version()).toList());
        assertEquals(List.of(1, 2, 2, 2, 1), messages.stream().map(message -> message.table().nodes().size()).toList());
        assertTrue(messages.stream().allMatch(message -> message.installationId().equals(3L) && message.householdId().equals(5L)));

        var afterMac = messages.get(2).table().nodes().stream().filter(node -> node.deviceId().equals(second.getId())).findFirst().orElseThrow();
        assertEquals("AA:BB:CC:DD:EE:99", afterMac.macAddress());
        var afterStatus = messages.get(3).table().nodes().stream().filter(node -> node.deviceId().equals(second.getId())).findFirst().orElseThrow();
        assertEquals(AssetStatus.MAINTENANCE, afterStatus.status());
        assertEquals(List.of(second.getId()), messages.get(4).table().nodes().stream().map(node -> node.deviceId()).toList());
    }

    @Test
    void theVersionIsStoredSoItSurvivesAndKeepsGrowing() {
        assign(registered("LIGHT"), "sala");
        assign(registered("LIGHT"), "cuarto");

        assertEquals(2, registries.findByInstallationId(new InstallationId(3L)).orElseThrow().getTableVersion());
    }

    @Test
    void aChangeThatTheRulesRejectIsNeitherStoredNorAnnounced() {
        assign(registered("DOOR"), "sala");
        clearInvocations(sync);

        assertThrows(DeviceIncompatibleWithPlan.class, () -> assign(registered("DOOR"), "cuarto"));

        verifyNoInteractions(sync);
        assertEquals(1, registries.findByInstallationId(new InstallationId(3L)).orElseThrow().getTableVersion());
    }

    @Test
    void aDeviceOutsideAnyHouseholdChangesWithoutAnnouncingAnything() {
        var free = registered("LIGHT");

        deviceService.handle(new ChangeDeviceStatusCommand(free.getId(), "MAINTENANCE"));
        deviceService.handle(new UpdateDeviceCommand(free.getId(), "AA:BB:CC:DD:EE:77", "DOOR"));

        verify(sync, never()).publish(any());
    }

    @Test
    void aFailureOfTheTransportDoesNotUndoTheChange() {
        doThrow(new IllegalStateException("broker down")).when(sync).publish(any());
        var light = registered("LIGHT");

        assertDoesNotThrow(() -> assign(light, "sala"));

        assertEquals(AssetStatus.IN_USE, devices.findById(light.getId()).orElseThrow().getAssetStatus());
        assertEquals(1, registries.findByInstallationId(new InstallationId(3L)).orElseThrow().getTableVersion());
    }
}
