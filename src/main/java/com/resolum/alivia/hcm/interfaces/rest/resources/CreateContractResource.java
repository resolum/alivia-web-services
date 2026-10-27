package com.resolum.alivia.hcm.interfaces.rest.resources;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateContractResource(
        @Pattern(regexp = "CTR-ALV-\\d{4}-\\d{4,8}") String code,
        @NotNull Long businessId,
        @NotBlank @Size(max = 100) String position,
        @NotNull ContractModality modality,
        @Min(1) @Max(48) Integer weeklyHours,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        ContractStatus initialStatus,
        @Size(max = 300) String notes
) {
}
