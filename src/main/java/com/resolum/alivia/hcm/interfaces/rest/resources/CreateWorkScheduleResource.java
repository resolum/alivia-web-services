package com.resolum.alivia.hcm.interfaces.rest.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateWorkScheduleResource(
        Long businessId,
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull @Size(max = 42) List<@Valid @NotNull WorkShiftResource> shifts,
        boolean draft
) {
}
