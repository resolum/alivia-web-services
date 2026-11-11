package com.resolum.alivia.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The limit of wrong credentials, through the real server. It has a context of its own with a limit of five a minute:
 * once the test source is refused it stays refused for a minute, so this class holds a single test and the other
 * classes run with the limit switched off.
 */
@TestPropertySource(properties = "alivia.iam.edge-credential.max-failed-attempts-per-minute=5")
class EdgeFailedAttemptsOnPostgresTest extends EdgeTransportTestSupport {

    @Test
    void aSourceThatPresentsTooManyWrongCredentialsIsRefusedAndNothingElseIsAffected() throws Exception {
        var e = installationWithEdges();

        // Right credentials and requests without a credential never count, however many there are.
        for (int attempt = 0; attempt < 12; attempt++) {
            assertEquals(200, call("GET", TABLE, e.micCred()).status());
            assertEquals(401, call("GET", TABLE, null).status());
        }
        // Five wrong ones are answered as usual.
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(401, call("GET", TABLE, e.micCred() + "x").status());
        }

        // Now the source is refused before anything is checked: not even its right credential gets through.
        var refused = call("GET", TABLE, e.micCred());
        assertEquals(429, refused.status());
        assertEquals(429, call("GET", TABLE, e.actCred()).status());
        assertEquals(429, call("PUT", SYNC, e.micCred(), "{\"appliedVersion\":0}", null).status());

        // The administrators' API is another door and is not affected.
        assertEquals(200, call("GET", "/api/v1/edges?size=1", admin).status());
        assertEquals(401, call("GET", TABLE, null).status());
    }
}
