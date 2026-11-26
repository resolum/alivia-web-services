package com.resolum.alivia.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.tracking.application.internal.queryservices.RecentEventsQueryServiceImpl;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold;
import com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.queries.GetRecentEventsByAssistedPersonQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import com.resolum.alivia.tracking.interfaces.rest.controllers.RecentEventsController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The recent events of the home of an assisted person: the limit, who may read them, and the shape of the answer. */
class RecentEventsTest {

    private static final String PATH = "/api/v1/assisted-persons/2/events/recent";
    private static final UsernamePasswordAuthenticationToken USER = new UsernamePasswordAuthenticationToken("7", null, List.of());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ReportingContext CONTEXT = new ReportingContext(3L, 30L, 300L, Set.of(10L));

    private final TrackingAccessService access = mock(TrackingAccessService.class);
    private final DeviceEventRepository events = mock(DeviceEventRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RecentEventsQueryServiceImpl service = new RecentEventsQueryServiceImpl(access, events,
            HistoryPolicy.proposed(), meters);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecentEventsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(access.requireReadableHouseholdOfPerson(7L, 2L)).thenReturn(300L);
        when(events.findRecentByHousehold(anyLong(), anyInt())).thenReturn(List.of());
    }

    private static DeviceEvent fromEdge(String id, String type, String at, String received, Long device, String command,
                                        String action, String result, String detail) {
        return DeviceEvent.fromEdge(CONTEXT, new ReportedEvent(id, type, Instant.parse(at), device, command, action, result,
                null, null, detail, Map.of()), Instant.parse(received), true, TrackingPolicy.defaults());
    }

    private static GetRecentEventsByAssistedPersonQuery query(Integer limit) {
        return new GetRecentEventsByAssistedPersonQuery(7L, 2L, limit);
    }

    // ---- the service

    @Test
    void withoutALimitItIsTheDefaultOfTheHistoryAndTheHomeIsThePersonsHome() {
        var view = service.handle(query(null));

        assertThat(view.limit()).isEqualTo(20);
        assertThat(view.householdId()).isEqualTo(300L);
        verify(events).findRecentByHousehold(300L, 20);
    }

    @ParameterizedTest
    @CsvSource({"1,true", "20,true", "100,true", "0,false", "-1,false", "101,false"})
    void theLimitStaysWithinTheMaximumOfTheHistory(int limit, boolean valid) {
        if (valid) {
            assertThat(service.handle(query(limit)).limit()).isEqualTo(limit);
        } else {
            assertThatThrownBy(() -> service.handle(query(limit))).isInstanceOfSatisfying(InvalidHistoryQuery.class,
                    e -> assertThat(e.code()).isEqualTo("invalid_limit"));
        }
    }

    @Test
    void aBadLimitIsRefusedBeforeLookingAnythingUp() {
        assertThatThrownBy(() -> service.handle(query(500))).isInstanceOf(InvalidHistoryQuery.class);

        verifyNoInteractions(access, events);
    }

    @Test
    void whoMayNotReadThePersonOrWhoseHomeIsMissingReadsNothing() {
        when(access.requireReadableHouseholdOfPerson(8L, 2L)).thenThrow(new AssistedPersonNotAccessible(2L));
        when(access.requireReadableHouseholdOfPerson(7L, 3L)).thenThrow(new AssistedPersonWithoutHousehold(3L));

        assertThatThrownBy(() -> service.handle(new GetRecentEventsByAssistedPersonQuery(8L, 2L, null)))
                .isInstanceOf(AssistedPersonNotAccessible.class);
        assertThatThrownBy(() -> service.handle(new GetRecentEventsByAssistedPersonQuery(7L, 3L, null)))
                .isInstanceOf(AssistedPersonWithoutHousehold.class);
        verifyNoInteractions(events);
    }

    @Test
    void ifTheEventsCannotBeReadItIsNotAnEmptyList() {
        when(events.findRecentByHousehold(anyLong(), anyInt())).thenThrow(new IllegalStateException("MongoDB is down"));

        assertThatThrownBy(() -> service.handle(query(null))).isInstanceOf(TrackingStorageUnavailable.class);
        assertThat(meters.get("tracking.storage.failures").tag("operation", "read_recent_events").counter().count())
                .isEqualTo(1.0);
    }

    // ---- the endpoint

    @Test
    void theEventsAreAnsweredAsTheContractSays() throws Exception {
        when(events.findRecentByHousehold(300L, 20)).thenReturn(List.of(
                DeviceEvent.byServer("dc-edge-3-1732460280000", EventType.EDGE_DISCONNECTED, 3L, 30L, 300L, null,
                        Instant.parse("2026-11-24T15:01:00Z"), "Sin reportes desde 2026-11-24T14:58:00Z"),
                fromEdge("help-1", "HELP_REQUESTED", "2026-11-24T14:59:00Z", "2026-11-24T14:59:01Z", null, null, null, null, "ayuda"),
                fromEdge("ev-3", "COMMAND_RESULT", "2026-11-24T14:58:00Z", "2026-11-24T15:05:00Z", 10L, "cmd-77", "open",
                        "SUCCESS", null)));

        var answer = mvc.perform(get(PATH).principal(USER)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var expected = JSON.readTree(Files.readString(Path.of("docs", "fixtures", "tracking", "q-recent-events.response.json")));
        assertThat(JSON.readTree(answer)).isEqualTo(expected);
    }

    @Test
    void aHomeWithoutEventsIsAnEmptyListNotAnError() throws Exception {
        mvc.perform(get(PATH).principal(USER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.limit").value(20));
    }

    @Test
    void theLimitOfTheRequestIsUsedAndABadOneIsA400() throws Exception {
        mvc.perform(get(PATH).principal(USER).param("limit", "5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(5));
        verify(events).findRecentByHousehold(300L, 5);

        mvc.perform(get(PATH).principal(USER).param("limit", "101")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_limit"));
        mvc.perform(get(PATH).principal(USER).param("limit", "many")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void whoCannotReadThePersonGetsTheSame404AsForAPersonWhoDoesNotExist() throws Exception {
        when(access.requireReadableHouseholdOfPerson(7L, 2L)).thenThrow(new AssistedPersonNotAccessible(2L));

        mvc.perform(get(PATH).principal(USER)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("assisted_person_not_found"))
                .andExpect(jsonPath("$.detail").value("The assisted person 2 was not found."));
    }

    @Test
    void anEdgeServiceIsRefusedAndAFailedReadIsA503() throws Exception {
        mvc.perform(get(PATH).principal(new UsernamePasswordAuthenticationToken("edge:3", null, List.of())))
                .andExpect(status().isForbidden());

        when(events.findRecentByHousehold(anyLong(), anyInt())).thenThrow(new IllegalStateException("MongoDB is down"));
        mvc.perform(get(PATH).principal(USER)).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));
    }
}
