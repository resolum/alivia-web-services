package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.application.internal.commandservices.MedicationAdministrationCommandServiceImpl;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationOutcomeAlreadyRecordedException;
import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.nursing.domain.model.commands.RecordMedicationOutcomeCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationAdministrationQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministration;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationResult;
import com.resolum.alivia.nursing.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.nursing.domain.services.MedicationAdministrationCommandService;
import com.resolum.alivia.nursing.domain.services.MedicationAdministrationQueryService;
import com.resolum.alivia.nursing.interfaces.rest.NursingExceptionHandler;
import com.resolum.alivia.nursing.interfaces.rest.controllers.MedicationAdministrationsController;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MedicationAdministrationsController Tests")
class MedicationAdministrationsControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final MedicationAdministrationId ID = new MedicationAdministrationId(12L, MONDAY);
    private static final UserId CAREGIVER = new UserId(7L);
    private static final String URL = "/api/v1/medication-administrations/12-20261026";
    private static final String OUTCOMES_URL = URL + "/outcomes";

    private final MedicationAdministrationQueryService queryService = mock(MedicationAdministrationQueryService.class);
    private final MedicationAdministrationCommandService commandService = mock(MedicationAdministrationCommandService.class);
    private final MockMvc mvc = mvcWith(commandService);

    @Test
    @DisplayName("GET by the assigned caregiver returns 200 with medication, schedule, responsible and status only")
    void getReturns200() throws Exception {
        when(queryService.handle(any(GetMedicationAdministrationQuery.class))).thenReturn(pending());

        mvc.perform(get(URL).principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.administrationId").value("12-20261026"))
                .andExpect(jsonPath("$.medication").value("Losartan 50 mg"))
                .andExpect(jsonPath("$.assistedPerson.id").value(8))
                .andExpect(jsonPath("$.assistedPerson.name").value("Maria Lopez"))
                .andExpect(jsonPath("$.date").value("2026-10-26"))
                .andExpect(jsonPath("$.startTime").value("08:00:00"))
                .andExpect(jsonPath("$.endTime").value("09:00:00"))
                .andExpect(jsonPath("$.responsible.type").value("CAREGIVER"))
                .andExpect(jsonPath("$.responsible.id").value(7))
                .andExpect(jsonPath("$.responsible.name").value("Ana Torres"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.outcome").isEmpty())
                // No clinical recommendations: the resource has exactly these fields.
                .andExpect(jsonPath("$.length()").value(9));

        verify(queryService).handle(argThat(query -> query.userId().equals(CAREGIVER) && query.administrationId().equals(ID)));
    }

    @Test
    @DisplayName("POST on a pending administration returns 201 and the assigned caregiver who recorded it")
    void postReturns201() throws Exception {
        when(commandService.handle(any(RecordMedicationOutcomeCommand.class))).thenAnswer(invocation -> {
            RecordMedicationOutcomeCommand command = invocation.getArgument(0);
            return pending().withOutcome(ActivityLog.medicationOutcome(pending(), command, 7L, MONDAY.atTime(10, 0)));
        });

        mvc.perform(post(OUTCOMES_URL).principal(() -> "7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"ADMINISTERED\",\"occurredAt\":\"2026-10-26T08:15:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ADMINISTERED"))
                .andExpect(jsonPath("$.outcome.result").value("ADMINISTERED"))
                .andExpect(jsonPath("$.outcome.occurredAt").value("2026-10-26T08:15:00"))
                .andExpect(jsonPath("$.outcome.registeredAt").value("2026-10-26T10:00:00"))
                .andExpect(jsonPath("$.outcome.registeredBy").value(7))
                .andExpect(jsonPath("$.outcome.late").value(false));

        verify(commandService).handle(argThat(command -> command.userId().equals(CAREGIVER)
                && command.administrationId().equals(ID) && command.result() == MedicationResult.ADMINISTERED
                && command.occurredAt().equals(MONDAY.atTime(8, 15))));
    }

    @Test
    @DisplayName("POST OMITTED without reason returns 400")
    void omittedWithoutReasonReturns400() throws Exception {
        mvc.perform(post(OUTCOMES_URL).principal(() -> "7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"OMITTED\",\"occurredAt\":\"2026-10-26T08:15:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A reason is required when the medication is omitted."));

        verify(commandService, never()).handle(any());
    }

    @Test
    @DisplayName("POST with an invalid result, a missing moment or a malformed id returns 400")
    void malformedRequestsReturn400() throws Exception {
        mvc.perform(post(OUTCOMES_URL).principal(() -> "7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"TAKEN\",\"occurredAt\":\"2026-10-26T08:15:00\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(OUTCOMES_URL).principal(() -> "7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"ADMINISTERED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/medication-administrations/12-2026").principal(() -> "7"))
                .andExpect(status().isBadRequest());

        verify(commandService, never()).handle(any());
    }

    @Test
    @DisplayName("POST on a closed administration returns 409")
    void closedAdministrationReturns409() throws Exception {
        when(commandService.handle(any(RecordMedicationOutcomeCommand.class)))
                .thenThrow(new MedicationOutcomeAlreadyRecordedException(ID));

        mvc.perform(post(OUTCOMES_URL).principal(() -> "7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"ADMINISTERED\",\"occurredAt\":\"2026-10-26T08:15:00\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A unique constraint violation on save returns 409, not 500")
    void uniqueViolationReturns409() throws Exception {
        var repository = mock(ActivityLogRepository.class);
        when(queryService.handle(any(GetMedicationAdministrationQuery.class))).thenReturn(pending());
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        var clock = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);
        var realMvc = mvcWith(new MedicationAdministrationCommandServiceImpl(queryService, repository, clock));

        realMvc.perform(post(OUTCOMES_URL).principal(() -> "7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"ADMINISTERED\",\"occurredAt\":\"2026-10-26T08:15:00\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET by a user without permission returns 403 and an unknown administration returns 404")
    void forbiddenAndNotFound() throws Exception {
        when(queryService.handle(any(GetMedicationAdministrationQuery.class)))
                .thenThrow(new MedicationAdministrationAccessDeniedException(ID))
                .thenThrow(new MedicationAdministrationNotFoundException(ID));

        mvc.perform(get(URL).principal(() -> "30")).andExpect(status().isForbidden());
        mvc.perform(get(URL).principal(() -> "7")).andExpect(status().isNotFound());
    }

    private MockMvc mvcWith(MedicationAdministrationCommandService service) {
        return MockMvcBuilders.standaloneSetup(new MedicationAdministrationsController(queryService, service))
                .setControllerAdvice(new NursingExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    private static MedicationAdministration pending() {
        return new MedicationAdministration(ID, 3L, "Losartan 50 mg", new PersonProfileId(8L), "Maria Lopez", MONDAY,
                LocalTime.of(8, 0), LocalTime.of(9, 0), MONDAY.atTime(8, 0), MONDAY.atTime(9, 0),
                7L, CAREGIVER, "Ana Torres", null);
    }
}
