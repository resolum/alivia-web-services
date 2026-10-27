package com.resolum.alivia.hcm.interfaces.rest.resources;

import java.time.LocalDate;

public record EmployeeEligibilityResource(Long employeeId, LocalDate date, boolean eligible) {
}
