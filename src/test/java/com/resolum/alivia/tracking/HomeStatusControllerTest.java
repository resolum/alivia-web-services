package com.resolum.alivia.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade.ScopedDevice;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.queryservices.HomeStatusQueryServiceImpl;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import com.resolum.alivia.tracking.domain.repositories.DeviceStateRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import com.resolum.alivia.tracking.interfaces.rest.controllers.HomeStatusController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /assisted-persons/{personId}/devices/status} with the real controller and service, over the example of
 * {@code docs/fixtures/tracking/q-home-status.response.json}: a home whose actuators edge is not heard.
 */
class HomeStatusControllerTest {

    private static final String PATH = "/api/v1/assisted-persons/2/devices/status";
    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("7", null, List.of());
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final TrackingAccessService access = mock(TrackingAccessService.class);
    private final AssetsContextFacade facade = mock(AssetsContextFacade.class);
    private final DeviceStateRepository states = mock(DeviceStateRepository.class);
    private final EdgeStatusRepository edges = mock(EdgeStatusRepository.class);
    private MockMvc mvc;

    private static DeviceState state(long device, long edge, ConnectionStatus connection, OperationalStatus operational,
                                     String current, Integer battery, String observedAt, List<String> faults) {
        var at = Instant.parse(observedAt);
        return new DeviceState(device, edge, 30L, connection, operational, current, battery, at, at, at.plusSeconds(1),
                faults);
    }

    @BeforeEach
    void setUp() {
        var service = new HomeStatusQueryServiceImpl(access, new ExternalAssetsService(facade), states, edges,
                new SimpleMeterRegistry());
        mvc = MockMvcBuilders.standaloneSetup(new HomeStatusController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(access.requireReadableHouseholdOfPerson(7L, 2L)).thenReturn(30L);
        when(facade.fetchEdgesOfHousehold(30L)).thenReturn(List.of(new AssetsContextFacade.AssignedEdge(2L, "MICROPHONE"),
                new AssetsContextFacade.AssignedEdge(3L, "ACTUATOR")));
        when(facade.fetchDevicesOfHousehold(30L)).thenReturn(List.of(
                new ScopedDevice(10L, "Puerta principal", "DOOR", "IN_USE", "entrada"),
                new ScopedDevice(11L, "Luz de la sala", "LIGHT", "IN_USE", "sala"),
                new ScopedDevice(12L, "Cortina del cuarto", "CURTAIN", "IN_USE", "cuarto"),
                new ScopedDevice(13L, "Micrófono de la sala", "MICROPHONE", "IN_USE", "sala")));
        when(states.findAllByDeviceIds(anyCollection())).thenReturn(List.of(
                state(10L, 3L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", 78,
                        "2026-11-24T14:58:00Z", List.of()),
                state(11L, 3L, ConnectionStatus.CONNECTED, OperationalStatus.DEGRADED, "on", 15,
                        "2026-11-24T14:57:00Z", List.of("LOW_SIGNAL")),
                state(13L, 2L, ConnectionStatus.CONNECTED, OperationalStatus.OK, null, null,
                        "2026-11-24T15:04:49Z", List.of())));
        when(edges.findAllByHouseholdId(30L)).thenReturn(List.of(
                new EdgeStatus(3L, 33L, 30L, Instant.parse("2026-11-24T14:58:30Z"), EdgeLink.OFFLINE, 0,
                        Instant.parse("2026-11-24T15:01:00Z")),
                new EdgeStatus(2L, 32L, 30L, Instant.parse("2026-11-24T15:04:50Z"), EdgeLink.ONLINE, 0, null)));
    }

    @Test
    void theHomeIsAnsweredAsTheContractSays() throws Exception {
        var expected = JSON.readTree(Files.readString(Path.of("docs", "fixtures", "tracking", "q-home-status.response.json")));

        var answer = mvc.perform(get(PATH).principal(USER)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JSON.readTree(answer)).isEqualTo(expected);
    }

    @Test
    void aFieldWithNothingToSayIsAbsentAndNotNull() throws Exception {
        var answer = JSON.readTree(mvc.perform(get(PATH).principal(USER)).andReturn().getResponse().getContentAsString());

        var neverReported = answer.get("devices").get(2);
        assertThat(neverReported.has("operationalStatus")).isFalse();
        assertThat(neverReported.has("batteryPct")).isFalse();
        assertThat(neverReported.has("lastUpdatedAt")).isFalse();
    }

    @Test
    void aHomeThatIsHeardHasNoOfflineSince() throws Exception {
        when(edges.findAllByHouseholdId(30L)).thenReturn(List.of(
                new EdgeStatus(3L, 33L, 30L, Instant.parse("2026-11-24T15:04:50Z"), EdgeLink.ONLINE, 0, null)));

        mvc.perform(get(PATH).principal(USER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.edgeLink").value("ONLINE"))
                .andExpect(jsonPath("$.offlineSince").doesNotExist())
                .andExpect(jsonPath("$.mayHavePendingEvents").value(false))
                .andExpect(jsonPath("$.devices[0].stale").value(false));
    }

    @Test
    void aPersonTheUserCannotReadIsA404AndSoIsOneWhoDoesNotExist() throws Exception {
        when(access.requireReadableHouseholdOfPerson(7L, 2L)).thenThrow(new AssistedPersonNotAccessible(2L));

        mvc.perform(get(PATH).principal(USER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("assisted_person_not_found"))
                .andExpect(jsonPath("$.detail").value("The assisted person 2 was not found."));
    }

    @Test
    void aReadablePersonWithoutAHomeIsA404WithItsOwnCode() throws Exception {
        when(access.requireReadableHouseholdOfPerson(7L, 2L)).thenThrow(new AssistedPersonWithoutHousehold(2L));

        mvc.perform(get(PATH).principal(USER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("assisted_person_without_household"));
    }

    @Test
    void anEdgeServiceOrAnAccountThatIsNotAUserIsRefused() throws Exception {
        mvc.perform(get(PATH).principal(new UsernamePasswordAuthenticationToken("edge:3", null, List.of())))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH)).andExpect(status().isForbidden());
    }

    @Test
    void ifTheStatesCannotBeReadTheAnswerIsAnUnavailableServiceToTryAgain() throws Exception {
        when(states.findAllByDeviceIds(anyCollection())).thenThrow(new IllegalStateException("MongoDB is down"));

        mvc.perform(get(PATH).principal(USER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("tracking_storage_unavailable"))
                .andExpect(jsonPath("$.detail").value("The information is not available now. Try again in a moment."));
    }
}
