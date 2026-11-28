package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceClosedException;
import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.assistance.domain.services.ActivityLogCommandService;
import com.resolum.alivia.assistance.domain.services.ActivityLogQueryService;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.interfaces.rest.AssistanceExceptionHandler;
import com.resolum.alivia.assistance.interfaces.rest.controllers.CareActivityStatusController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TS-14: {@code PATCH /care-activities/{id}/status} over the existing completion rules. */
class CareActivityStatusControllerTest {

    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("7", null, List.of());
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private final ActivityLogCommandService commands = mock(ActivityLogCommandService.class);
    private final ActivityLogQueryService queries = mock(ActivityLogQueryService.class);
    private final AssistanceAccessService access = mock(AssistanceAccessService.class);
    private final CareActivity activity = mock(CareActivity.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CareActivityStatusController(commands, queries, access))
                .setControllerAdvice(new AssistanceExceptionHandler(), new GlobalExceptionHandler()).build();
        var person = mock(AssistedPerson.class);
        when(person.getId()).thenReturn(5L);
        when(activity.getAssistedPerson()).thenReturn(person);
        when(access.requireOperateActivity(3L, 7L)).thenReturn(activity);
    }

    private static String body(String status) {
        return "{\"status\":\"" + status + "\",\"assignmentId\":4,\"date\":\"" + DAY + "\"}";
    }

    @Test
    void completedAnswers200WithWhoAndWhen() throws Exception {
        when(commands.handle(any(CompleteActivityCommand.class))).thenReturn(9L);
        var log = mock(ActivityLog.class);
        when(log.getId()).thenReturn(9L);
        when(log.getStatus()).thenReturn(ActivityLogStatus.COMPLETED);
        when(log.getCompletedAt()).thenReturn(DAY.atTime(9, 30));
        when(queries.handle(any(com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByAssistedPersonIdQuery.class)))
                .thenReturn(List.of(log));

        mvc.perform(patch("/api/v1/care-activities/3/status").principal(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(body("COMPLETED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(commands).handle(new CompleteActivityCommand(3L, 4L, DAY, 7L, null));
    }

    @Test
    void anActivityThatDoesNotExistIs404() throws Exception {
        when(access.requireOperateActivity(99L, 7L)).thenThrow(new CareActivityNotFoundException(99L));

        mvc.perform(patch("/api/v1/care-activities/99/status").principal(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(body("COMPLETED")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOccurrenceAlreadyClosedIs409AndAnUnknownStatusIs400() throws Exception {
        when(commands.handle(any(CompleteActivityCommand.class))).thenThrow(new OccurrenceClosedException(4L, DAY));

        mvc.perform(patch("/api/v1/care-activities/3/status").principal(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(body("COMPLETED")))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/care-activities/3/status").principal(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(body("DONE")))
                .andExpect(status().isBadRequest());
    }
}
