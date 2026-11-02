package com.resolum.alivia.hcm.interfaces.rest.resources;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

public record UpdateEmployeeResource(String photoUrl, String photoPublicId, String phoneNumber,
                                     String firstName, String lastName, @Past LocalDate birthDate,
                                     EmployeeRole role) {
}
