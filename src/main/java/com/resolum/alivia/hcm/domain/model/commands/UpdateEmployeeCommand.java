package com.resolum.alivia.hcm.domain.model.commands;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import java.time.LocalDate;

public record UpdateEmployeeCommand(Long employeeId, Long businessId, String photoUrl,
                                    String photoPublicId, String phoneNumber, String firstName,
                                    String lastName, LocalDate birthDate, EmployeeRole role) {
}
