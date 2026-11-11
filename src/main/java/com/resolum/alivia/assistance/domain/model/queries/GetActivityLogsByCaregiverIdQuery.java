package com.resolum.alivia.assistance.domain.model.queries;

import java.time.LocalDate;

public record GetActivityLogsByCaregiverIdQuery(Long caregiverId, LocalDate from, LocalDate to) {}
