package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.ContractOverlap;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.CreateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.commands.RenewContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.ActivateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.TerminateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.hcm.domain.model.queries.GetContractQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetContractsQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeeByIdQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeesQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractAction;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeStatus;
import com.resolum.alivia.hcm.domain.services.ContractCommandService;
import com.resolum.alivia.hcm.domain.services.ContractQueryService;
import com.resolum.alivia.hcm.domain.services.EmployeeCommandService;
import com.resolum.alivia.hcm.domain.services.EmployeeQueryService;
import com.resolum.alivia.hcm.interfaces.rest.controllers.ContractsController;
import com.resolum.alivia.hcm.interfaces.rest.controllers.EmployeesController;
import com.resolum.alivia.hcm.interfaces.rest.transform.ContractResourceAssembler;
import com.resolum.alivia.hcm.interfaces.rest.transform.EmployeeResourceAssembler;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade.StaffProfileData;
import com.resolum.alivia.hcm.interfaces.rest.DefaultBusiness;
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

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HcmControllersTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);
    private static final Principal ADMIN = () -> "9";
    private static final String CONTRACT_BODY = """
            {"businessId":1,"position":"Field technician","modality":"HOURLY","weeklyHours":40,
             "startDate":"2026-10-25","endDate":"2027-04-26","notes":"D.L. 728"}
            """;

    private ContractCommandService contractCommands;
    private ContractQueryService contractQueries;
    private EmployeeCommandService employeeCommands;
    private EmployeeQueryService employeeQueries;
    private ProfilesContextFacade profiles;
    private MockMvc contractsMvc;
    private MockMvc employeesMvc;

    @BeforeEach
    void setUp() {
        contractCommands = mock(ContractCommandService.class);
        contractQueries = mock(ContractQueryService.class);
        employeeCommands = mock(EmployeeCommandService.class);
        employeeQueries = mock(EmployeeQueryService.class);
        profiles = mock(ProfilesContextFacade.class);
        contractsMvc = MockMvcBuilders.standaloneSetup(new ContractsController(contractCommands, contractQueries,
                        new ContractResourceAssembler(CLOCK), CLOCK, new DefaultBusiness(1L)))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        employeesMvc = MockMvcBuilders.standaloneSetup(new EmployeesController(employeeCommands, employeeQueries,
                        new EmployeeResourceAssembler(profiles), new DefaultBusiness(1L)))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createsContractUsingAuthenticatedActor() throws Exception {
        when(contractCommands.handle(any(CreateContractCommand.class))).thenReturn(contract(ContractStatus.ACTIVE));

        contractsMvc.perform(post("/api/v1/employees/3/contracts").principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(CONTRACT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CTR-ALV-2026-0005"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.eligible").value(true));

        var captor = ArgumentCaptor.forClass(CreateContractCommand.class);
        verify(contractCommands).handle(captor.capture());
        assertEquals(3L, captor.getValue().employeeId());
        assertEquals(9L, captor.getValue().actorUserId());
        assertEquals(ContractModality.HOURLY, captor.getValue().modality());
    }

    @Test
    void rejectsContractCreationWithoutValidActorOrBody() throws Exception {
        contractsMvc.perform(post("/api/v1/employees/3/contracts")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTRACT_BODY))
                .andExpect(status().isBadRequest());
        contractsMvc.perform(post("/api/v1/employees/3/contracts").principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTRACT_BODY))
                .andExpect(status().isBadRequest());
        contractsMvc.perform(post("/api/v1/employees/3/contracts").principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"businessId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        verifyNoInteractions(contractCommands);
    }

    @Test
    void listsContractsWithFilters() throws Exception {
        when(contractQueries.handle(any(GetContractsQuery.class)))
                .thenReturn(new PageImpl<>(List.of(contract(ContractStatus.PENDING)), PageRequest.of(0, 10), 1));

        contractsMvc.perform(get("/api/v1/contracts").param("businessId", "1").param("status", "PENDING")
                        .param("modality", "HOURLY").param("search", "tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));

        var captor = ArgumentCaptor.forClass(GetContractsQuery.class);
        verify(contractQueries).handle(captor.capture());
        assertEquals(ContractViewStatus.PENDING, captor.getValue().status());
        assertEquals("tech", captor.getValue().search());
    }

    @Test
    void listsContractsOfEmployee() throws Exception {
        when(contractQueries.handle(any(GetContractsQuery.class)))
                .thenReturn(new PageImpl<>(List.of(contract(ContractStatus.ACTIVE)), PageRequest.of(0, 10), 1));

        contractsMvc.perform(get("/api/v1/employees/3/contracts").param("businessId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].employeeId").value(3));

        var captor = ArgumentCaptor.forClass(GetContractsQuery.class);
        verify(contractQueries).handle(captor.capture());
        assertEquals(3L, captor.getValue().employeeId());
    }

    @Test
    void getsContractDetailOrNotFound() throws Exception {
        when(contractQueries.handle(new GetContractQuery(3L, 5L, 1L))).thenReturn(contract(ContractStatus.ACTIVE));
        when(contractQueries.handle(new GetContractQuery(3L, 404L, 1L))).thenThrow(new ContractNotFound(404L));

        contractsMvc.perform(get("/api/v1/employees/3/contracts/5").param("businessId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
        contractsMvc.perform(get("/api/v1/employees/3/contracts/404").param("businessId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("contract_not_found"));
    }

    @Test
    void updatesContractOrReportsConflict() throws Exception {
        when(contractCommands.handle(any(UpdateContractCommand.class)))
                .thenReturn(contract(ContractStatus.ACTIVE))
                .thenThrow(new ContractOverlap(3L));
        var body = """
                {"position":"Field technician","modality":"HOURLY","weeklyHours":40,
                 "startDate":"2026-10-25","endDate":"2027-04-26"}
                """;

        contractsMvc.perform(patch("/api/v1/employees/3/contracts/5").param("businessId", "1").principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        contractsMvc.perform(patch("/api/v1/employees/3/contracts/5").param("businessId", "1").principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("contract_overlap"));
    }

    @Test
    void renewsAndTerminatesContracts() throws Exception {
        when(contractCommands.handle(any(RenewContractCommand.class))).thenReturn(contract(ContractStatus.ACTIVE));
        when(contractCommands.handle(any(TerminateContractCommand.class)))
                .thenReturn(contract(ContractStatus.TERMINATED));

        contractsMvc.perform(post("/api/v1/employees/3/contracts/5/renewals").param("businessId", "1")
                        .principal(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2027-04-27\",\"endDate\":\"2028-04-26\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        contractsMvc.perform(post("/api/v1/employees/3/contracts/5/termination").param("businessId", "1")
                        .principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"))
                .andExpect(jsonPath("$.eligible").value(false));

        verify(contractCommands).handle(new TerminateContractCommand(3L, 5L, 1L, 9L));
    }

    @Test
    void activatesPendingContracts() throws Exception {
        when(contractCommands.handle(any(ActivateContractCommand.class))).thenReturn(contract(ContractStatus.ACTIVE));

        contractsMvc.perform(post("/api/v1/employees/3/contracts/5/activation").param("businessId", "1")
                        .principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(contractCommands).handle(new ActivateContractCommand(3L, 5L, 1L, 9L));
    }

    @Test
    void returnsContractHistory() throws Exception {
        when(contractQueries.history(new GetContractQuery(3L, 5L, 1L))).thenReturn(List.of(
                new ContractVersion(5L, 2, ContractAction.RENEWED, 9L, Map.of("status", "ACTIVE")),
                new ContractVersion(5L, 1, ContractAction.CREATED, 9L, Map.of("status", "PENDING"))));

        contractsMvc.perform(get("/api/v1/employees/3/contracts/5/history").param("businessId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action").value("RENEWED"))
                .andExpect(jsonPath("$[1].snapshot.status").value("PENDING"));
    }

    @Test
    void reportsEligibilityForRequestedDateOrToday() throws Exception {
        when(contractQueries.isEmployeeEligible(3L, 1L, TODAY)).thenReturn(true);
        when(contractQueries.isEmployeeEligible(3L, 1L, LocalDate.of(2030, 1, 1))).thenReturn(false);

        contractsMvc.perform(get("/api/v1/employees/3/eligibility").param("businessId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true));
        contractsMvc.perform(get("/api/v1/employees/3/eligibility").param("businessId", "1")
                        .param("date", "2030-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    void createsEmployee() throws Exception {
        when(employeeCommands.handle(any(CreateEmployeeCommand.class))).thenReturn(employee());
        when(profiles.fetchStaffProfileById(30L)).thenReturn(Optional.of(profile()));

        employeesMvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content("""
                        {"businessId":1,"corporateEmail":"ana@alivia.pe","photoUrl":"https://img/ana.png",
                         "photoPublicId":"ana","phoneNumber":"+51987654321","firstName":"Ana",
                         "lastName":"Pérez","dni":"12345678","birthDate":"1995-04-12","role":"TECH"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.corporateEmail").value("ana@alivia.pe"))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.role").value("TECH"))
                .andExpect(jsonPath("$.status").value(EmployeeStatus.ACTIVE.name()));
    }

    @Test
    void listsAndGetsEmployees() throws Exception {
        when(employeeQueries.handle(any(GetEmployeesQuery.class)))
                .thenReturn(new PageImpl<>(List.of(employee()), PageRequest.of(0, 20), 1));
        when(employeeQueries.handle(new GetEmployeeByIdQuery(3L, 1L))).thenReturn(employee());
        when(employeeQueries.handle(new GetEmployeeByIdQuery(3L, 2L))).thenThrow(new EmployeeNotInBusiness(3L, 2L));
        when(profiles.fetchStaffProfileById(30L)).thenReturn(Optional.of(profile()));

        employeesMvc.perform(get("/api/v1/employees").param("businessId", "1").param("role", "TECH")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(1));
        employeesMvc.perform(get("/api/v1/employees/3").param("businessId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("12345678"));
        employeesMvc.perform(get("/api/v1/employees/3").param("businessId", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("employee_not_in_business"));
    }

    @Test
    void updatesEmployeeAndReportsMissingProfile() throws Exception {
        when(employeeCommands.handle(any(UpdateEmployeeCommand.class))).thenReturn(employee());
        when(profiles.fetchStaffProfileById(30L)).thenReturn(Optional.of(profile())).thenReturn(Optional.empty());

        employeesMvc.perform(patch("/api/v1/employees/3").param("businessId", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CLEANING\"}"))
                .andExpect(status().isOk());
        employeesMvc.perform(patch("/api/v1/employees/3").param("businessId", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CLEANING\"}"))
                .andExpect(status().isNotFound());

        verify(employeeCommands, times(2)).handle(new UpdateEmployeeCommand(3L, 1L, null, null, null,
                null, null, null, EmployeeRole.CLEANING));
    }

    private static Contract contract(ContractStatus status) {
        var contract = new Contract(3L, 1L, "Field technician", ContractModality.HOURLY, 40,
                new ContractPeriod(TODAY.minusDays(1), TODAY.plusMonths(6)),
                status == ContractStatus.TERMINATED ? ContractStatus.ACTIVE : status, "D.L. 728");
        if (status == ContractStatus.TERMINATED) contract.terminate();
        contract.assignCode("CTR-ALV-2026-0005");
        ReflectionTestUtils.setField(contract, "id", 5L);
        return contract;
    }

    private static Employee employee() {
        var employee = new Employee(1L, 20L, 30L, EmployeeRole.TECH);
        ReflectionTestUtils.setField(employee, "id", 3L);
        return employee;
    }

    private static StaffProfileData profile() {
        return new StaffProfileData(30L, "ana@alivia.pe", "https://img/ana.png", "ana", "+51987654321",
                "Ana", "Pérez", "12345678", LocalDate.of(1995, 4, 12));
    }
}
