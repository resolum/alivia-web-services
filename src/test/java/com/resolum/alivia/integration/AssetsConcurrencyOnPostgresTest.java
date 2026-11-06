package com.resolum.alivia.integration;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.EdgeStillAssigned;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.ChangeDeviceStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.PlaceEdgeInInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The races that H2 cannot show, over a real PostgreSQL: locks taken in one order never wait for each other, a change
 * that loses a race is reported as a conflict instead of overwriting the winner, and a row that two requests create at
 * once is created once. Every outcome other than a success or an expected conflict (a deadlock, a constraint violation,
 * a 500) fails the test.
 */
class AssetsConcurrencyOnPostgresTest extends PostgresIntegrationTest {

    private static final ContractedActuators PLAN = new ContractedActuators(Map.of(DeviceType.LIGHT, 5));
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    @Autowired
    private HouseholdDeviceRegistryRepository registries;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EdgeRepository edges;
    @Autowired
    private DeviceAssignmentCommandService assignments;
    @Autowired
    private DeviceCommandService deviceCommands;
    @Autowired
    private EdgeCommandService edgeCommands;
    @Autowired
    private HouseholdDeviceRegistryCommandService registryCommands;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static String mac(String prefix) {
        int n = SEQUENCE.getAndIncrement();
        return String.format("%s:%02X:%02X:%02X", prefix, n >> 16, (n >> 8) & 255, n & 255);
    }

    private long newRegistry() {
        int n = SEQUENCE.getAndIncrement();
        return registries.saveAndFlush(HouseholdDeviceRegistry.open(60000L + n, 80000L + n, 1L)).getInstallationId().installationId();
    }

    private Edge newEdge(String type) {
        return edges.saveAndFlush(new Edge(1L, mac("EE:FF:01"), type));
    }

    /** Outcome of one task: its name, or the exception class when it failed. */
    private List<String> runAll(List<Callable<String>> tasks) throws Exception {
        var pool = Executors.newFixedThreadPool(tasks.size());
        var start = new CountDownLatch(1);
        var futures = tasks.stream().map(task -> pool.submit(() -> {
            start.await();
            try {
                return task.call();
            } catch (Throwable failure) {
                return failure.getClass().getSimpleName();
            }
        })).toList();
        start.countDown();
        var out = new ArrayList<String>();
        for (var future : futures) {
            out.add(future.get());
        }
        pool.shutdown();
        return out;
    }

    private static final List<String> EXPECTED_CONFLICTS = List.of(ObjectOptimisticLockingFailureException.class.getSimpleName(),
            EdgeNotAvailable.class.getSimpleName(), EdgeStillAssigned.class.getSimpleName(),
            DeviceNotAssigned.class.getSimpleName(), "InvalidAssetStatusTransition", "DeviceStillAssigned");

    private static void assertOnlyExpected(List<String> outcomes, String where) {
        for (var outcome : outcomes) {
            assertTrue(outcome.equals("ok") || EXPECTED_CONFLICTS.contains(outcome), where + ": " + outcomes);
        }
    }

    @Test
    void unassigningAndChangingTheStatusOfTheSameDeviceNeverDeadlock() throws Exception {
        for (int round = 0; round < 25; round++) {
            long installation = newRegistry();
            var light = devices.saveAndFlush(new Device(mac("DD:EE:01"), "Luz", "LIGHT", 1L));
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var registry = registries.findByInstallationIdForUpdate(new com.resolum.alivia.shared.domain.model.valueobjects.InstallationId(installation)).orElseThrow();
                registry.register(devices.findById(light.getId()).orElseThrow(), "sala", "Luz", 9L, LocalDateTime.now(), PLAN);
                registries.saveAndFlush(registry);
            });

            var outcomes = runAll(List.of(
                    () -> { assignments.handle(new UnassignDeviceCommand(light.getId(), "prueba", 9L, CallerAuthority.BUSINESS_ADMINISTRATOR)); return "ok"; },
                    () -> { deviceCommands.handle(new ChangeDeviceStatusCommand(light.getId(), "MAINTENANCE")); return "ok"; }));

