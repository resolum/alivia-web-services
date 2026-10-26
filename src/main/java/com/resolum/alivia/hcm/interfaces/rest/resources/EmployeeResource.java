package com.resolum.alivia.hcm.interfaces.rest.resources;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResource(Long id, Long businessId, Long userId, Long staffProfileId,
                               String corporateEmail, String photoUrl, String photoPublicId,
                               String phoneNumber, String firstName, String lastName, String dni,
                               LocalDate birthDate, EmployeeRole role, EmployeeStatus status,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
}
