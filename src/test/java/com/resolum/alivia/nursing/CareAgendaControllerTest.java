package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.model.queries.GetCareAgendaQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetPendingRemindersQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.nursing.domain.services.CareActivityQueryService;
import com.resolum.alivia.nursing.interfaces.rest.NursingExceptionHandler;
import com.resolum.alivia.nursing.interfaces.rest.controllers.CareAgendaController;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CareAgendaController Tests")
class CareAgendaControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);

    private final CareActivityQueryService queryService = mock(CareActivityQueryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CareAgendaController(queryService))
            .setControllerAdvice(new NursingExceptionHandler(), new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("GET /care-agenda returns 200 with shift, activity, person, schedule, responsible and status")
    void agendaReturns200() throws Exception {
        when(queryService.handle(any(GetCareAgendaQuery.class)))
                .thenReturn(List.of(occurrence(12L, CareActivityType.MEDICATION, 22, 6)));

        mvc.perform(get("/api/v1/care-agenda").principal(() -> "7")
                        .param("from", "2026-10-26").param("to", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").value(12))
                .andExpect(jsonPath("$[0].activity.title").value("Morning medication"))
                .andExpect(jsonPath("$[0].activity.type").value("MEDICATION"))
                .andExpect(jsonPath("$[0].assistedPerson.id").value(8))
                .andExpect(jsonPath("$[0].assistedPerson.name").value("Maria Lopez"))
                .andExpect(jsonPath("$[0].date").value("2026-10-26"))
                .andExpect(jsonPath("$[0].endsAt").value("2026-10-27T06:00:00"))
                .andExpect(jsonPath("$[0].responsible.type").value("CAREGIVER"))
                .andExpect(jsonPath("$[0].responsible.id").value(7))
                .andExpect(jsonPath("$[0].responsible.personProfileId").value(70))
                .andExpect(jsonPath("$[0].responsible.name").isEmpty())
                .andExpect(jsonPath("$[0].responsible.email").value("ana.torres@example.com"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"));

        verify(queryService).handle(argThat((GetCareAgendaQuery query) -> query.userId().equals(new UserId(7L))
                && query.from().equals(MONDAY) && query.to().equals(LocalDate.of(2026, 11, 1))));
    }

    @Test
    @DisplayName("GET /reminders?status=PENDING returns 200 keeping the date and priority order")
    void remindersReturn200() throws Exception {
        when(queryService.handle(any(GetPendingRemindersQuery.class))).thenReturn(List.of(
                occurrence(1L, CareActivityType.MEDICATION, 14, 15),
                occurrence(2L, CareActivityType.THERAPY, 14, 15)));

        mvc.perform(get("/api/v1/reminders").principal(() -> "7").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").value(1))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[1].priority").value("LOW"));
    }

    @Test
    @DisplayName("GET /care-agenda with from after to returns 400")
    void fromAfterToReturns400() throws Exception {
        mvc.perform(get("/api/v1/care-agenda").principal(() -> "7")
                        .param("from", "2026-11-02").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_argument"));

        verify(queryService, never()).handle(any(GetCareAgendaQuery.class));
    }

    @Test
    @DisplayName("GET /care-agenda without from or with an invalid date returns 400")
    void missingOrMalformedDateReturns400() throws Exception {
        mvc.perform(get("/api/v1/care-agenda").principal(() -> "7").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/care-agenda").principal(() -> "7")
                        .param("from", "26-10-2026").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    @DisplayName("GET /care-agenda for an assisted person the caregiver is not linked to returns 403")
    void unlinkedPersonReturns403() throws Exception {
        when(queryService.handle(any(GetCareAgendaQuery.class))).thenThrow(new AssistedPersonAccessDeniedException(20L));

        mvc.perform(get("/api/v1/care-agenda").principal(() -> "7")
                        .param("from", "2026-10-26").param("to", "2026-11-01").param("assistedPersonId", "20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("assisted_person_access_denied"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("GET /reminders with a status other than PENDING returns 400")
    void invalidReminderStatusReturns400() throws Exception {
        mvc.perform(get("/api/v1/reminders").principal(() -> "7").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));

        verify(queryService, never()).handle(any(GetPendingRemindersQuery.class));
    }

    private static AgendaItem occurrence(Long assignmentId, CareActivityType type, int startHour, int endHour) {
        var start = LocalTime.of(startHour, 0);
        var end = LocalTime.of(endHour, 0);
        var endDate = end.isBefore(start) ? MONDAY.plusDays(1) : MONDAY;
        return new AgendaItem(3L, assignmentId, "Morning medication", type, new PersonProfileId(8L), MONDAY,
                new TimeRange(start, end), MONDAY.atTime(start), endDate.atTime(end), 7L, true,
                OccurrenceStatus.SCHEDULED,
                new AgendaItem.Contact("Maria Lopez", new PersonProfileId(70L), null, "ana.torres@example.com"));
    }
}
