package com.resolum.alivia.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.tracking.application.internal.queryservices.DeviceEventsQueryServiceImpl;
import com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import com.resolum.alivia.tracking.interfaces.rest.controllers.DeviceEventsController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /devices/{deviceId}/events} with the real controller and service, against {@code q-device-events.response.json}. */
class DeviceEventsControllerTest {

    private static final String PATH = "/api/v1/devices/10/events";
    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");
    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("7", null, List.of());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ReportingContext CONTEXT = new ReportingContext(3L, 30L, 30L, Set.of(10L));

    private final TrackingAccessService access = mock(TrackingAccessService.class);
    private final DeviceEventRepository events = mock(DeviceEventRepository.class);
    private MockMvc mvc;

    private static DeviceEvent event(String id, String type, String occurredAt, String received, String commandId,
                                     String action, String result, Map<String, Object> attributes) {
        return DeviceEvent.fromEdge(CONTEXT, new ReportedEvent(id, type, Instant.parse(occurredAt), 10L, commandId, action,
                result, null, null, null, attributes), Instant.parse(received), true, TrackingPolicy.defaults());
    }

    @BeforeEach
    void setUp() {
        var service = new DeviceEventsQueryServiceImpl(access, events, HistoryPolicy.proposed(),
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        mvc = MockMvcBuilders.standaloneSetup(new DeviceEventsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(access.requireReadableHouseholdOfDevice(7L, 10L)).thenReturn(30L);
        when(events.findByDevice(anyLong(), anyLong(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new EventPage(List.of(), 0));
    }

    @Test
    void thePageIsAnsweredAsTheContractSays() throws Exception {
        when(events.findByDevice(30L, 10L, Instant.parse("2026-11-24T00:00:00Z"), Instant.parse("2026-11-25T00:00:00Z"),
                null, 0, 2)).thenReturn(new EventPage(List.of(
                event("ev-3", "COMMAND_RESULT", "2026-11-24T14:58:00Z", "2026-11-24T15:05:00Z", "cmd-77", "open", "SUCCESS", null),
                event("ev-2", "DEVICE_STATE_CHANGED", "2026-11-24T14:00:00Z", "2026-11-24T14:00:02Z", null, null, null,
                        Map.of("state", "open", "previous", "closed"))), 3));

        var answer = mvc.perform(get(PATH).principal(USER).param("from", "2026-11-24T00:00:00Z")
                        .param("to", "2026-11-25T00:00:00Z").param("size", "2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var expected = JSON.readTree(Files.readString(Path.of("docs", "fixtures", "tracking", "q-device-events.response.json")));
        assertThat(JSON.readTree(answer)).isEqualTo(expected);
    }

    @Test
    void whatIdentifiesTheEdgeOrTheInstallationIsNotShown() throws Exception {
        when(events.findByDevice(anyLong(), anyLong(), any(), any(), any(), anyInt(), anyInt())).thenReturn(new EventPage(
                List.of(event("ev-1", "DEVICE_STATE_CHANGED", "2026-11-24T14:00:00Z", "2026-11-24T14:00:02Z", null, null,
                        null, null)), 1));

        var item = JSON.readTree(mvc.perform(get(PATH).principal(USER)).andReturn().getResponse().getContentAsString())
                .get("items").get(0);

        assertThat(item.has("edgeId")).isFalse();
        assertThat(item.has("installationId")).isFalse();
        assertThat(item.has("householdId")).isFalse();
        assertThat(item.has("attributes")).isFalse();
    }

    @Test
    void anEmptyPeriodIsAnEmptyPageNotAnError() throws Exception {
        mvc.perform(get(PATH).principal(USER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void theFilterByTypeIsPassedOn() throws Exception {
        mvc.perform(get(PATH).principal(USER).param("type", "help_requested")).andExpect(status().isOk());

        verify(events).findByDevice(30L, 10L, NOW.minus(java.time.Duration.ofDays(31)), NOW, EventType.HELP_REQUESTED, 0, 20);
    }

    @Test
    void anInvalidRequestIsA400WithTheCodeOfTheReason() throws Exception {
        mvc.perform(get(PATH).principal(USER).param("from", "2026-11-25T00:00:00Z").param("to", "2026-11-24T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_range"));
        mvc.perform(get(PATH).principal(USER).param("from", "2026-01-01T00:00:00Z").param("to", "2026-11-24T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("range_too_large"));
        mvc.perform(get(PATH).principal(USER).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_page_size"));
        mvc.perform(get(PATH).principal(USER).param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_page"));
        mvc.perform(get(PATH).principal(USER).param("type", "EXPLODED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_type"));
    }

    @Test
    void aValueThatIsNotADateOrANumberIsAnInvalidRequest() throws Exception {
        mvc.perform(get(PATH).principal(USER).param("from", "yesterday"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
        mvc.perform(get(PATH).principal(USER).param("page", "two"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void aDeviceTheUserCannotReadIsA404WithTheCodeOfAMissingOne() throws Exception {
        when(access.requireReadableHouseholdOfDevice(7L, 10L)).thenThrow(new DeviceNotAccessible(10L));

        mvc.perform(get(PATH).principal(USER)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("device_not_found"))
                .andExpect(jsonPath("$.detail").value("The device 10 was not found."));
    }

    @Test
    void anEdgeServiceOrWhoIsNotAUserIsRefused() throws Exception {
        mvc.perform(get(PATH).principal(new UsernamePasswordAuthenticationToken("edge:3", null, List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void ifTheEventsCannotBeReadTheAnswerIsAnUnavailableServiceToTryAgain() throws Exception {
        when(events.findByDevice(anyLong(), anyLong(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("MongoDB is down"));

        mvc.perform(get(PATH).principal(USER)).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("tracking_storage_unavailable"));
    }
}
