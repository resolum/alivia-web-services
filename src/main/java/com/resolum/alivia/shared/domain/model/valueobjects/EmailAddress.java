package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;

@Embeddable
public record EmailAddress(@Email(regexp = "invalid email address") String email) {
}
