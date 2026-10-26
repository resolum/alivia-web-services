package com.resolum.alivia.hcm.application.internal.queryservices;

import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeeByIdQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeesQuery;
import com.resolum.alivia.hcm.domain.services.EmployeeQueryService;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
public class EmployeeQueryServiceImpl implements EmployeeQueryService {
    private final EmployeeRepository repository;

    public EmployeeQueryServiceImpl(EmployeeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Employee> handle(GetEmployeesQuery query) {
        requirePositive(query.businessId(), "Business identifier");
        return repository.findAll((root, ignored, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("businessId"), query.businessId()));
            if (query.status() != null) predicates.add(builder.equal(root.get("status"), query.status()));
            if (query.role() != null) predicates.add(builder.equal(root.get("role"), query.role()));
            return builder.and(predicates.toArray(Predicate[]::new));
        }, query.pageable());
    }

    @Override
    @Transactional(readOnly = true)
    public Employee handle(GetEmployeeByIdQuery query) {
        var employee = repository.findById(query.employeeId())
                .orElseThrow(() -> new EmployeeNotFound(query.employeeId()));
        if (!employee.belongsTo(query.businessId())) {
            throw new EmployeeNotInBusiness(query.employeeId(), query.businessId());
        }
        return employee;
    }

    private static void requirePositive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " must be positive.");
    }
}
