package com.resolum.alivia.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Who issued and who revoked the credential of an edge, how old it is, and what the API tells about it: through the real
 * server, security chain and PostgreSQL.
 */
class EdgeCredentialAuditOnPostgresTest extends EdgeTransportTestSupport {

    private static String path(long edge) {
        return "/api/v1/edges/" + edge + "/credential";
    }

    private long administrator() {
        return jdbc.queryForObject("select id from users where email = 'super-admin@alivia.test'", Long.class);
    }

    private String status(long edge) throws Exception {
        var response = call("GET", path(edge), admin);
        assertEquals(200, response.status(), response.body());
        return response.body();
    }

    private static boolean has(String body, String field) {
        return body.matches("(?s).*\"" + field + "\":(?!null).*");
    }

    @Test
    void anEdgeWithoutCredentialIsNotIssuedAndAMissingEdgeIs404() throws Exception {
        long edge = newEdge("ACTUATOR_EDGE");

        var body = status(edge);

        assertEquals("NOT_ISSUED", text(body, "state"));
        assertFalse(has(body, "issuedAt"), body);
        assertEquals(404, call("GET", path(999_999_999L), admin).status());
        assertEquals(401, call("GET", path(edge), null).status());
    }

    @Test
    void issuingRecordsWhoAndWhenAndTheStatusNeverShowsTheSecret() throws Exception {
        long edge = newEdge("ACTUATOR_EDGE");
        var secret = issue(edge);

        var body = status(edge);

        assertEquals("ACTIVE", text(body, "state"));
        assertEquals(administrator(), number(body, "issuedBy"));
        assertEquals(0, number(body, "ageDays"));
        assertTrue(body.contains("\"rotationRecommended\":false"), body);
        assertTrue(has(body, "issuedAt"), body);
        assertFalse(has(body, "revokedAt"), body);
        assertFalse(body.contains(secret.substring(secret.indexOf('.') + 1)), "the secret is never shown again");
        assertEquals(administrator(), jdbc.queryForObject("select issued_by from edge_credentials where edge_id = ?", Long.class, edge));
        assertNotNull(jdbc.queryForObject("select issued_at from edge_credentials where edge_id = ?", Object.class, edge));
    }

    @Test
    void anOldSecretIsRecommendedForRotationAndRotatingStartsItsAgeAgain() throws Exception {
        long edge = newEdge("MICROPHONE_EDGE");
        issue(edge);
        jdbc.update("update edge_credentials set issued_at = now() - interval '400 days' where edge_id = ?", edge);

        var old = status(edge);
        assertTrue(number(old, "ageDays") >= 399, old);
        assertTrue(old.contains("\"rotationRecommended\":true"), old);

        issue(edge);
        var rotated = status(edge);
        assertEquals(0, number(rotated, "ageDays"));
        assertTrue(rotated.contains("\"rotationRecommended\":false"), rotated);
    }

    @Test
    void anAdministratorWhoRevokesIsRecordedAndANewSecretForgetsTheRevocation() throws Exception {
        long installation = newInstallation();
        long edge = newEdge("ACTUATOR_EDGE");
        place(installation, edge);
        issue(edge);

        assertEquals(204, call("DELETE", path(edge), admin).status());
        var revoked = status(edge);
        assertEquals("REVOKED", text(revoked, "state"));
        assertEquals("REVOKED_BY_ADMINISTRATOR", text(revoked, "revokedReason"));
        assertEquals(administrator(), number(revoked, "revokedBy"));
        assertTrue(has(revoked, "revokedAt"), revoked);
        assertTrue(revoked.contains("\"rotationRecommended\":false"), "a revoked credential is not worth rotating: " + revoked);

        issue(edge);
        var again = status(edge);
        assertEquals("ACTIVE", text(again, "state"));
        assertFalse(has(again, "revokedAt") || has(again, "revokedBy") || has(again, "revokedReason"), again);
    }

    @Test
    void aChangeOfTheEdgeRevokesWithItsReasonAndNoAdministrator() throws Exception {
        long installation = newInstallation();

        // Taken out of its installation.
        long removed = newEdge("ACTUATOR_EDGE");
        place(installation, removed);
        issue(removed);
        assertEquals(200, call("DELETE", "/api/v1/installations/" + installation + "/edges/" + removed, admin).status());
        assertReason(removed, "EDGE_REMOVED_FROM_INSTALLATION");

        // Replaced by another edge of the same kind.
        long first = newEdge("ACTUATOR_EDGE");
        long second = newEdge("ACTUATOR_EDGE");
        place(installation, first);
        issue(first);
        place(installation, second);
        assertReason(first, "EDGE_REPLACED_IN_INSTALLATION");

        // Retired.
        long retired = newEdge("MICROPHONE_EDGE");
        issue(retired);
        assertEquals(200, call("PUT", "/api/v1/edges/" + retired + "/status", admin, "{\"status\":\"RETIRED\"}", null).status());
        assertReason(retired, "EDGE_RETIRED");

        // Changing the type of an edge that is not placed.
        var mac = mac("AA:BB:07");
        var created = call("POST", "/api/v1/edges", admin, "{\"macAddress\":\"" + mac + "\",\"edgeType\":\"ACTUATOR_EDGE\"}", null);
        long retyped = number(created.body(), "id");
        issue(retyped);
        assertEquals(200, call("PUT", "/api/v1/edges/" + retyped, admin,
                "{\"macAddress\":\"" + mac + "\",\"edgeType\":\"MICROPHONE_EDGE\"}", null).status());
        assertReason(retyped, "EDGE_TYPE_CHANGED");
    }

    private void assertReason(long edge, String reason) throws Exception {
        var body = status(edge);
        assertEquals("REVOKED", text(body, "state"), body);
        assertEquals(reason, text(body, "revokedReason"), body);
        assertFalse(has(body, "revokedBy"), "no administrator revoked it: " + body);
    }
}
