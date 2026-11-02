package com.resolum.alivia.hcm.domain.model.aggregates;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import com.resolum.alivia.hcm.domain.exceptions.ContractNotActive;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Table(name = "contracts", indexes = {
        @Index(name = "idx_contract_employee_status", columnList = "employee_id,status"),
        @Index(name = "idx_contract_business", columnList = "business_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_contract_code", columnNames = "code"))
@Getter
public class Contract extends AuditableAbstractAggregateRoot<Contract> {
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(length = 32, unique = true)
    private String code;
    @Column(nullable = false, length = 100)
    private String position;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContractModality modality;
    @Column(name = "weekly_hours")
    private Integer weeklyHours;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContractStatus status;
    @Column(length = 300)
    private String notes;

    protected Contract() {
    }

    public Contract(Long employeeId, Long businessId, String position, ContractModality modality,
                    Integer weeklyHours, ContractPeriod period, ContractStatus initialStatus,
                    String notes) {
        requirePositive(employeeId, "Employee identifier");
        requirePositive(businessId, "Business identifier");
        validateTerms(position, modality, weeklyHours, notes);
        if (period == null || initialStatus == null || initialStatus == ContractStatus.TERMINATED) {
            throw new IllegalArgumentException("Period and a valid initial status are required.");
        }
        this.employeeId = employeeId;
        this.businessId = businessId;
        this.position = position.trim();
        this.modality = modality;
        this.weeklyHours = weeklyHours;
        this.startDate = period.startDate();
        this.endDate = period.endDate();
        this.status = initialStatus;
        this.notes = normalize(notes);
    }

    public void assignCode(String code) {
        if (this.code != null || code == null || code.isBlank()) {
            throw new IllegalStateException("Contract code can only be assigned once.");
        }
        this.code = code;
    }

    public void update(String position, ContractModality modality, Integer weeklyHours,
                       ContractPeriod period, String notes) {
        ensureNotTerminated();
        validateTerms(position, modality, weeklyHours, notes);
        if (period == null) throw new IllegalArgumentException("Contract period is required.");
        this.position = position.trim();
        this.modality = modality;
        this.weeklyHours = weeklyHours;
        this.startDate = period.startDate();
        this.endDate = period.endDate();
        this.notes = normalize(notes);
    }

    /** Puts a pending contract into force, as long as its period has not ended. */
    public void activate(LocalDate today) {
        ensureNotTerminated();
        if (status != ContractStatus.PENDING) {
            throw new IllegalArgumentException("Only pending contracts can be activated.");
        }
        if (endDate.isBefore(today)) {
            throw new IllegalArgumentException("An expired contract cannot be activated; renew it instead.");
        }
        this.status = ContractStatus.ACTIVE;
    }

    public void renew(ContractPeriod period) {
        ensureNotTerminated();
        if (period == null || !period.endDate().isAfter(endDate)) {
            throw new IllegalArgumentException("A renewal must extend the contract end date.");
        }
        this.startDate = period.startDate();
        this.endDate = period.endDate();
        this.status = ContractStatus.ACTIVE;
    }

    public void terminate() {
        ensureNotTerminated();
        this.status = ContractStatus.TERMINATED;
    }

    public ContractPeriod getPeriod() {
        return new ContractPeriod(startDate, endDate);
    }

    public ContractViewStatus viewStatus(LocalDate today) {
        if (status == ContractStatus.TERMINATED) return ContractViewStatus.TERMINATED;
        if (endDate.isBefore(today)) return ContractViewStatus.EXPIRED;
        if (status == ContractStatus.PENDING || startDate.isAfter(today)) return ContractViewStatus.PENDING;
        return ContractViewStatus.ACTIVE;
    }

    public boolean isEligibleOn(LocalDate date) {
        return status == ContractStatus.ACTIVE && getPeriod().contains(date);
    }

    private void ensureNotTerminated() {
        if (status == ContractStatus.TERMINATED) {
            throw new ContractNotActive();
        }
    }

    private static void validateTerms(String position, ContractModality modality,
                                      Integer weeklyHours, String notes) {
        if (position == null || position.isBlank() || modality == null) {
            throw new IllegalArgumentException("Position and contract modality are required.");
        }
        if (modality == ContractModality.HOURLY
                && (weeklyHours == null || weeklyHours < 1 || weeklyHours > 48)) {
            throw new IllegalArgumentException("Hourly contracts require between 1 and 48 weekly hours.");
        }
        if (weeklyHours != null && (weeklyHours < 1 || weeklyHours > 48)) {
            throw new IllegalArgumentException("Weekly hours must be between 1 and 48.");
        }
        if (notes != null && notes.length() > 300) {
            throw new IllegalArgumentException("Contract notes cannot exceed 300 characters.");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) throw new IllegalArgumentException(name + " must be positive.");
    }
}
