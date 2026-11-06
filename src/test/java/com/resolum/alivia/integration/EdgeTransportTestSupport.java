package com.resolum.alivia.integration;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What the tests of the transport towards the edge services share: installations, edges, credentials and devices. */
abstract class EdgeTransportTestSupport extends PostgresIntegrationTest {

    protected static final ContractedActuators PLAN = new ContractedActuators(Map.of(DeviceType.LIGHT, 5));
    protected static final AtomicInteger SEQUENCE = new AtomicInteger(100);
    protected static final String TABLE = "/api/v1/edge/device-table";
    protected static final String SYNC = "/api/v1/edge/sync-state";
    /** Generous on purpose: these limits tell "answered" from "blocked or lost", not a fast machine from a slow one. */
    protected static final long ANSWER_WITHIN_SECONDS = 10;

    @Autowired
    protected HouseholdDeviceRegistryRepository registries;
    @Autowired
    protected EdgeRepository edges;
    @Autowired
    protected DeviceRepository devices;
    @Autowired
    protected IamContextFacade iam;
    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected record Edges(long installation, long mic, long act, String micCred, String actCred) {
    }

    protected static String mac(String prefix) {
        int n = SEQUENCE.getAndIncrement();
        return String.format("%s:%02X:%02X:%02X", prefix, n >> 16, (n >> 8) & 255, n & 255);
    }

    /** The entity tag of the table of an installation at a version, as the header carries it. */
    protected static String tag(long installation, long version) {
        return "\"" + installation + "." + version + "\"";
    }

    protected long newInstallation() {
        int n = SEQUENCE.getAndIncrement();
        return registries.saveAndFlush(HouseholdDeviceRegistry.open(5000L + n, 9000L + n, 1L))
                .getInstallationId().installationId();
    }

    /** An edge registered through the API (it starts available, in the inventory). */
    protected long newEdge(String type) throws Exception {
        var response = call("POST", "/api/v1/edges", admin,
                "{\"macAddress\":\"" + mac("AA:BB:01") + "\",\"edgeType\":\"" + type + "\"}", null);
        assertEquals(201, response.status(), response.body());
        return number(response.body(), "id");
    }

    protected void place(long installation, long edge) throws Exception {
        assertEquals(200, call("PUT", "/api/v1/installations/" + installation + "/edges/" + edge, admin).status());
    }

    /** Issues (or replaces) the credential of an edge through the API and returns the secret. */
    protected String issue(long edge) throws Exception {
        var response = call("POST", "/api/v1/edges/" + edge + "/credential", admin);
        assertEquals(201, response.status(), response.body());
        return text(response.body(), "credential");
    }

    /** An installation whose two edges were registered, placed and given credentials through the API. */
    protected Edges installationWithEdges() throws Exception {
        long installation = newInstallation();
        long mic = newEdge("MICROPHONE_EDGE");
        long act = newEdge("ACTUATOR_EDGE");
        place(installation, mic);
        place(installation, act);
        return new Edges(installation, mic, act, issue(mic), issue(act));
    }

    /** Registers a new light in the household (one new version of its table) and returns its id. */
    protected long registerLight(long installation) {
        var light = devices.saveAndFlush(new Device(mac("DD:EE:01"), "Luz", "LIGHT", 1L));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var registry = registries.findByInstallationIdForUpdate(new InstallationId(installation)).orElseThrow();
            registry.register(devices.findById(light.getId()).orElseThrow(), "sala", "Luz principal", 9L,
                    LocalDateTime.now(), PLAN);
            registries.saveAndFlush(registry);
        });
        return light.getId();
    }

    /** Sets the version of the table directly, without publishing any event (what another instance would leave). */
    protected void setVersion(long installation, long version) {
        jdbc.update("update household_device_registries set table_version = ? where installation_id = ?", version, installation);
    }
}
