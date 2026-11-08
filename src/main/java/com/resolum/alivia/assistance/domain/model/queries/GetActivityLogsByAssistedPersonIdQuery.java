package com.resolum.alivia.assistance.domain.model.queries;

import java.time.LocalDate;

public record GetActivityLogsByAssistedPersonIdQuery(Long assistedPersonId, LocalDate from, LocalDate to) {}
