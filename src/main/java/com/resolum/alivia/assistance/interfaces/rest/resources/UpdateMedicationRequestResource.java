package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.util.List;

public record UpdateMedicationRequestResource(
        String dose,
        List<String> times,
        String frequency,
        String effectiveFrom,
        String indicationRef
) {}
