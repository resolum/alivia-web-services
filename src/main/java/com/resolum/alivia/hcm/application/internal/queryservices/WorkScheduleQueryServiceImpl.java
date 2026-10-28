package com.resolum.alivia.hcm.application.internal.queryservices;

import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotFound;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkScheduleQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkScheduleSummaryQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkSchedulesQuery;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleSummary;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleViewStatus;
import com.resolum.alivia.hcm.domain.services.WorkScheduleQueryService;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

@Service
public class WorkScheduleQueryServiceImpl implements WorkScheduleQueryService {
    private final WorkSchedulePersistenceRepository workSchedules;
    private final ContractPersistenceRepository contracts;
    private final Clock clock;

    public WorkScheduleQueryServiceImpl(WorkSchedulePersistenceRepository workSchedules,
                                        ContractPersistenceRepository contracts, Clock clock) {
        this.workSchedules = workSchedules;
        this.contracts = contracts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkSchedule> handle(GetWorkSchedulesQuery query) {
        return workSchedules.findAll((root, criteria, builder) -> {
            var filters = new ArrayList<Predicate>();
            filters.add(builder.equal(root.get("businessId"), query.businessId()));
            if (query.employeeId() != null) filters.add(builder.equal(root.get("employeeId"), query.employeeId()));
            if (query.contractId() != null) filters.add(builder.equal(root.get("contractId"), query.contractId()));
            if (query.role() != null) {
                var employees = criteria.subquery(Long.class);
                var employee = employees.from(Employee.class);
                employees.select(employee.get("id")).where(builder.equal(employee.get("role"), query.role()));
                filters.add(root.get("employeeId").in(employees));
            }
            if (query.status() != null) {
                var today = LocalDate.now(clock);
                var active = builder.equal(root.get("status"), WorkScheduleStatus.ACTIVE);
                var started = builder.lessThanOrEqualTo(root.<LocalDate>get("startDate"), today);
                var ended = builder.and(builder.isNotNull(root.get("endDate")),
                        builder.lessThan(root.<LocalDate>get("endDate"), today));
                filters.add(switch (query.status()) {
                    case DRAFT -> builder.equal(root.get("status"), WorkScheduleStatus.DRAFT);
                    case INACTIVE -> builder.equal(root.get("status"), WorkScheduleStatus.INACTIVE);
                    case SCHEDULED -> builder.and(active, builder.not(started));
                    case ACTIVE -> builder.and(active, started, builder.not(ended));
                    case FINISHED -> builder.and(active, ended);
                });
            }
            if (query.search() != null && !query.search().isBlank()) {
                String term = "%" + query.search().trim().toLowerCase() + "%";
                filters.add(builder.or(builder.like(builder.lower(root.get("code")), term),
                        builder.like(builder.lower(root.get("name")), term)));
            }
            return builder.and(filters.toArray(Predicate[]::new));
        }, query.pageable());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkSchedule handle(GetWorkScheduleQuery query) {
        var workSchedule = workSchedules.findById(query.workScheduleId())
                .orElseThrow(() -> new WorkScheduleNotFound(query.workScheduleId()));
        if (!workSchedule.getEmployeeId().equals(query.employeeId())
                || !workSchedule.getBusinessId().equals(query.businessId())) {
            throw new WorkScheduleNotFound(query.workScheduleId());
        }
        return workSchedule;
    }

    /** Counts the schedules in effect or about to start; drafts, finished and inactive ones are left out. */
    @Override
    @Transactional(readOnly = true)
    public WorkScheduleSummary handle(GetWorkScheduleSummaryQuery query) {
        var today = LocalDate.now(clock);
        var current = workSchedules.findByBusinessIdAndStatus(query.businessId(), WorkScheduleStatus.ACTIVE)
                .stream()
                .filter(schedule -> schedule.viewStatus(today) != WorkScheduleViewStatus.FINISHED)
                .toList();
        if (current.isEmpty()) return new WorkScheduleSummary(0, 0, BigDecimal.ZERO);
        var totalHours = current.stream().map(WorkSchedule::weeklyHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        long employees = current.stream().map(WorkSchedule::getEmployeeId).distinct().count();
        return new WorkScheduleSummary(current.size(), employees,
                totalHours.divide(BigDecimal.valueOf(current.size()), 1, RoundingMode.HALF_UP));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Contract> associatedContract(WorkSchedule workSchedule) {
        return contracts.findById(workSchedule.getContractId());
    }
}
