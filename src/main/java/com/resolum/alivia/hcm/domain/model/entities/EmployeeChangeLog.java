package com.resolum.alivia.hcm.domain.model.entities;

import com.resolum.alivia.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Getter
public class EmployeeChangeLog extends AuditableModel {
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> changes;

    protected EmployeeChangeLog() {
    }

    public EmployeeChangeLog(Long employeeId, Long businessId, Map<String, String> changes) {
        if (employeeId == null || businessId == null || changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException("Employee, business and at least one change are required.");
        }
        this.employeeId = employeeId;
        this.businessId = businessId;
        this.changes = Map.copyOf(changes);
    }
}
