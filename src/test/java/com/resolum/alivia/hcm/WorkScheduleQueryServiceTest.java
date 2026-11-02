package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.application.internal.queryservices.WorkScheduleQueryServiceImpl;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotFound;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkScheduleQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkScheduleSummaryQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkSchedulesQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkSchedulePeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleViewStatus;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class WorkScheduleQueryServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);
    private static final Pageable PAGEABLE = PageRequest.of(0, 8);

    private WorkSchedulePersistenceRepository workSchedules;
    private ContractPersistenceRepository contracts;
    private WorkScheduleQueryServiceImpl service;
    private Root root;
    private CriteriaQuery criteriaQuery;
    private CriteriaBuilder builder;
    private Path path;

    @BeforeEach
    void setUp() {
        workSchedules = mock(WorkSchedulePersistenceRepository.class);
        contracts = mock(ContractPersistenceRepository.class);
        service = new WorkScheduleQueryServiceImpl(workSchedules, contracts, CLOCK);
        root = mock(Root.class);
        criteriaQuery = mock(CriteriaQuery.class);
        builder = mock(CriteriaBuilder.class);
        path = mock(Path.class);
        doReturn(path).when(root).get(anyString());
        var subquery = mock(Subquery.class, RETURNS_SELF);
        doReturn(subquery).when(criteriaQuery).subquery(Long.class);
        doReturn(root).when(subquery).from(any(Class.class));
    }

    @ParameterizedTest
    @EnumSource(WorkScheduleViewStatus.class)
    void listsSchedulesApplyingEveryFilter(WorkScheduleViewStatus status) {
        when(workSchedules.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty());

        service.handle(new GetWorkSchedulesQuery(1L, 3L, 5L, EmployeeRole.TECH, status, " Turno ", PAGEABLE));

        evaluateListSpecification();
        verify(builder).equal(path, 1L);
        verify(builder).equal(path, 3L);
        verify(builder).equal(path, 5L);
        verify(builder).equal(path, EmployeeRole.TECH);
        verify(builder, times(2)).like(any(), eq("%turno%"));
        switch (status) {
            case DRAFT -> verify(builder).equal(path, WorkScheduleStatus.DRAFT);
            case INACTIVE -> verify(builder).equal(path, WorkScheduleStatus.INACTIVE);
            default -> verify(builder).equal(path, WorkScheduleStatus.ACTIVE);
        }
    }

    @Test
    void listsSchedulesOfBusinessWithoutOptionalFilters() {
        when(workSchedules.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty());

        service.handle(new GetWorkSchedulesQuery(1L, null, null, null, null, "  ", PAGEABLE));

        evaluateListSpecification();
        verify(builder).equal(path, 1L);
        verify(builder, never()).like(any(), anyString());
        verify(criteriaQuery, never()).subquery(Long.class);
    }

    @Test
    void returnsOwnedScheduleOnly() {
        var schedule = schedule(TODAY, null);
        when(workSchedules.findById(12L)).thenReturn(Optional.of(schedule));
        when(workSchedules.findById(404L)).thenReturn(Optional.empty());

        assertSame(schedule, service.handle(new GetWorkScheduleQuery(3L, 12L, 1L)));
        assertThrows(WorkScheduleNotFound.class, () -> service.handle(new GetWorkScheduleQuery(3L, 404L, 1L)));
        assertThrows(WorkScheduleNotFound.class, () -> service.handle(new GetWorkScheduleQuery(4L, 12L, 1L)));
        assertThrows(WorkScheduleNotFound.class, () -> service.handle(new GetWorkScheduleQuery(3L, 12L, 2L)));
    }

    @Test
    void summarizesSchedulesInEffectOrScheduled() {
        var current = schedule(TODAY.minusDays(10), null);
        var upcoming = schedule(TODAY.plusDays(5), null);
        var finished = schedule(TODAY.minusMonths(2), TODAY.minusDays(1));
        when(workSchedules.findByBusinessIdAndStatus(1L, WorkScheduleStatus.ACTIVE))
                .thenReturn(List.of(current, upcoming, finished));

        var summary = service.handle(new GetWorkScheduleSummaryQuery(1L));

        assertEquals(2, summary.totalSchedules());
        assertEquals(1, summary.employeesWithSchedule());
        assertEquals(new BigDecimal("40.0"), summary.averageWeeklyHours());
    }

    @Test
    void summarizesAnEmptyBusiness() {
        when(workSchedules.findByBusinessIdAndStatus(1L, WorkScheduleStatus.ACTIVE)).thenReturn(List.of());

        var summary = service.handle(new GetWorkScheduleSummaryQuery(1L));

        assertEquals(0, summary.totalSchedules());
        assertEquals(BigDecimal.ZERO, summary.averageWeeklyHours());
    }

    @Test
    void findsTheAssociatedContract() {
        var contract = new Contract(3L, 1L, "Técnico", ContractModality.HOURLY, 40,
                new ContractPeriod(TODAY.minusMonths(1), TODAY.plusMonths(6)), ContractStatus.ACTIVE, null);
        when(contracts.findById(5L)).thenReturn(Optional.of(contract));

        assertEquals(Optional.of(contract), service.associatedContract(schedule(TODAY, null)));
    }

    private void evaluateListSpecification() {
        var captor = ArgumentCaptor.forClass(Specification.class);
        verify(workSchedules).findAll(captor.capture(), eq(PAGEABLE));
        captor.getValue().toPredicate(root, criteriaQuery, builder);
    }

    private static WorkSchedule schedule(LocalDate start, LocalDate end) {
        var schedule = new WorkSchedule(3L, 1L, 5L, "Turno", new WorkSchedulePeriod(start, end),
                WorkScheduleDomainTest.splitWeekdays(), 9L);
        schedule.activate();
        ReflectionTestUtils.setField(schedule, "id", 12L);
        return schedule;
    }
}
