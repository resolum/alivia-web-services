package com.resolum.alivia.assistance.domain.model.queries;

import java.time.LocalDate;

public record GetAgendaByCaregiverIdQuery(Long caregiverId, LocalDate from, LocalDate to) {}
