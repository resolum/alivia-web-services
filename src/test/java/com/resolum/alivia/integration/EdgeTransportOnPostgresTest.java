package com.resolum.alivia.integration;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The transport of the table of devices towards the edge services, end to end: an administrator registers and places the
 * edges and gives them credentials, and the edges read, confirm and wait for their table through the real server.
 */
class EdgeTransportOnPostgresTest extends PostgresIntegrationTest {

    private static final ContractedActuators PLAN = new ContractedActuators(Map.of(DeviceType.LIGHT, 5));
    private static final AtomicInteger SEQUENCE = new AtomicInteger(100);
    private static final String TABLE = "/api/v1/edge/device-table";
    private static final String SYNC = "/api/v1/edge/sync-state";

    @Autowired
    private HouseholdDeviceRegistryRepository registries;
    @Autowired
    private EdgeRepository edges;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private IamContextFacade iam;
    @Autowired
    private PlatformTransactionManager transactionManager;

    record Edges(long installation, long mic, long act, String micCred, String actCred) {
    }

    private String mac(String prefix) {
        int n = SEQUENCE.getAndIncrement();
        return String.format("%s:%02X:%02X:%02X", prefix, n >> 16, (n >> 8) & 255, n & 255);
    }

    /** An installation whose two edges were registered by the API, placed by the API and given credentials by the API. */
    private Edges installationWithEdges() throws Exception {
        int n = SEQUENCE.getAndIncrement();
        long installation = 5000L + n;
        registries.saveAndFlush(HouseholdDeviceRegistry.open(installation, 9000L + n, 1L));
        long mic = number(call("POST", "/api/v1/edges", admin,
                "{\"macAddress\":\"" + mac("AA:BB:01") + "\",\"edgeType\":\"MICROPHONE_EDGE\"}", null).body(), "id");
        long act = number(call("POST", "/api/v1/edges", admin,
                "{\"macAddress\":\"" + mac("AA:BB:02") + "\",\"edgeType\":\"ACTUATOR_EDGE\"}", null).body(), "id");
        assertEquals(200, call("PUT", "/api/v1/installations/" + installation + "/edges/" + mic, admin).status());
        assertEquals(200, call("PUT", "/api/v1/installations/" + installation + "/edges/" + act, admin).status());
        var micCred = text(call("POST", "/api/v1/edges/" + mic + "/credential", admin).body(), "credential");
        var actCred = text(call("POST", "/api/v1/edges/" + act + "/credential", admin).body(), "credential");
        return new Edges(installation, mic, act, micCred, actCred);
    }

