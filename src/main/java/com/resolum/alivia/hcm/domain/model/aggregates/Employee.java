package com.resolum.alivia.hcm.domain.model.aggregates;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeStatus;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(name = "employees", uniqueConstraints = {
        @UniqueConstraint(name = "uk_employees_user_id", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_employees_staff_profile_id", columnNames = "staff_profile_id")
})
@Getter
public class Employee extends AuditableAbstractAggregateRoot<Employee> {
    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "staff_profile_id", nullable = false)
    private Long staffProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmployeeRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmployeeStatus status;

    protected Employee() {
    }

    public Employee(Long businessId, Long userId, Long staffProfileId, EmployeeRole role) {
        requirePositive(businessId, "Business identifier");
        requirePositive(userId, "User identifier");
        requirePositive(staffProfileId, "Staff profile identifier");
        if (role == null) throw new IllegalArgumentException("Employee role is required.");
        this.businessId = businessId;
        this.userId = userId;
        this.staffProfileId = staffProfileId;
        this.role = role;
        this.status = EmployeeStatus.ACTIVE;
    }

    public boolean belongsTo(Long candidateBusinessId) {
        return businessId.equals(candidateBusinessId);
    }

    public void changeRole(EmployeeRole role) {
        if (role != null) this.role = role;
    }

    private static void requirePositive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " must be positive.");
    }
}
