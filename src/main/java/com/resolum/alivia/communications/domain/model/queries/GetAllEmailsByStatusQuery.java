package com.resolum.alivia.communications.domain.model.queries;

import com.resolum.alivia.communications.domain.model.valueobjects.EmailStatus;

public record GetAllEmailsByStatusQuery(EmailStatus status) {
}