            assertOnlyExpected(outcomes, "round " + round);
            var status = jdbc.queryForObject("select asset_status from devices where id = ?", String.class, light.getId());
            var active = jdbc.queryForObject("select count(*) from device_assignments where device_id = ? and unassigned_at is null",
                    Integer.class, light.getId());
            // The device is either still in the household (in use or in maintenance) or free (available or in maintenance),
            // and never both "in use" and without assignment.
            assertFalse("IN_USE".equals(status) && active == 0, "round " + round + ": " + status + " without assignment");
            assertFalse("AVAILABLE".equals(status) && active == 1, "round " + round + ": available but assigned");
        }
    }

    @Test
    void theSameEdgeTakenByManyInstallationsAtOnceHasExactlyOneWinner() throws Exception {
        for (int round = 0; round < 10; round++) {
            var edge = newEdge("ACTUATOR_EDGE");
            var tasks = new ArrayList<Callable<String>>();
            for (int i = 0; i < 6; i++) {
                long installation = newRegistry();
                tasks.add(() -> call("PUT", "/api/v1/installations/" + installation + "/edges/" + edge.getId(), admin).status() == 200
                        ? "ok" : "conflict");
            }
            var outcomes = runAll(tasks);

            assertEquals(1, Collections.frequency(outcomes, "ok"), "round " + round + ": " + outcomes);
            assertEquals(1, jdbc.queryForObject("select count(*) from household_device_registries where actuator_edge = ?",
                    Integer.class, edge.getId()));
            assertEquals("IN_USE", jdbc.queryForObject("select asset_status from edges where id = ?", String.class, edge.getId()));
        }
    }

    @Test
    void placingAnEdgeAndRetiringItAtTheSameTimeNeverLeavesARetiredEdgePlaced() throws Exception {
        // The retirement waits a random few milliseconds, so over the rounds both orders of the race happen.
        for (int round = 0; round < 60; round++) {
            var edge = newEdge("ACTUATOR_EDGE");
            long installation = newRegistry();
            var outcomes = runAll(List.of(
                    () -> { registryCommands.handle(new PlaceEdgeInInstallationCommand(installation, edge.getId())); return "ok"; },
                    () -> {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
                        edgeCommands.handle(new ChangeEdgeStatusCommand(edge.getId(), "RETIRED"));
                        return "ok";
                    }));

            assertOnlyExpected(outcomes, "round " + round);
            var status = jdbc.queryForObject("select asset_status from edges where id = ?", String.class, edge.getId());
            long holders = jdbc.queryForObject("select count(*) from household_device_registries where actuator_edge = ?",
                    Long.class, edge.getId());
            assertTrue(("IN_USE".equals(status) && holders == 1) || ("RETIRED".equals(status) && holders == 0),
                    "round " + round + ": " + status + " held by " + holders + " " + outcomes);
        }
    }

    @Test
    void issuingACredentialAndChangingTheTypeOfTheEdgeNeverLeavesAStaleKind() throws Exception {
        for (int round = 0; round < 25; round++) {
            var edge = newEdge("ACTUATOR_EDGE");
            var outcomes = runAll(List.of(
                    () -> call("POST", "/api/v1/edges/" + edge.getId() + "/credential", admin).status() < 300 ? "ok" : "conflict",
                    () -> { edgeCommands.handle(new UpdateEdgeCommand(edge.getId(), edge.getMacAddress().macAddress(), "MICROPHONE_EDGE")); return "ok"; }));

            assertOnlyExpected(outcomes.stream().map(o -> o.equals("conflict") ? "ok" : o).toList(), "round " + round);
            var type = jdbc.queryForObject("select edge_type from edges where id = ?", String.class, edge.getId());
            var rows = jdbc.queryForList("select edge_kind, revoked_at is not null as revoked from edge_credentials where edge_id = ?",
                    edge.getId());
            var kind = type.startsWith("MICROPHONE") ? "MICROPHONE" : "ACTUATOR";
            assertTrue(rows.isEmpty() || Boolean.TRUE.equals(rows.get(0).get("revoked")) || kind.equals(rows.get(0).get("edge_kind")),
                    "round " + round + ": type " + type + " credential " + rows);
        }
    }

    @Test
    void theFirstConfirmationsOfAnEdgeRaceWithoutErrorsAndLeaveTheHighestVersion() throws Exception {
        for (int round = 0; round < 8; round++) {
            long installation = newRegistry();
            var edge = newEdge("MICROPHONE_EDGE");
            assertEquals(200, call("PUT", "/api/v1/installations/" + installation + "/edges/" + edge.getId(), admin).status());
            var credential = text(call("POST", "/api/v1/edges/" + edge.getId() + "/credential", admin).body(), "credential");
            jdbc.update("update household_device_registries set table_version = 40 where installation_id = ?", installation);

            var versions = new ArrayList<>(List.of(0, 3, 9, 17, 25, 40, 12, 33));
            Collections.shuffle(versions);
            var tasks = new ArrayList<Callable<String>>();
            for (var version : versions) {
                tasks.add(() -> {
                    var response = call("PUT", "/api/v1/edge/sync-state", credential, "{\"appliedVersion\":" + version + "}", null);
                    // Whatever is stored is never lower than what this edge just reported.
                    return response.status() == 200 && number(response.body(), "appliedVersion") >= version ? "ok" : "status " + response.status();
                });
            }
            var outcomes = runAll(tasks);

            assertEquals(Collections.nCopies(8, "ok"), outcomes, "round " + round);
            assertEquals(40L, jdbc.queryForObject("select applied_version from edge_sync_states where edge_id = ?", Long.class, edge.getId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from edge_sync_states where edge_id = ?", Integer.class, edge.getId()));
        }
    }
}
