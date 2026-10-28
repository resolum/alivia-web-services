package com.resolum.alivia.hcm.application.internal.queryservices;

import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.hcm.domain.model.queries.GetContractQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetContractsQuery;
import com.resolum.alivia.hcm.domain.services.ContractQueryService;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractVersionRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContractQueryServiceImpl implements ContractQueryService {
    private final ContractPersistenceRepository contracts;
    private final ContractVersionRepository versions;
    private final EmployeeRepository employees;
    private final Clock clock;

    public ContractQueryServiceImpl(ContractPersistenceRepository contracts,
                                    ContractVersionRepository versions,
                                    EmployeeRepository employees, Clock clock) {
        this.contracts = contracts;
        this.versions = versions;
        this.employees = employees;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> handle(GetContractsQuery query) {
        return contracts.findAll((root, ignored, builder) -> {
            var filters = new ArrayList<Predicate>();
            filters.add(builder.equal(root.get("businessId"), query.businessId()));
            if (query.employeeId() != null) filters.add(builder.equal(root.get("employeeId"), query.employeeId()));
            if (query.status() != null) {
                var today = LocalDate.now(clock);
                switch (query.status()) {
                    case ACTIVE -> filters.add(builder.and(
                            builder.equal(root.get("status"), ContractStatus.ACTIVE),
                            builder.lessThanOrEqualTo(root.<LocalDate>get("startDate"), today),
                            builder.greaterThanOrEqualTo(root.<LocalDate>get("endDate"), today)));
                    case PENDING -> filters.add(builder.or(
                            builder.equal(root.get("status"), ContractStatus.PENDING),
                            builder.and(builder.equal(root.get("status"), ContractStatus.ACTIVE),
                                    builder.greaterThan(root.<LocalDate>get("startDate"), today))));
                    case EXPIRED -> filters.add(builder.and(
                            builder.notEqual(root.get("status"), ContractStatus.TERMINATED),
                            builder.lessThan(root.<LocalDate>get("endDate"), today)));
                    case TERMINATED -> filters.add(
                            builder.equal(root.get("status"), ContractStatus.TERMINATED));
                }
            }
            if (query.modality() != null) filters.add(builder.equal(root.get("modality"), query.modality()));
            if (query.search() != null && !query.search().isBlank()) {
                String term = "%" + query.search().trim().toLowerCase() + "%";
                filters.add(builder.or(builder.like(builder.lower(root.get("code")), term),
                        builder.like(builder.lower(root.get("position")), term)));
            }
            return builder.and(filters.toArray(Predicate[]::new));
        }, query.pageable());
    }

    @Override
    @Transactional(readOnly = true)
    public Contract handle(GetContractQuery query) {
        var contract = contracts.findById(query.contractId())
                .orElseThrow(() -> new ContractNotFound(query.contractId()));
        if (!contract.getEmployeeId().equals(query.employeeId())
                || !contract.getBusinessId().equals(query.businessId())) {
            throw new ContractNotFound(query.contractId());
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractVersion> history(GetContractQuery query) {
        handle(query);
        return versions.findByContractIdOrderByVersionDesc(query.contractId());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmployeeEligible(Long employeeId, Long businessId, LocalDate date) {
        var employee = employees.findById(employeeId).orElseThrow(() -> new EmployeeNotFound(employeeId));
        if (!employee.belongsTo(businessId)) throw new EmployeeNotInBusiness(employeeId, businessId);
        return contracts.findAll((root, ignored, builder) -> builder.and(
                builder.equal(root.get("employeeId"), employeeId),
                builder.equal(root.get("status"), com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus.ACTIVE),
                builder.lessThanOrEqualTo(root.get("startDate"), date),
                builder.greaterThanOrEqualTo(root.get("endDate"), date))).stream().findAny().isPresent();
    }
}
