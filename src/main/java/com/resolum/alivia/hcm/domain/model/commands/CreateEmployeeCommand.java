package com.resolum.alivia.hcm.domain.model.commands;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import java.time.LocalDate;

public record CreateEmployeeCommand(Long businessId, String corporateEmail, String photoUrl,
                                    String photoPublicId, String phoneNumber, String firstName,
                                    String lastName, String dni, LocalDate birthDate,
                                    EmployeeRole role) {
}
