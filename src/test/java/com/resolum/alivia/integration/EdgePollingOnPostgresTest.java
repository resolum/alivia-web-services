package com.resolum.alivia.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wake-up of a waiting edge by polling: a change that no event announced (what another instance of the application
 * leaves in the database) is found at the next poll. The poll runs every second here, in its own context, so that these
 * tests do not wait ten minutes and the ones of {@link EdgeTransportOnPostgresTest} still prove the event.
 */
@TestPropertySource(properties = "alivia.assets.edge-sync.poll-interval=PT1S")
class EdgePollingOnPostgresTest extends EdgeTransportTestSupport {

    @Test
    void aChangeMadeByAnotherInstanceIsFoundByThePoll() throws Exception {
        var e = installationWithEdges();
        var waiting = callAsync(TABLE + "?wait=20", e.actCred(), tag(e.installation(), 0));
        Thread.sleep(400);

        // No event is published: this is what another instance of the application would do.
        setVersion(e.installation(), 3);
        var woken = waiting.get(ANSWER_WITHIN_SECONDS, TimeUnit.SECONDS);

        assertEquals(200, woken.statusCode());
        assertEquals(tag(e.installation(), 3), woken.headers().firstValue("ETag").orElseThrow());
    }

    @Test
    void anInstallationThatStopsBeingActiveWhileItsEdgeWaitsIsReported() throws Exception {
        var e = installationWithEdges();
        var waiting = callAsync(TABLE + "?wait=20", e.micCred(), tag(e.installation(), 0));
        Thread.sleep(400);

        jdbc.update("update household_device_registries set status = 'DEACTIVATED' where installation_id = ?", e.installation());
        var response = waiting.get(ANSWER_WITHIN_SECONDS, TimeUnit.SECONDS);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("edge_without_installation"), response.body());
    }

    @Test
    void manyMoreWaitingEdgesThanServerThreadsAndDatabaseConnectionsDoNotBlockTheServer() throws Exception {
        // 240 waiting requests: more than the 200 threads of the server and far more than the 10 connections of the pool.
        // Each one would hold a connection if the session stayed open while it waits.
        var waits = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        var installations = new ArrayList<Long>();
        for (int i = 0; i < 120; i++) {
            long installation = newInstallation();
            long mic = newEdge("MICROPHONE_EDGE");
            long act = newEdge("ACTUATOR_EDGE");
            place(installation, mic);
            place(installation, act);
            installations.add(installation);
            waits.add(callAsync(TABLE + "?wait=25", issue(mic), tag(installation, 0)));
            waits.add(callAsync(TABLE + "?wait=25", issue(act), tag(installation, 0)));
        }
        Thread.sleep(3000);
        assertEquals(0, waits.stream().filter(CompletableFuture::isDone).count(), "all are held");

        var other = installationWithEdges();
        var ordinary = call("GET", TABLE, other.micCred());
        assertEquals(200, ordinary.status(), "an ordinary request is answered while 240 wait");

        for (var installation : installations) {
            setVersion(installation, 1);
        }
        int woken = 0;
        for (int i = 0; i < waits.size(); i++) {
            var response = waits.get(i).get(25, TimeUnit.SECONDS);
            if (response.statusCode() == 200
                    && response.headers().firstValue("ETag").orElse("").equals(tag(installations.get(i / 2), 1))) {
                woken++;
            }
        }
        assertEquals(waits.size(), woken);
    }
}
