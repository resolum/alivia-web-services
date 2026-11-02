package com.resolum.alivia.hcm.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RenewContractResource(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {
}
