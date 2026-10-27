package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotFound;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleOverlap;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.commands.CreateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.DeactivateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkScheduleQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkScheduleSummaryQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkSchedulesQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkSchedulePeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleSummary;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleViewStatus;
import com.resolum.alivia.hcm.domain.services.WorkScheduleCommandService;
import com.resolum.alivia.hcm.domain.services.WorkScheduleQueryService;
import com.resolum.alivia.hcm.interfaces.rest.controllers.WorkSchedulesController;
import com.resolum.alivia.hcm.interfaces.rest.transform.WorkScheduleResourceAssembler;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkSchedulesControllerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);
    private static final Principal ADMIN = () -> "9";
    private static final String BODY = """
            {"businessId":1,"name":"Turno A","startDate":"2026-10-26","endDate":null,"draft":false,
             "shifts":[{"day":"MONDAY","startTime":"08:00","endTime":"12:00"}]}
            """;

    private WorkScheduleCommandService commands;
    private WorkScheduleQueryService queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(WorkScheduleCommandService.class);
        queries = mock(WorkScheduleQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new WorkSchedulesController(commands, queries,
                        new WorkScheduleResourceAssembler(CLOCK)))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(queries.associatedContract(any())).thenReturn(Optional.of(contract()));
    }

    @Test
    void createsScheduleInsideContractUsingAuthenticatedActor() throws Exception {
        when(commands.handle(any(CreateWorkScheduleCommand.class))).thenReturn(schedule());

        mvc.perform(post("/api/v1/employees/3/contracts/5/work-schedules").principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("HOR-2026-0012"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.weeklyHours").value(40.0))
                .andExpect(jsonPath("$.contract.code").value("CTR-ALV-2026-0005"))
                .andExpect(jsonPath("$.compliance").value("COMPLIANT"))
                .andExpect(jsonPath("$.exceedsLegalLimit").value(false));

        var captor = ArgumentCaptor.forClass(CreateWorkScheduleCommand.class);
        verify(commands).handle(captor.capture());
        assertEquals(5L, captor.getValue().contractId());
        assertEquals(9L, captor.getValue().actorUserId());
        assertEquals(DayOfWeek.MONDAY, captor.getValue().shifts().getFirst().day());
    }

    @Test
    void rejectsCreationWithoutValidActor() throws Exception {
        mvc.perform(post("/api/v1/employees/3/contracts/5/work-schedules")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/employees/3/contracts/5/work-schedules").principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(commands);
    }

    @Test
    void listsSchedulesAndSummary() throws Exception {
        when(queries.handle(any(GetWorkSchedulesQuery.class)))
                .thenReturn(new PageImpl<>(List.of(schedule()), PageRequest.of(0, 8), 1));
        when(queries.handle(any(GetWorkScheduleSummaryQuery.class)))
                .thenReturn(new WorkScheduleSummary(1, 1, new BigDecimal("40.0")));

        mvc.perform(get("/api/v1/work-schedules").param("businessId", "1").param("role", "TECH")
                        .param("status", "ACTIVE").param("contractId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Turno A"));
        mvc.perform(get("/api/v1/work-schedules/summary").param("businessId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageWeeklyHours").value(40.0));

        var captor = ArgumentCaptor.forClass(GetWorkSchedulesQuery.class);
        verify(queries).handle(captor.capture());
        assertEquals(EmployeeRole.TECH, captor.getValue().role());
        assertEquals(WorkScheduleViewStatus.ACTIVE, captor.getValue().status());
        assertEquals(5L, captor.getValue().contractId());
    }

    @Test
    void readsUpdatesAndDeactivates() throws Exception {
        when(queries.handle(any(GetWorkScheduleQuery.class))).thenReturn(schedule());
        when(commands.handle(any(UpdateWorkScheduleCommand.class))).thenReturn(schedule());
        when(commands.handle(any(DeactivateWorkScheduleCommand.class))).thenReturn(schedule());

        mvc.perform(get("/api/v1/employees/3/work-schedules/12").param("businessId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(12));
        mvc.perform(patch("/api/v1/employees/3/work-schedules/12").param("businessId", "1").principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/employees/3/work-schedules/12/deactivation").param("businessId", "1")
                        .principal(ADMIN))
                .andExpect(status().isOk());

        verify(commands).handle(new DeactivateWorkScheduleCommand(3L, 12L, 1L, 9L));
    }

    @Test
    void mapsWorkScheduleErrors() throws Exception {
        when(queries.handle(any(GetWorkScheduleQuery.class))).thenThrow(new WorkScheduleNotFound(12L));
        when(commands.handle(any(UpdateWorkScheduleCommand.class))).thenThrow(new WorkScheduleOverlap(3L));

        mvc.perform(get("/api/v1/employees/3/work-schedules/12").param("businessId", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/employees/3/work-schedules/12").param("businessId", "1").principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void reportsSchedulesWithoutContract() {
        var assembler = new WorkScheduleResourceAssembler(CLOCK);
        var resource = assembler.toResource(schedule(), Optional.empty());

        assertNull(resource.contract());
        assertEquals("NO_REFERENCE", resource.compliance().name());
    }

    private static WorkSchedule schedule() {
        var schedule = new WorkSchedule(3L, 1L, 5L, "Turno A", new WorkSchedulePeriod(TODAY, null),
                WorkScheduleDomainTest.splitWeekdays(), 9L);
        schedule.activate();
        ReflectionTestUtils.setField(schedule, "id", 12L);
        schedule.assignCode("HOR-2026-0012");
        return schedule;
    }

    private static Contract contract() {
        var contract = new Contract(3L, 1L, "Técnico", ContractModality.HOURLY, 40,
                new ContractPeriod(TODAY.minusMonths(1), TODAY.plusMonths(6)), ContractStatus.ACTIVE, null);
        ReflectionTestUtils.setField(contract, "id", 5L);
        contract.assignCode("CTR-ALV-2026-0005");
        return contract;
    }
}
