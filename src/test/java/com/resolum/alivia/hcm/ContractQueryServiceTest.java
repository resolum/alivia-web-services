package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.application.internal.queryservices.ContractQueryServiceImpl;
import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.hcm.domain.model.queries.GetContractQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetContractsQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractAction;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractVersionRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class ContractQueryServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    private ContractPersistenceRepository contracts;
    private ContractVersionRepository versions;
    private EmployeeRepository employees;
    private ContractQueryServiceImpl service;

    private Root root;
    private CriteriaQuery criteriaQuery;
    private CriteriaBuilder builder;
    private Path path;

    @BeforeEach
    void setUp() {
        contracts = mock(ContractPersistenceRepository.class);
        versions = mock(ContractVersionRepository.class);
        employees = mock(EmployeeRepository.class);
        service = new ContractQueryServiceImpl(contracts, versions, employees, CLOCK);

        root = mock(Root.class);
        criteriaQuery = mock(CriteriaQuery.class);
        builder = mock(CriteriaBuilder.class);
        path = mock(Path.class);
        doReturn(path).when(root).get(anyString());
        when(employees.findById(3L)).thenReturn(Optional.of(new Employee(1L, 20L, 30L, EmployeeRole.TECH)));
    }

    @ParameterizedTest
    @EnumSource(ContractViewStatus.class)
    void listsContractsApplyingEveryFilter(ContractViewStatus status) {
        Page<Contract> page = new PageImpl<>(List.of(contract(3L, 1L)), PAGEABLE, 1);
        when(contracts.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(page);

        var result = service.handle(new GetContractsQuery(1L, 3L, status, ContractModality.HOURLY,
                " Tech ", PAGEABLE));

        assertSame(page, result);
        evaluateListSpecification();
        verify(builder).equal(path, 1L);
        verify(builder).equal(path, 3L);
        verify(builder).equal(path, ContractModality.HOURLY);
        verify(builder, times(2)).like(any(), eq("%tech%"));
        switch (status) {
            case ACTIVE -> verify(builder).greaterThanOrEqualTo(path, TODAY);
            case PENDING -> verify(builder).greaterThan(path, TODAY);
            case EXPIRED -> verify(builder).notEqual(path, ContractStatus.TERMINATED);
            case TERMINATED -> verify(builder).equal(path, ContractStatus.TERMINATED);
        }
    }

    @Test
    void listsContractsOfBusinessWithoutOptionalFilters() {
        when(contracts.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty());

        service.handle(new GetContractsQuery(1L, null, null, null, "   ", PAGEABLE));

        evaluateListSpecification();
        verify(builder).equal(path, 1L);
        verify(builder, never()).like(any(), anyString());
        verify(builder, never()).greaterThan(any(Path.class), any(LocalDate.class));
    }

    @Test
    void returnsOwnedContractAndItsHistory() {
        var contract = contract(3L, 1L);
        var versionList = List.of(new ContractVersion(5L, 2, ContractAction.UPDATED, 9L, Map.of()),
                new ContractVersion(5L, 1, ContractAction.CREATED, 9L, Map.of()));
        when(contracts.findById(5L)).thenReturn(Optional.of(contract));
        when(versions.findByContractIdOrderByVersionDesc(5L)).thenReturn(versionList);

        assertSame(contract, service.handle(new GetContractQuery(3L, 5L, 1L)));
        assertEquals(versionList, service.history(new GetContractQuery(3L, 5L, 1L)));
    }

    @Test
    void hidesContractsOutsideEmployeeOrBusiness() {
        when(contracts.findById(404L)).thenReturn(Optional.empty());
        when(contracts.findById(5L)).thenReturn(Optional.of(contract(3L, 1L)));

        assertThrows(ContractNotFound.class, () -> service.handle(new GetContractQuery(3L, 404L, 1L)));
        assertThrows(ContractNotFound.class, () -> service.handle(new GetContractQuery(4L, 5L, 1L)));
        assertThrows(ContractNotFound.class, () -> service.history(new GetContractQuery(3L, 5L, 2L)));
        verifyNoInteractions(versions);
    }

    @Test
    void reportsEmployeeEligibilityFromActiveContracts() {
        when(contracts.findAll(any(Specification.class)))
                .thenReturn(List.of(contract(3L, 1L)))
                .thenReturn(List.of());

        assertTrue(service.isEmployeeEligible(3L, 1L, TODAY));
        assertFalse(service.isEmployeeEligible(3L, 1L, TODAY.plusYears(5)));

        var captor = ArgumentCaptor.forClass(Specification.class);
        verify(contracts, times(2)).findAll(captor.capture());
        captor.getValue().toPredicate(root, criteriaQuery, builder);
        verify(builder).equal(path, 3L);
        verify(builder).equal(path, ContractStatus.ACTIVE);
        verify(builder).lessThanOrEqualTo(path, TODAY.plusYears(5));
        verify(builder).greaterThanOrEqualTo(path, TODAY.plusYears(5));
    }

    @Test
    void rejectsEligibilityForUnknownOrForeignEmployees() {
        when(employees.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EmployeeNotFound.class, () -> service.isEmployeeEligible(99L, 1L, TODAY));
        assertThrows(EmployeeNotInBusiness.class, () -> service.isEmployeeEligible(3L, 2L, TODAY));
        verify(contracts, never()).findAll(any(Specification.class));
    }

    private void evaluateListSpecification() {
        var captor = ArgumentCaptor.forClass(Specification.class);
        verify(contracts).findAll(captor.capture(), eq(PAGEABLE));
        captor.getValue().toPredicate(root, criteriaQuery, builder);
    }

    private static Contract contract(Long employeeId, Long businessId) {
        var contract = new Contract(employeeId, businessId, "Field technician", ContractModality.HOURLY, 40,
                new ContractPeriod(TODAY.minusMonths(1), TODAY.plusMonths(6)), ContractStatus.ACTIVE, null);
        ReflectionTestUtils.setField(contract, "id", 5L);
        return contract;
    }
}
