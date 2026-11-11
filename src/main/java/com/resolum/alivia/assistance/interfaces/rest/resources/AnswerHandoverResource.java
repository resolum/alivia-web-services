package com.resolum.alivia.assistance.interfaces.rest.resources;

import java.util.List;

public record AnswerHandoverResource(
        boolean accept,
        String actorId,
        String actorName,
        List<String> attended,
        List<String> pending
) {}
