package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.application.internal.commandservices.WorkScheduleCommandServiceImpl;
import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotFound;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleOutsideContract;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleOverlap;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.commands.CreateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.DeactivateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkSchedulePeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class WorkScheduleCommandServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);

    private WorkSchedulePersistenceRepository workSchedules;
    private ContractPersistenceRepository contracts;
    private EmployeeRepository employees;
    private WorkScheduleCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        workSchedules = mock(WorkSchedulePersistenceRepository.class);
        contracts = mock(ContractPersistenceRepository.class);
        employees = mock(EmployeeRepository.class);
        service = new WorkScheduleCommandServiceImpl(workSchedules, contracts, employees, CLOCK);

        when(employees.findById(3L)).thenReturn(Optional.of(new Employee(1L, 20L, 30L, EmployeeRole.TECH)));
        when(contracts.findById(5L)).thenReturn(Optional.of(contract(3L, ContractStatus.ACTIVE, TODAY.plusMonths(6))));
        when(workSchedules.saveAndFlush(any(WorkSchedule.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 12L));
        when(workSchedules.save(any(WorkSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishesScheduleInsideItsContractWithGeneratedCode() {
        var schedule = service.handle(create(5L, null, false));

        assertEquals("HOR-2026-0012", schedule.getCode());
        assertEquals(WorkScheduleStatus.ACTIVE, schedule.getStatus());
        assertEquals(5L, schedule.getContractId());
        verify(workSchedules).existsActiveOverlapping(eq(3L), eq(TODAY), eq(LocalDate.of(9999, 12, 31)), isNull());
    }

    @Test
    void savesDraftLinkedToContractWithoutShifts() {
        var schedule = service.handle(new CreateWorkScheduleCommand(3L, 5L, 1L, "Borrador", TODAY, null,
                List.of(), true, 9L));

        assertEquals(WorkScheduleStatus.DRAFT, schedule.getStatus());
        assertEquals(5L, schedule.getContractId());
        verify(workSchedules, never()).existsActiveOverlapping(anyLong(), any(), any(), any());
    }

    @Test
    void rejectsSchedulesOutsideTheContractPeriodOrOnClosedContracts() {
        assertThrows(WorkScheduleOutsideContract.class, () -> service.handle(create(5L, TODAY.plusMonths(7), false)));

        when(contracts.findById(6L)).thenReturn(Optional.of(withId(
                contract(3L, ContractStatus.TERMINATED, TODAY.plusMonths(6)), 6L)));
        assertThrows(WorkScheduleOutsideContract.class, () -> service.handle(create(6L, null, true)));
        verify(workSchedules, never()).saveAndFlush(any());
    }

    @Test
    void rejectsContractsOfAnotherEmployee() {
        when(contracts.findById(7L)).thenReturn(Optional.of(withId(
                contract(4L, ContractStatus.ACTIVE, TODAY.plusMonths(6)), 7L)));

        assertThrows(ContractNotFound.class, () -> service.handle(create(7L, null, false)));
    }

    @Test
    void rejectsOverlappingActiveSchedules() {
        when(workSchedules.existsActiveOverlapping(anyLong(), any(), any(), any())).thenReturn(true);

        assertThrows(WorkScheduleOverlap.class, () -> service.handle(create(5L, TODAY.plusMonths(1), false)));
        verify(workSchedules).existsActiveOverlapping(3L, TODAY, TODAY.plusMonths(1), null);
    }

    @Test
    void publishesDraftOnUpdateExcludingItself() {
        var draft = withId(new WorkSchedule(3L, 1L, 5L, "Borrador", new WorkSchedulePeriod(TODAY, null),
                List.of(), 9L), 12L);
        when(workSchedules.findById(12L)).thenReturn(Optional.of(draft));

        var schedule = service.handle(new UpdateWorkScheduleCommand(3L, 12L, 1L, "Turno Rotativo A", TODAY,
                null, WorkScheduleDomainTest.splitWeekdays(), false, 9L));

        assertEquals(WorkScheduleStatus.ACTIVE, schedule.getStatus());
        assertEquals("Turno Rotativo A", schedule.getName());
        verify(workSchedules).existsActiveOverlapping(eq(3L), eq(TODAY), any(), eq(12L));
    }

    @Test
    void deactivatesOwnedScheduleOnly() {
        var schedule = withId(new WorkSchedule(3L, 1L, 5L, "Turno", new WorkSchedulePeriod(TODAY, null),
                WorkScheduleDomainTest.splitWeekdays(), 9L), 12L);
        when(workSchedules.findById(12L)).thenReturn(Optional.of(schedule));
        when(employees.findById(4L)).thenReturn(Optional.of(new Employee(1L, 21L, 31L, EmployeeRole.TECH)));

        assertThrows(WorkScheduleNotFound.class,
                () -> service.handle(new DeactivateWorkScheduleCommand(4L, 12L, 1L, 9L)));
        assertThrows(EmployeeNotInBusiness.class,
                () -> service.handle(new DeactivateWorkScheduleCommand(3L, 12L, 2L, 9L)));
        assertEquals(WorkScheduleStatus.INACTIVE,
                service.handle(new DeactivateWorkScheduleCommand(3L, 12L, 1L, 9L)).getStatus());
    }

    private static CreateWorkScheduleCommand create(Long contractId, LocalDate endDate, boolean draft) {
        return new CreateWorkScheduleCommand(3L, contractId, 1L, "Jornada Ordinaria Diurna", TODAY, endDate,
                WorkScheduleDomainTest.splitWeekdays(), draft, 9L);
    }

    private static Contract contract(Long employeeId, ContractStatus status, LocalDate endDate) {
        var contract = withId(new Contract(employeeId, 1L, "Técnico", ContractModality.HOURLY, 40,
                new ContractPeriod(TODAY.minusMonths(1), endDate), ContractStatus.ACTIVE, null), 5L);
        if (status == ContractStatus.TERMINATED) contract.terminate();
        return contract;
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
