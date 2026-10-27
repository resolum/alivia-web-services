package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.application.internal.commandservices.EmployeeCommandServiceImpl;
import com.resolum.alivia.hcm.application.internal.queryservices.EmployeeQueryServiceImpl;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.entities.EmployeeChangeLog;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeeByIdQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeesQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeStatus;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeChangeLogRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade.StaffProfileData;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class EmployeeServicesTest {
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 4, 12);
    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    private EmployeeRepository repository;
    private EmployeeChangeLogRepository changeLogs;
    private ProfilesContextFacade profiles;
    private IamContextFacade iam;
    private EmployeeCommandServiceImpl commandService;
    private EmployeeQueryServiceImpl queryService;

    @BeforeEach
    void setUp() {
        repository = mock(EmployeeRepository.class);
        changeLogs = mock(EmployeeChangeLogRepository.class);
        profiles = mock(ProfilesContextFacade.class);
        iam = mock(IamContextFacade.class);
        commandService = new EmployeeCommandServiceImpl(repository, changeLogs, profiles, iam);
        queryService = new EmployeeQueryServiceImpl(repository);
        when(repository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsEmployeeWithStaffProfileAndCompanyUser() {
        when(profiles.createStaffProfile("ana@alivia.pe", "https://img/ana.png", "ana", "+51987654321",
                "Ana", "Pérez", "12345678", BIRTH_DATE)).thenReturn(30L);
        when(iam.registerCompanyUser(eq("ana@alivia.pe"), startsWith("Tmp!A9"), eq("Ana Pérez"),
                eq("+51987654321"), eq("SUBSCRIPTION_MANAGER"), eq(1L))).thenReturn(20L);

        var employee = commandService.handle(createCommand(1L, EmployeeRole.SUBSCRIPTION_MANAGER));

        assertEquals(1L, employee.getBusinessId());
        assertEquals(20L, employee.getUserId());
        assertEquals(30L, employee.getStaffProfileId());
        assertEquals(EmployeeRole.SUBSCRIPTION_MANAGER, employee.getRole());
        assertEquals(EmployeeStatus.ACTIVE, employee.getStatus());
    }

    @Test
    void registersOperationalEmployeesAsTechnicians() {
        when(profiles.createStaffProfile(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(30L);
        when(iam.registerCompanyUser(any(), any(), any(), any(), eq("TECHNICIAN"), eq(1L))).thenReturn(20L);

        var employee = commandService.handle(createCommand(1L, EmployeeRole.CLEANING));

        assertEquals(EmployeeRole.CLEANING, employee.getRole());
        verify(iam).registerCompanyUser(any(), any(), any(), any(), eq("TECHNICIAN"), eq(1L));
    }

    @Test
    void rejectsEmployeeCreationWithoutBusinessOrRole() {
        when(profiles.createStaffProfile(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(30L);

        assertThrows(IllegalArgumentException.class, () -> commandService.handle(createCommand(null, EmployeeRole.TECH)));
        assertThrows(IllegalArgumentException.class, () -> commandService.handle(createCommand(0L, EmployeeRole.TECH)));
        assertThrows(IllegalArgumentException.class, () -> commandService.handle(createCommand(1L, null)));
        verifyNoInteractions(iam);
        verify(repository, never()).save(any());
    }

    @Test
    void updatesEmployeeAndRecordsOnlyChangedFields() {
        var employee = employee(1L);
        when(repository.findById(3L)).thenReturn(Optional.of(employee));
        when(profiles.fetchStaffProfileById(30L)).thenReturn(Optional.of(profile()));

        var updated = commandService.handle(new UpdateEmployeeCommand(3L, 1L, null, null, "+51900000000",
                "Ana María", "Pérez", null, EmployeeRole.CLEANING));

        assertEquals(EmployeeRole.CLEANING, updated.getRole());
        verify(profiles).updateStaffProfile(30L, "Ana María", "Pérez", BIRTH_DATE, "+51900000000",
                "https://img/ana.png", "ana");
        var captor = ArgumentCaptor.forClass(EmployeeChangeLog.class);
        verify(changeLogs).save(captor.capture());
        var changes = captor.getValue().getChanges();
        assertEquals("Ana", changes.get("firstName.previous"));
        assertEquals("Ana María", changes.get("firstName.current"));
        assertEquals("+51900000000", changes.get("phoneNumber.current"));
        assertEquals("TECH", changes.get("role.previous"));
        assertEquals("CLEANING", changes.get("role.current"));
        assertFalse(changes.containsKey("lastName.current"));
        assertFalse(changes.containsKey("birthDate.current"));
    }

    @Test
    void skipsChangeLogWhenNothingChanged() {
        when(repository.findById(3L)).thenReturn(Optional.of(employee(1L)));
        when(profiles.fetchStaffProfileById(30L)).thenReturn(Optional.of(profile()));

        commandService.handle(new UpdateEmployeeCommand(3L, 1L, null, null, null, null, null,
                BIRTH_DATE, null));

        verify(profiles).updateStaffProfile(30L, "Ana", "Pérez", BIRTH_DATE, "+51987654321",
                "https://img/ana.png", "ana");
        verifyNoInteractions(changeLogs);
    }

    @Test
    void rejectsUpdatesForMissingOrForeignEmployeesAndProfiles() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        when(repository.findById(3L)).thenReturn(Optional.of(employee(1L)));
        when(profiles.fetchStaffProfileById(30L)).thenReturn(Optional.empty());

        assertThrows(EmployeeNotFound.class, () -> commandService.handle(emptyUpdate(99L, 1L)));
        assertThrows(EmployeeNotInBusiness.class, () -> commandService.handle(emptyUpdate(3L, 2L)));
        assertThrows(StaffProfileNotFoundException.class, () -> commandService.handle(emptyUpdate(3L, 1L)));
        verify(repository, never()).save(any());
    }

    @Test
    void listsEmployeesWithOptionalFilters() {
        when(repository.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty());
        Root root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        doReturn(path).when(root).get(anyString());

        queryService.handle(new GetEmployeesQuery(1L, EmployeeStatus.ACTIVE, EmployeeRole.TECH, PAGEABLE));
        queryService.handle(new GetEmployeesQuery(1L, null, null, PAGEABLE));

        var captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository, times(2)).findAll(captor.capture(), eq(PAGEABLE));
        for (var specification : captor.getAllValues()) {
            specification.toPredicate(root, mock(CriteriaQuery.class), builder);
        }
        verify(builder, times(2)).equal(path, 1L);
        verify(builder).equal(path, EmployeeStatus.ACTIVE);
        verify(builder).equal(path, EmployeeRole.TECH);
        assertThrows(IllegalArgumentException.class,
                () -> queryService.handle(new GetEmployeesQuery(null, null, null, PAGEABLE)));
        assertThrows(IllegalArgumentException.class,
                () -> queryService.handle(new GetEmployeesQuery(-1L, null, null, PAGEABLE)));
    }

    @Test
    void returnsEmployeeOnlyInsideItsBusiness() {
        var employee = employee(1L);
        when(repository.findById(3L)).thenReturn(Optional.of(employee));
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertSame(employee, queryService.handle(new GetEmployeeByIdQuery(3L, 1L)));
        assertThrows(EmployeeNotInBusiness.class, () -> queryService.handle(new GetEmployeeByIdQuery(3L, 2L)));
        assertThrows(EmployeeNotFound.class, () -> queryService.handle(new GetEmployeeByIdQuery(99L, 1L)));
    }

    private static CreateEmployeeCommand createCommand(Long businessId, EmployeeRole role) {
        return new CreateEmployeeCommand(businessId, "ana@alivia.pe", "https://img/ana.png", "ana",
                "+51987654321", "Ana", "Pérez", "12345678", BIRTH_DATE, role);
    }

    private static UpdateEmployeeCommand emptyUpdate(Long employeeId, Long businessId) {
        return new UpdateEmployeeCommand(employeeId, businessId, null, null, null, null, null, null, null);
    }

    private static Employee employee(Long businessId) {
        var employee = new Employee(businessId, 20L, 30L, EmployeeRole.TECH);
        ReflectionTestUtils.setField(employee, "id", 3L);
        return employee;
    }

    private static StaffProfileData profile() {
        return new StaffProfileData(30L, "ana@alivia.pe", "https://img/ana.png", "ana", "+51987654321",
                "Ana", "Pérez", "12345678", BIRTH_DATE);
    }
}
