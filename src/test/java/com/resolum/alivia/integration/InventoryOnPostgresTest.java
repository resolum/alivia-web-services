package com.resolum.alivia.integration;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The inventory of devices and edges read through the real server and PostgreSQL: who may read it, the filters and
 * pages, and where each asset is as the installations place and remove them.
 *
 * <p>The database is shared with the other classes, so these tests look for their own assets by id and never
 * assume what else is in the tables.</p>
 */
class InventoryOnPostgresTest extends EdgeTransportTestSupport {

    private static boolean lists(String page, long id) {
        return Pattern.compile("\"id\":" + id + "[,}]").matcher(page).find();
    }

    /** Whether the asset is anywhere in the filtered list: the tables hold whatever the other classes left, so it walks the pages. */
    private boolean listed(String path, long id) throws Exception {
        for (int page = 0; ; page++) {
            var body = call("GET", path + (path.contains("?") ? "&" : "?") + "size=200&page=" + page, admin).body();
            if (lists(body, id)) {
                return true;
            }
            if (page + 1 >= number(body, "totalPages")) {
                return false;
            }
        }
    }

    private static boolean hasInstallation(String item) {
        return Pattern.compile("\"installationId\":\\d+").matcher(item).find();
    }

    @Test
    void onlyAnAdministratorReadsTheInventory() throws Exception {
        for (var path : new String[]{"/api/v1/edges", "/api/v1/edges/1", "/api/v1/devices", "/api/v1/devices/1"}) {
            assertEquals(401, call("GET", path, null).status(), path);
        }
        var e = installationWithEdges();
        // An edge credential is only understood under /api/v1/edge/**; anywhere else it is not a valid token.
        assertEquals(401, call("GET", "/api/v1/edges", e.micCred()).status());
        assertEquals(401, call("GET", "/api/v1/devices", e.actCred()).status());
    }

    @Test
    void anEdgeShowsItsInstallationOnlyWhileItIsPlaced() throws Exception {
        long installation = newInstallation();
        long edge = newEdge("ACTUATOR_EDGE");

        var inInventory = call("GET", "/api/v1/edges/" + edge, admin);
        assertEquals(200, inInventory.status(), inInventory.body());
        assertEquals("ACTUATOR_EDGE", text(inInventory.body(), "edgeType"));
        assertEquals("AVAILABLE", text(inInventory.body(), "status"));
        assertFalse(hasInstallation(inInventory.body()), inInventory.body());

        place(installation, edge);
        var placed = call("GET", "/api/v1/edges/" + edge, admin);
        assertEquals("IN_USE", text(placed.body(), "status"));
        assertEquals(installation, number(placed.body(), "installationId"));
        assertTrue(listed("/api/v1/edges?status=in_use&type=actuator_edge", edge));

        assertEquals(200, call("DELETE", "/api/v1/installations/" + installation + "/edges/" + edge, admin).status());
        var removed = call("GET", "/api/v1/edges/" + edge, admin);
        assertEquals("AVAILABLE", text(removed.body(), "status"));
        assertFalse(hasInstallation(removed.body()), removed.body());
        assertFalse(listed("/api/v1/edges?status=IN_USE", edge));
        assertTrue(listed("/api/v1/edges?status=AVAILABLE", edge));
    }

    @Test
    void theEdgeListFiltersByStatusAndTypeAndComesInPages() throws Exception {
        long microphone = newEdge("MICROPHONE_EDGE");
        long first = newEdge("ACTUATOR_EDGE");
        long second = newEdge("ACTUATOR_EDGE");
        assertEquals(200, call("PUT", "/api/v1/edges/" + second + "/status", admin, "{\"status\":\"MAINTENANCE\"}", null).status());

        assertTrue(listed("/api/v1/edges?type=ACTUATOR_EDGE", first));
        assertTrue(listed("/api/v1/edges?type=ACTUATOR_EDGE", second));
        assertFalse(listed("/api/v1/edges?type=ACTUATOR_EDGE", microphone));
        assertTrue(listed("/api/v1/edges?status=MAINTENANCE&type=ACTUATOR_EDGE", second));
        assertFalse(listed("/api/v1/edges?status=MAINTENANCE&type=ACTUATOR_EDGE", first));

        // One edge per page, in id order, and the totals agree with the pages.
        var page0 = call("GET", "/api/v1/edges?size=1&page=0", admin).body();
        var page1 = call("GET", "/api/v1/edges?size=1&page=1", admin).body();
        assertEquals(1, number(page0, "size"));
        assertEquals(number(page0, "totalElements"), number(page1, "totalElements"));
        assertEquals(number(page0, "totalElements"), number(page0, "totalPages"));
        assertTrue(number(page0, "id") < number(page1, "id"));
        var beyond = call("GET", "/api/v1/edges?size=1&page=" + number(page0, "totalElements"), admin).body();
        assertTrue(beyond.contains("\"content\":[]"), beyond);
    }

    @Test
    void aDeviceShowsTheInstallationWhereItIsAssignedAndTheListFilters() throws Exception {
        long installation = newInstallation();
        var created = call("POST", "/api/v1/devices", admin,
                "{\"macAddress\":\"" + mac("DD:EE:02") + "\",\"alias\":\"Puerta\",\"deviceType\":\"DOOR\"}", null);
        assertEquals(201, created.status(), created.body());
        long door = number(created.body(), "id");
        long light = registerLight(installation);

        var free = call("GET", "/api/v1/devices/" + door, admin);
        assertEquals(200, free.status(), free.body());
        assertEquals("DOOR", text(free.body(), "deviceType"));
        assertEquals("Puerta", text(free.body(), "alias"));
        assertFalse(hasInstallation(free.body()), free.body());

        var assigned = call("GET", "/api/v1/devices/" + light, admin);
        assertEquals(installation, number(assigned.body(), "installationId"));
        assertEquals("IN_USE", text(assigned.body(), "status"));

        assertTrue(listed("/api/v1/devices?type=light&status=in_use", light));
        assertFalse(listed("/api/v1/devices?type=light&status=in_use", door));
        assertTrue(listed("/api/v1/devices?status=AVAILABLE", door));
        assertFalse(listed("/api/v1/devices?status=AVAILABLE", light));
    }

    @Test
    void badFiltersAndPagesAreRejectedAndMissingAssetsAre404() throws Exception {
        assertEquals(400, call("GET", "/api/v1/edges?status=SLEEPING", admin).status());
        assertEquals(400, call("GET", "/api/v1/edges?type=MICROPHONE", admin).status());
        assertEquals(400, call("GET", "/api/v1/devices?type=CAMERA", admin).status());
        assertEquals(400, call("GET", "/api/v1/devices?size=201", admin).status());
        assertEquals(400, call("GET", "/api/v1/devices?size=0", admin).status());
        assertEquals(400, call("GET", "/api/v1/devices?page=-1", admin).status());
        assertEquals(404, call("GET", "/api/v1/edges/999999999", admin).status());
        assertEquals(404, call("GET", "/api/v1/devices/999999999", admin).status());
    }
}
