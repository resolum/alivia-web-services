package com.resolum.alivia.integration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The transport of the table of devices towards the edge services, end to end: an administrator registers and places the
 * edges and gives them credentials, and the edges read, confirm and wait for their table through the real server.
 *
 * <p>The database is only polled every ten minutes here, so a waiting edge that is woken can only have been woken by
 * the event of the change: no test depends on how fast the machine is, only on whether the answer arrives. The wake-up
 * by polling has its own class ({@link EdgePollingOnPostgresTest}).</p>
 */
class EdgeTransportOnPostgresTest extends EdgeTransportTestSupport {

    @Test
    void anEdgeRegisteredInTheInventoryIsOnlyAssignedWhenAnInstallationPlacesIt() throws Exception {
        long installation = newInstallation();

        var registered = call("POST", "/api/v1/edges", admin,
                "{\"macAddress\":\"" + mac("AA:BB:03") + "\",\"edgeType\":\"ACTUATOR_EDGE\"}", null);
        assertEquals(201, registered.status(), registered.body());
        long edge = number(registered.body(), "id");
        assertEquals("AVAILABLE", text(registered.body(), "status"));
        assertEquals(0, jdbc.queryForObject("select count(*) from household_device_registries where actuator_edge = ? "
                + "or microphone_edge = ?", Integer.class, edge, edge));

        // IN_USE is reached only by placing it.
        assertEquals(400, call("PUT", "/api/v1/edges/" + edge + "/status", admin, "{\"status\":\"IN_USE\"}", null).status());
        place(installation, edge);
        assertEquals("IN_USE", jdbc.queryForObject("select asset_status from edges where id = ?", String.class, edge));
        // While placed it cannot be made available or retired, only broken or in maintenance.
        assertEquals(409, call("PUT", "/api/v1/edges/" + edge + "/status", admin, "{\"status\":\"RETIRED\"}", null).status());
        assertEquals(200, call("PUT", "/api/v1/edges/" + edge + "/status", admin, "{\"status\":\"BROKEN\"}", null).status());
        // Removing it returns it to the inventory (it stays broken) and takes its credential away.
        var credential = issue(edge);
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
        assertEquals(tag(e.installation(), 0), first.etag());
        assertEquals(e.installation(), number(first.body(), "installationId"));
        assertEquals(304, call("GET", TABLE, e.micCred(), null, tag(e.installation(), 0)).status());

        registerLight(e.installation());
        var second = call("GET", TABLE, e.actCred(), null, tag(e.installation(), 0));
        assertEquals(200, second.status());
        assertEquals(tag(e.installation(), 1), second.etag());
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
    void anEdgeThatIsUpToDateWaitsAndIsWokenByTheEventOfACommittedChange() throws Exception {
        var e = installationWithEdges();
        var waiting = callAsync(TABLE + "?wait=20", e.micCred(), tag(e.installation(), 0));
        Thread.sleep(800);
        assertFalse(waiting.isDone(), "held while nothing changes");

        registerLight(e.installation());
        var response = waiting.get(ANSWER_WITHIN_SECONDS, TimeUnit.SECONDS);

        // Only the event can have woken it (the poll is ten minutes), and the answer of a request that waited goes through
        // the security chain again and must still be authenticated.
        assertEquals(200, response.statusCode());
        assertEquals(tag(e.installation(), 1), response.headers().firstValue("ETag").orElseThrow());
    }

    @Test
    void theTimeLimitAnswers304AndTheSecurityRulesHoldForWaitingRequests() throws Exception {
        var e = installationWithEdges();
        setVersion(e.installation(), 3);

        long start = System.nanoTime();
        var expired = call("GET", TABLE + "?wait=2", e.actCred(), null, tag(e.installation(), 3));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(304, expired.status());
        assertEquals(tag(e.installation(), 3), expired.etag());
        assertTrue(millis >= 1900, "it waited the two seconds it was asked to: " + millis + " ms");

        assertEquals(400, call("GET", TABLE + "?wait=-1", e.actCred(), null, tag(e.installation(), 3)).status());
        assertEquals(400, call("GET", TABLE + "?wait=abc", e.actCred(), null, tag(e.installation(), 3)).status());
        assertEquals(403, call("GET", TABLE + "?wait=5", admin, null, tag(e.installation(), 3)).status());
        assertEquals(401, call("GET", TABLE + "?wait=5", null, null, tag(e.installation(), 3)).status());
        assertEquals(401, call("GET", TABLE + "?wait=5", e.actCred() + "x", null, tag(e.installation(), 3)).status());
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

        var waiting = callAsync(TABLE + "?wait=20", e.actCred(), tag(e.installation(), 1));
        Thread.sleep(500);
        assertFalse(waiting.isDone());
        var changed = call("PUT", "/api/v1/devices/" + deviceId + "/status", admin, "{\"status\":\"maintenance\"}", null);
        assertEquals(200, changed.status(), changed.body());
        assertEquals("MAINTENANCE", text(changed.body(), "status"));

        var woken = waiting.get(ANSWER_WITHIN_SECONDS, TimeUnit.SECONDS);
        assertEquals(200, woken.statusCode());
        assertEquals(tag(e.installation(), 2), woken.headers().firstValue("ETag").orElseThrow());
        assertTrue(woken.body().contains("\"status\":\"MAINTENANCE\""), woken.body());

        // A device that is not in any household changes status without touching any table.
        var registered = call("POST", "/api/v1/devices", admin,
                "{\"macAddress\":\"" + mac("DD:EE:02") + "\",\"alias\":\"Suelta\",\"deviceType\":\"LIGHT\"}", null);
        long looseId = number(registered.body(), "id");
        assertEquals("BROKEN", text(call("PUT", "/api/v1/devices/" + looseId + "/status", admin, "{\"status\":\"BROKEN\"}", null).body(), "status"));
        assertEquals(2, number(call("GET", TABLE, e.micCred()).body(), "version"));
    }

    // ---- H-01: the tag carries the installation

    @Test
    void anEdgeMovedToAnotherInstallationAtTheSameVersionNumberGetsTheTableOfItsNewHome() throws Exception {
        long oldHome = newInstallation();
        long newHome = newInstallation();
        setVersion(oldHome, 3);
        setVersion(newHome, 3);
        long edge = newEdge("ACTUATOR_EDGE");
        place(oldHome, edge);
        var atOldHome = call("GET", TABLE, issue(edge));
        assertEquals(tag(oldHome, 3), atOldHome.etag());

        // The same hardware leaves the first home and is installed in the second one.
        assertEquals(200, call("DELETE", "/api/v1/installations/" + oldHome + "/edges/" + edge, admin).status());
        place(newHome, edge);
        var credential = issue(edge);

        var atNewHome = call("GET", TABLE, credential, null, atOldHome.etag());
        assertEquals(200, atNewHome.status(), "the old tag must not be taken for an up-to-date table of the new home");
        assertEquals(newHome, number(atNewHome.body(), "installationId"));
        assertEquals(tag(newHome, 3), atNewHome.etag());
        assertEquals(304, call("GET", TABLE, credential, null, atNewHome.etag()).status());
        // The same holds for the waiting variant.
        assertEquals(200, call("GET", TABLE + "?wait=20", credential, null, atOldHome.etag()).status());
    }

    // ---- H-02: the database went back in time

    @Test
    void anEdgeAheadOfARestoredDatabaseIsToldSoAndResynchronizesInsteadOfLoopingOrStayingStuck() throws Exception {
        var e = installationWithEdges();
        setVersion(e.installation(), 40);
        assertEquals(tag(e.installation(), 40), call("GET", TABLE, e.micCred()).etag());
        assertEquals(40, number(call("PUT", SYNC, e.micCred(), "{\"appliedVersion\":40}", null).body(), "appliedVersion"));

        setVersion(e.installation(), 10); // the database was restored from an older backup

        for (var path : new String[]{TABLE, TABLE + "?wait=20"}) {
            var conflict = call("GET", path, e.micCred(), null, tag(e.installation(), 40));
            assertEquals(409, conflict.status(), path);
            assertTrue(conflict.body().contains("table_version_ahead"), conflict.body());
        }
        // The edge drops its tag and asks again: it takes the table of the server, whose version is lower.
        var resynchronized = call("GET", TABLE, e.micCred());
        assertEquals(200, resynchronized.status());
        assertEquals(tag(e.installation(), 10), resynchronized.etag());
        assertEquals(304, call("GET", TABLE + "?wait=0", e.micCred(), null, resynchronized.etag()).status());

        // What it confirmed before the restore no longer means anything: confirming again from the lower version works.
        assertEquals(422, call("PUT", SYNC, e.micCred(), "{\"appliedVersion\":40}", null).status());
        assertEquals(10, number(call("PUT", SYNC, e.micCred(), "{\"appliedVersion\":10}", null).body(), "appliedVersion"));
        assertEquals(10L, jdbc.queryForObject("select applied_version from edge_sync_states where edge_id = ?", Long.class, e.mic()));
    }

    // ---- H-03: a wait that began with an old credential ends when the credential changes

    @Test
    void revokingTheCredentialEndsTheWaitAtOnceAndTheEdgeNeverReceivesAnotherTable() throws Exception {
        var e = installationWithEdges();
        var waiting = callAsync(TABLE + "?wait=20", e.actCred(), tag(e.installation(), 0));
        Thread.sleep(600);

        assertEquals(204, call("DELETE", "/api/v1/edges/" + e.act() + "/credential", admin).status());
        var ended = waiting.get(ANSWER_WITHIN_SECONDS, TimeUnit.SECONDS);
        assertEquals(304, ended.statusCode(), "the wait ends with nothing to deliver");

        registerLight(e.installation());
        assertEquals(401, call("GET", TABLE, e.actCred()).status());
        assertEquals(401, call("GET", TABLE + "?wait=5", e.actCred(), null, tag(e.installation(), 0)).status());
    }

    @Test
    void replacingTheSecretOrTakingTheEdgeOutOfTheInstallationAlsoEndsItsWait() throws Exception {
        var e = installationWithEdges();

        var withOldSecret = callAsync(TABLE + "?wait=20", e.micCred(), tag(e.installation(), 0));
        Thread.sleep(600);
        var newSecret = issue(e.mic());
        assertEquals(304, withOldSecret.get(ANSWER_WITHIN_SECONDS, TimeUnit.SECONDS).statusCode());
        assertEquals(401, call("GET", TABLE, e.micCred()).status(), "the old secret stopped working");
        assertEquals(200, call("GET", TABLE, newSecret).status());

        var beforeRemoval = callAsync(TABLE + "?wait=20", newSecret, tag(e.installation(), 0));
        Thread.sleep(600);
        assertEquals(200, call("DELETE", "/api/v1/installations/" + e.installation() + "/edges/" + e.mic(), admin).status());
        assertEquals(304, beforeRemoval.get(ANSWER_WITHIN_SECONDS, TimeUnit.SECONDS).statusCode());
        assertEquals(401, call("GET", TABLE, newSecret).status());
    }
}