    private long registerLight(long installation) {
        var light = devices.saveAndFlush(new Device(mac("DD:EE:01"), "Luz", "LIGHT", 1L));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var registry = registries.findByInstallationIdForUpdate(new InstallationId(installation)).orElseThrow();
            registry.register(devices.findById(light.getId()).orElseThrow(), "sala", "Luz principal", 9L,
                    LocalDateTime.now(), PLAN);
            registries.saveAndFlush(registry);
        });
        return light.getId();
    }

    @Test
    void anEdgeRegisteredInTheInventoryIsOnlyAssignedWhenAnInstallationPlacesIt() throws Exception {
        long installation = 7000L + SEQUENCE.getAndIncrement();
        registries.saveAndFlush(HouseholdDeviceRegistry.open(installation, installation + 1000, 1L));

        var registered = call("POST", "/api/v1/edges", admin,
                "{\"macAddress\":\"" + mac("AA:BB:03") + "\",\"edgeType\":\"ACTUATOR_EDGE\"}", null);
        assertEquals(201, registered.status(), registered.body());
        long edge = number(registered.body(), "id");
        assertEquals("AVAILABLE", text(registered.body(), "status"));
        assertEquals(0, jdbc.queryForObject("select count(*) from household_device_registries where actuator_edge = ? "
                + "or microphone_edge = ?", Integer.class, edge, edge));

        // IN_USE is reached only by placing it.
        assertEquals(400, call("PUT", "/api/v1/edges/" + edge + "/status", admin, "{\"status\":\"IN_USE\"}", null).status());
        assertEquals(200, call("PUT", "/api/v1/installations/" + installation + "/edges/" + edge, admin).status());
        assertEquals("IN_USE", jdbc.queryForObject("select asset_status from edges where id = ?", String.class, edge));
        // While placed it cannot be made available or retired, only broken or in maintenance.
        assertEquals(409, call("PUT", "/api/v1/edges/" + edge + "/status", admin, "{\"status\":\"RETIRED\"}", null).status());
        assertEquals(200, call("PUT", "/api/v1/edges/" + edge + "/status", admin, "{\"status\":\"BROKEN\"}", null).status());
        // Removing it returns it to the inventory (it stays broken) and takes its credential away.
        var credential = text(call("POST", "/api/v1/edges/" + edge + "/credential", admin).body(), "credential");
        assertEquals(404, call("GET", "/api/v1/edge/anything", credential).status());
        assertEquals(200, call("DELETE", "/api/v1/installations/" + installation + "/edges/" + edge, admin).status());
        assertEquals(401, call("GET", "/api/v1/edge/anything", credential).status());
        assertEquals("BROKEN", jdbc.queryForObject("select asset_status from edges where id = ?", String.class, edge));
    }

    @Test
    void theEdgeReadsItsTableConfirmsItAndTheBusinessSeesHowFarBehindItIs() throws Exception {
        var e = installationWithEdges();

        // Who may read it.
        assertEquals(401, call("GET", TABLE, null).status());
        assertEquals(401, call("GET", TABLE, e.micCred() + "x").status());
        assertEquals(403, call("GET", TABLE, admin).status());

        var first = call("GET", TABLE, e.micCred());
        assertEquals(200, first.status());
        assertEquals("\"0\"", first.etag());
        assertEquals(e.installation(), number(first.body(), "installationId"));
        assertEquals(304, call("GET", TABLE, e.micCred(), null, "\"0\"").status());

        registerLight(e.installation());
        var second = call("GET", TABLE, e.actCred(), null, "\"0\"");
        assertEquals(200, second.status());
        assertEquals("\"1\"", second.etag());
        assertTrue(second.body().contains("\"function\":\"Luz principal\""), second.body());
        assertEquals(second.body(), call("GET", TABLE, e.micCred()).body(), "both edges receive the same table");

        // Confirmations only grow, never pass the table, and are idempotent.
        assertEquals(1, number(call("PUT", SYNC, e.micCred(), "{\"appliedVersion\":1}", null).body(), "appliedVersion"));
        assertEquals(1, number(call("PUT", SYNC, e.micCred(), "{\"appliedVersion\":0}", null).body(), "appliedVersion"));
        assertEquals(422, call("PUT", SYNC, e.micCred(), "{\"appliedVersion\":2}", null).status());
        assertEquals(400, call("PUT", SYNC, e.micCred(), "{\"appliedVersion\":-1}", null).status());
        assertEquals(403, call("PUT", SYNC, admin, "{\"appliedVersion\":1}", null).status());

        var view = call("GET", "/api/v1/installations/" + e.installation() + "/edge-sync", admin);
        assertEquals(200, view.status());
        assertEquals(1, number(view.body(), "currentVersion"));
        assertTrue(view.body().contains("\"upToDate\":true"), view.body());
        assertTrue(view.body().contains("\"upToDate\":false"), view.body());

        // Taking an edge out forgets what it confirmed; a revoked credential stops working at once.
        assertEquals(204, call("DELETE", "/api/v1/edges/" + e.act() + "/credential", admin).status());
        assertEquals(401, call("GET", TABLE, e.actCred()).status());
        assertEquals(200, call("DELETE", "/api/v1/installations/" + e.installation() + "/edges/" + e.mic(), admin).status());
        assertEquals(0, jdbc.queryForObject("select count(*) from edge_sync_states where edge_id = ?", Integer.class, e.mic()));
    }

    @Test
    void anEdgeThatIsUpToDateWaitsAndIsWokenByACommittedChange() throws Exception {
        var e = installationWithEdges();
        var waiting = callAsync(TABLE + "?wait=20", e.micCred(), "\"0\"");
        Thread.sleep(800);
        assertFalse(waiting.isDone(), "held while nothing changes");

        long start = System.nanoTime();
        registerLight(e.installation());
        var response = waiting.get(10, TimeUnit.SECONDS);
        long millis = (System.nanoTime() - start) / 1_000_000;

        // The answer of a request that waited goes through the security chain again and must still be authenticated.
        assertEquals(200, response.statusCode());
        assertEquals("\"1\"", response.headers().firstValue("ETag").orElseThrow());
        assertTrue(millis < 2000, "woken by the event, not by the next poll: " + millis + " ms");
    }

    @Test
    void aChangeMadeByAnotherInstanceIsFoundByThePollAndTheTimeLimitAnswers304() throws Exception {
        var e = installationWithEdges();
        var waiting = callAsync(TABLE + "?wait=20", e.actCred(), "\"0\"");
        Thread.sleep(400);

        // No event is published: this is what another instance of the application would do.
        jdbc.update("update household_device_registries set table_version = 3 where installation_id = ?", e.installation());
        var woken = waiting.get(10, TimeUnit.SECONDS);
        assertEquals(200, woken.statusCode());
        assertEquals("\"3\"", woken.headers().firstValue("ETag").orElseThrow());

        long start = System.nanoTime();
        var expired = call("GET", TABLE + "?wait=2", e.actCred(), null, "\"3\"");
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(304, expired.status());
        assertEquals("\"3\"", expired.etag());
        assertTrue(millis >= 1900 && millis < 4500, "waited about two seconds: " + millis);

        assertEquals(400, call("GET", TABLE + "?wait=-1", e.actCred(), null, "\"3\"").status());
        assertEquals(403, call("GET", TABLE + "?wait=5", admin, null, "\"3\"").status());
        assertEquals(401, call("GET", TABLE + "?wait=5", null, null, "\"3\"").status());
    }

    @Test
    void manyMoreWaitingEdgesThanServerThreadsAndDatabaseConnectionsDoNotBlockTheServer() throws Exception {
        // 240 waiting requests: more than the 200 threads of the server and far more than the 10 connections of the pool.
        // Each one would hold a connection if the session stayed open while it waits.
        var waits = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        var installations = new ArrayList<Long>();
        for (int i = 0; i < 120; i++) {
            int n = SEQUENCE.getAndIncrement();
            long installation = 20000L + n;
            installations.add(installation);
            var mic = edges.saveAndFlush(new Edge(1L, mac("CC:DD:01"), "MICROPHONE_EDGE"));
            var act = edges.saveAndFlush(new Edge(1L, mac("CC:DD:02"), "ACTUATOR_EDGE"));
            var registry = HouseholdDeviceRegistry.open(installation, 40000L + n, 1L);
            registry.placeEdge(mic);
            registry.placeEdge(act);
            registries.saveAndFlush(registry);
            waits.add(callAsync(TABLE + "?wait=25", iam.issueEdgeCredential(mic.getId(), "MICROPHONE"), "\"0\""));
            waits.add(callAsync(TABLE + "?wait=25", iam.issueEdgeCredential(act.getId(), "ACTUATOR"), "\"0\""));
        }
        Thread.sleep(3000);
        assertEquals(0, waits.stream().filter(CompletableFuture::isDone).count(), "all are held");

        var other = installationWithEdges();
        long start = System.nanoTime();
        var ordinary = call("GET", TABLE, other.micCred());
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(200, ordinary.status());
        assertTrue(millis < 2000, "an ordinary request is answered while 240 wait: " + millis + " ms");

        jdbc.update("update household_device_registries set table_version = 1 where installation_id between ? and ?",
                installations.get(0), installations.get(installations.size() - 1));
        int woken = 0;
        for (var wait : waits) {
            var response = wait.get(20, TimeUnit.SECONDS);
            if (response.statusCode() == 200 && response.headers().firstValue("ETag").orElse("").equals("\"1\"")) {
                woken++;
            }
        }
        assertEquals(waits.size(), woken);
    }

    @Test
    void aDeviceStatusChangedThroughTheApiReachesTheWaitingEdge() throws Exception {
        var e = installationWithEdges();
        long deviceId = registerLight(e.installation());      // version 1: the device is assigned and IN_USE
        assertEquals(400, call("PUT", "/api/v1/devices/" + deviceId + "/status", admin, "{\"status\":\"IN_USE\"}", null).status());
        assertEquals(400, call("PUT", "/api/v1/devices/" + deviceId + "/status", admin, "{\"status\":\"SLEEPING\"}", null).status());
        // An assigned device cannot be made available or retired, only taken to maintenance or broken.
        assertEquals(409, call("PUT", "/api/v1/devices/" + deviceId + "/status", admin, "{\"status\":\"RETIRED\"}", null).status());
        assertEquals(409, call("PUT", "/api/v1/devices/" + deviceId + "/status", admin, "{\"status\":\"AVAILABLE\"}", null).status());
        assertEquals(404, call("PUT", "/api/v1/devices/999999/status", admin, "{\"status\":\"BROKEN\"}", null).status());
        assertEquals(1, number(call("GET", TABLE, e.micCred()).body(), "version"), "nothing was stored by the refused changes");

        var waiting = callAsync(TABLE + "?wait=20", e.actCred(), "\"1\"");
        Thread.sleep(500);
        assertFalse(waiting.isDone());
        long start = System.nanoTime();
        var changed = call("PUT", "/api/v1/devices/" + deviceId + "/status", admin, "{\"status\":\"maintenance\"}", null);
        assertEquals(200, changed.status(), changed.body());
        assertEquals("MAINTENANCE", text(changed.body(), "status"));

        var woken = waiting.get(10, TimeUnit.SECONDS);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(200, woken.statusCode());
        assertEquals("\"2\"", woken.headers().firstValue("ETag").orElseThrow());
        assertTrue(woken.body().contains("\"status\":\"MAINTENANCE\""), woken.body());
        assertTrue(millis < 2000, "the edge learned of the change at once: " + millis + " ms");

        // A device that is not in any household changes status without touching any table.
        var registered = call("POST", "/api/v1/devices", admin,
                "{\"macAddress\":\"" + mac("DD:EE:02") + "\",\"alias\":\"Suelta\",\"deviceType\":\"LIGHT\"}", null);
        long looseId = number(registered.body(), "id");
        assertEquals("BROKEN", text(call("PUT", "/api/v1/devices/" + looseId + "/status", admin, "{\"status\":\"BROKEN\"}", null).body(), "status"));
        assertEquals(2, number(call("GET", TABLE, e.micCred()).body(), "version"));
    }
}
