package com.resolum.alivia.hcm.interfaces.rest.resources;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateEmployeeResource(
        @NotNull @Positive Long businessId,
        @NotBlank @Email String corporateEmail,
        String photoUrl,
        String photoPublicId,
        @NotBlank String phoneNumber,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Pattern(regexp = "\\d{8}") String dni,
        @NotNull @Past LocalDate birthDate,
        @NotNull EmployeeRole role
) {
}
