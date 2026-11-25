package com.resolum.alivia.tracking;

import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.tracking.domain.exceptions.EdgeNotInInstallation;
import com.resolum.alivia.tracking.domain.model.commands.ReportUrgentEventCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.services.RecordEdgeEventsCommandService;
import com.resolum.alivia.tracking.interfaces.rest.controllers.EdgeEventsController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The urgent events endpoint: the contract it has always had, with the example of the edge contract as the request. */
class EdgeEventsControllerTest {

    private static final String PATH = "/api/v1/edge/events";
    private static final UsernamePasswordAuthenticationToken EDGE =
            new UsernamePasswordAuthenticationToken("edge:3", null, List.of());

    private RecordEdgeEventsCommandService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RecordEdgeEventsCommandService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EdgeEventsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("docs", "fixtures", "tracking", name));
    }

    @Test
    void theExampleOfTheContractIsAcceptedAndAnsweredWithTheNumberOfRelativesToldNow() throws Exception {
        when(service.handle(any(ReportUrgentEventCommand.class))).thenReturn(2);

        mvc.perform(post(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("e1-event.request.json")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.notified").value(2));

        verify(service).handle(new ReportUrgentEventCommand(3L, "c0ffee00a1b2c3d4e5f60718293a4b5c",
                EventType.HELP_REQUESTED, 1L, null, "me caí y no puedo levantarme",
                Instant.parse("2026-11-24T15:03:21.480Z")));
    }

    @Test
    void theReportOfAnEdgeThatDoesNotSendWhenItHappenedStillWorks() throws Exception {
        when(service.handle(any(ReportUrgentEventCommand.class))).thenReturn(1);

        mvc.perform(post(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"help-9\",\"type\":\"help_requested\",\"detail\":\"ayuda\"}"))
                .andExpect(status().isAccepted());

        verify(service).handle(new ReportUrgentEventCommand(3L, "help-9", EventType.HELP_REQUESTED, null, null, "ayuda", null));
    }

    @Test
    void theTypesOfTheOtherEndpointsAndUnknownOnesAreRefused() throws Exception {
        for (var type : new String[]{"FIRE", "COMMAND_RESULT", "EDGE_DISCONNECTED"}) {
            mvc.perform(post(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventId\":\"x\",\"type\":\"" + type + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void anInvalidReportIsRefusedBeforeReachingTheService() throws Exception {
        for (var body : new String[]{
                "{\"eventId\":\"has space\",\"type\":\"HELP_REQUESTED\"}",
                "{\"type\":\"HELP_REQUESTED\"}",
                "{\"eventId\":\"x\",\"type\":\"LOW_BATTERY\",\"batteryPct\":101}",
                "{\"eventId\":\"x\",\"type\":\"HELP_REQUESTED\",\"detail\":\"" + "a".repeat(501) + "\"}"}) {
            mvc.perform(post(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void aCallerThatIsNotAnEdgeIsRefusedAndAnEdgeWithoutAHomeGetsNotFound() throws Exception {
        var user = new UsernamePasswordAuthenticationToken("7", null, List.of());
        mvc.perform(post(PATH).principal(user).contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("e1-event.request.json")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        when(service.handle(any(ReportUrgentEventCommand.class))).thenThrow(new EdgeNotInInstallation(3L));
        mvc.perform(post(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("e1-event.request.json")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_without_installation"));
    }
}
