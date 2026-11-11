package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.util.List;

public record RequestHandoverResource(
        String fromShiftId,
        String toShiftId,
        List<String> attended,
        List<String> pending
) {}
