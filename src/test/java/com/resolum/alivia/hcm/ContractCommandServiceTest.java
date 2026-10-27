package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.application.internal.commandservices.ContractCommandServiceImpl;
import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.ContractOverlap;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.RenewContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.TerminateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateContractCommand;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractAction;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractVersionRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class ContractCommandServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);

    private ContractPersistenceRepository contracts;
    private ContractVersionRepository versions;
    private EmployeeRepository employees;
    private ContractCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        contracts = mock(ContractPersistenceRepository.class);
        versions = mock(ContractVersionRepository.class);
        employees = mock(EmployeeRepository.class);
        service = new ContractCommandServiceImpl(contracts, versions, employees, CLOCK);

        when(employees.findById(3L)).thenReturn(Optional.of(new Employee(1L, 20L, 30L, EmployeeRole.TECH)));
        when(contracts.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsPendingContractWithGeneratedCodeAndFirstVersion() {
        when(contracts.saveAndFlush(any(Contract.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        var contract = service.handle(new CreateContractCommand(3L, 1L, " Field technician ",
                ContractModality.HOURLY, 40, TODAY, TODAY.plusMonths(6), null, "  ", 9L));

        assertEquals("CTR-ALV-2026-0005", contract.getCode());
        assertEquals(ContractStatus.PENDING, contract.getStatus());
        assertEquals("Field technician", contract.getPosition());
        verify(contracts).existsOverlapping(eq(3L), eq(TODAY), eq(TODAY.plusMonths(6)),
                eq(EnumSet.of(ContractStatus.PENDING, ContractStatus.ACTIVE)), isNull());
        var version = savedVersion();
        assertEquals(1, version.getVersion());
        assertEquals(ContractAction.CREATED, version.getAction());
        assertEquals(9L, version.getActorUserId());
        assertEquals("CTR-ALV-2026-0005", version.getSnapshot().get("code"));
        assertEquals("PENDING", version.getSnapshot().get("status"));
        assertEquals("null", version.getSnapshot().get("notes"));
    }

    @Test
    void createsContractWithRequestedInitialStatus() {
        when(contracts.saveAndFlush(any(Contract.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 12L));

        var contract = service.handle(new CreateContractCommand(3L, 1L, "Supervisor",
                ContractModality.SERVICE, null, TODAY, TODAY.plusMonths(6), ContractStatus.ACTIVE,
                "Indefinite", 9L));

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals("CTR-ALV-2026-0012", contract.getCode());
    }

    @Test
    void rejectsOverlappingContracts() {
        when(contracts.existsOverlapping(anyLong(), any(), any(), any(), any())).thenReturn(true);

        assertThrows(ContractOverlap.class, () -> service.handle(new CreateContractCommand(3L, 1L,
                "Tech", ContractModality.SERVICE, null, TODAY, TODAY.plusMonths(1), null, null, 9L)));
        verify(contracts, never()).saveAndFlush(any());
        verifyNoInteractions(versions);
    }

    @Test
    void rejectsUnknownEmployeeOrEmployeeFromAnotherBusiness() {
        when(employees.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EmployeeNotFound.class, () -> service.handle(new CreateContractCommand(99L, 1L,
                "Tech", ContractModality.SERVICE, null, TODAY, TODAY.plusMonths(1), null, null, 9L)));
        assertThrows(EmployeeNotInBusiness.class, () -> service.handle(new TerminateContractCommand(3L, 5L,
                2L, 9L)));
        verifyNoInteractions(versions);
    }

    @Test
    void updatesContractExcludingItselfFromOverlapCheck() {
        when(contracts.findById(5L)).thenReturn(Optional.of(existingContract(3L, 1L)));
        when(versions.countByContractId(5L)).thenReturn(1L);

        var contract = service.handle(new UpdateContractCommand(3L, 5L, 1L, "Supervisor",
                ContractModality.SERVICE, null, TODAY, TODAY.plusYears(1), "Updated", 9L));

        assertEquals("Supervisor", contract.getPosition());
        assertEquals(ContractModality.SERVICE, contract.getModality());
        assertEquals("Updated", contract.getNotes());
        verify(contracts).existsOverlapping(eq(3L), eq(TODAY), eq(TODAY.plusYears(1)), any(), eq(5L));
        var version = savedVersion();
        assertEquals(2, version.getVersion());
        assertEquals(ContractAction.UPDATED, version.getAction());
        assertEquals("Supervisor", version.getSnapshot().get("position"));
    }

    @Test
    void renewsContractAndRecordsVersion() {
        when(contracts.findById(5L)).thenReturn(Optional.of(existingContract(3L, 1L)));
        when(versions.countByContractId(5L)).thenReturn(2L);

        var contract = service.handle(new RenewContractCommand(3L, 5L, 1L,
                TODAY.plusMonths(6), TODAY.plusYears(2), 9L));

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals(TODAY.plusYears(2), contract.getEndDate());
        var version = savedVersion();
        assertEquals(3, version.getVersion());
        assertEquals(ContractAction.RENEWED, version.getAction());
    }

    @Test
    void terminatesContractWithoutCheckingOverlaps() {
        when(contracts.findById(5L)).thenReturn(Optional.of(existingContract(3L, 1L)));

        var contract = service.handle(new TerminateContractCommand(3L, 5L, 1L, 9L));

        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
        assertEquals(ContractAction.TERMINATED, savedVersion().getAction());
        verify(contracts, never()).existsOverlapping(any(), any(), any(), any(), any());
    }

    @Test
    void hidesContractsThatDoNotBelongToEmployeeOrBusiness() {
        when(contracts.findById(404L)).thenReturn(Optional.empty());
        when(contracts.findById(5L)).thenReturn(Optional.of(existingContract(4L, 1L)));
        when(contracts.findById(6L)).thenReturn(Optional.of(existingContract(3L, 2L)));

        assertThrows(ContractNotFound.class, () -> service.handle(new TerminateContractCommand(3L, 404L, 1L, 9L)));
        assertThrows(ContractNotFound.class, () -> service.handle(new TerminateContractCommand(3L, 5L, 1L, 9L)));
        assertThrows(ContractNotFound.class, () -> service.handle(new RenewContractCommand(3L, 6L, 1L,
                TODAY, TODAY.plusYears(1), 9L)));
        verifyNoInteractions(versions);
    }

    private ContractVersion savedVersion() {
        var captor = ArgumentCaptor.forClass(ContractVersion.class);
        verify(versions).save(captor.capture());
        return captor.getValue();
    }

    private static Contract existingContract(Long employeeId, Long businessId) {
        var contract = new Contract(employeeId, businessId, "Field technician", ContractModality.HOURLY, 40,
                new ContractPeriod(TODAY.minusMonths(1), TODAY.plusMonths(6)), ContractStatus.ACTIVE, null);
        contract.assignCode("CTR-ALV-2026-0005");
        return withId(contract, 5L);
    }

    private static Contract withId(Contract contract, Long id) {
        ReflectionTestUtils.setField(contract, "id", id);
        return contract;
    }
}
