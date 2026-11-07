package com.resolum.alivia.nursing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationScheduleNotFoundException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.commands.ChangeMedicationResponsibleCommand;
import com.resolum.alivia.nursing.domain.model.commands.CreateMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.commands.SuspendMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.commands.UpdateMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationByIdQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationsByAssistedPersonQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.Schedule;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.MedicationScheduleCommandService;
import com.resolum.alivia.nursing.domain.services.MedicationScheduleQueryService;
import com.resolum.alivia.nursing.interfaces.rest.NursingExceptionHandler;
import com.resolum.alivia.nursing.interfaces.rest.controllers.MedicationsController;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MedicationsController Tests")
class MedicationsControllerTest {

    private final MedicationScheduleCommandService commandService = mock(MedicationScheduleCommandService.class);
    private final MedicationScheduleQueryService queryService = mock(MedicationScheduleQueryService.class);
    private final CareMembershipRepository membershipRepository = mock(CareMembershipRepository.class);
    private final ExternalProfilesService externalProfilesService = mock(ExternalProfilesService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MedicationsController(
            commandService, queryService, membershipRepository, externalProfilesService, objectMapper))
            .setControllerAdvice(new NursingExceptionHandler(), new GlobalExceptionHandler())
            .build();

    private final Principal principal = () -> "10";

    @Test
    @DisplayName("POST /api/v1/assisted-persons/8/medications creates a medication schedule")
    void scheduleMedicationReturns201() throws Exception {
        var activity = new CareActivity(new PersonProfileId(8L), CareActivityType.MEDICATION, "Losartan 50 mg",
                "{\"medication\":\"Losartan\",\"dose\":\"50 mg\",\"frequency\":\"daily\"}");
        ReflectionTestUtils.setField(activity, "id", 1L);
        activity.assign(2L, Schedule.oneTime(LocalDate.now(), new TimeRange(LocalTime.of(8, 0), LocalTime.of(8, 15))), LocalDate.now());

        when(commandService.handle(any(CreateMedicationScheduleCommand.class))).thenReturn(activity);

        mvc.perform(post("/api/v1/assisted-persons/8/medications")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "medication": "Losartan",
                                    "dose": "50 mg",
                                    "times": ["08:00"],
                                    "frequency": "daily",
                                    "startDate": "2026-11-07",
                                    "caregiverId": 2,
                                    "indicationRef": "Receta Dr. Paredes"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.medication").value("Losartan"))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    @DisplayName("GET /api/v1/assisted-persons/8/medications lists scheduled medications")
    void listMedicationsReturns200() throws Exception {
        var activity = new CareActivity(new PersonProfileId(8L), CareActivityType.MEDICATION, "Losartan 50 mg",
                "{\"medication\":\"Losartan\",\"dose\":\"50 mg\"}");
        ReflectionTestUtils.setField(activity, "id", 1L);

        when(queryService.handle(any(GetMedicationsByAssistedPersonQuery.class))).thenReturn(List.of(activity));

        mvc.perform(get("/api/v1/assisted-persons/8/medications")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].medication").value("Losartan"));
    }

    @Test
    @DisplayName("PUT /api/v1/medications/1 updates indication")
    void updateMedicationReturns200() throws Exception {
        var activity = new CareActivity(new PersonProfileId(8L), CareActivityType.MEDICATION, "Losartan 100 mg",
                "{\"medication\":\"Losartan\",\"dose\":\"100 mg\"}");
        ReflectionTestUtils.setField(activity, "id", 1L);

        when(commandService.handle(any(UpdateMedicationScheduleCommand.class))).thenReturn(activity);

        mvc.perform(put("/api/v1/medications/1")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "dose": "100 mg",
                                    "times": ["08:00", "20:00"],
                                    "frequency": "daily",
                                    "effectiveFrom": "2026-11-08",
                                    "indicationRef": "Aumento por Dr. Salas"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    @DisplayName("POST /api/v1/medications/1/suspend suspends medication")
    void suspendMedicationReturns200() throws Exception {
        var activity = new CareActivity(new PersonProfileId(8L), CareActivityType.MEDICATION, "Losartan 50 mg",
                "{\"medication\":\"Losartan\",\"suspension\":{\"from\":\"2026-11-08\",\"source\":\"Dr. Salas\"}}");
        ReflectionTestUtils.setField(activity, "id", 1L);
        activity.deactivate();

        when(commandService.handle(any(SuspendMedicationScheduleCommand.class))).thenReturn(activity);

        mvc.perform(post("/api/v1/medications/1/suspend")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "from": "2026-11-08",
                                    "source": "Dr. Salas"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("suspended"));
    }

    @Test
    @DisplayName("PATCH /api/v1/medications/1/responsible reassigns caregiver")
    void changeResponsibleReturns200() throws Exception {
        var activity = new CareActivity(new PersonProfileId(8L), CareActivityType.MEDICATION, "Losartan 50 mg",
                "{\"medication\":\"Losartan\"}");
        ReflectionTestUtils.setField(activity, "id", 1L);

        when(commandService.handle(any(ChangeMedicationResponsibleCommand.class))).thenReturn(activity);

        mvc.perform(patch("/api/v1/medications/1/responsible")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "caregiverId": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    @DisplayName("GET /api/v1/medications/99 returns 404 when not found")
    void getMedicationNotFoundReturns404() throws Exception {
        when(queryService.handle(any(GetMedicationByIdQuery.class))).thenThrow(new MedicationScheduleNotFoundException(99L));

        mvc.perform(get("/api/v1/medications/99")
                        .principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("medication_schedule_not_found"));
    }

    @Test
    @DisplayName("GET /api/v1/assisted-persons/99/medications returns 403 when access denied")
    void getMedicationsAccessDeniedReturns403() throws Exception {
        when(queryService.handle(any(GetMedicationsByAssistedPersonQuery.class))).thenThrow(new AssistedPersonAccessDeniedException(99L));

        mvc.perform(get("/api/v1/assisted-persons/99/medications")
                        .principal(principal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("assisted_person_access_denied"));
    }
}
