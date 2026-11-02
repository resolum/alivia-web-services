package com.resolum.alivia.iam.domain.model.valueobjects;

import java.time.LocalDateTime;

/** What the activation page shows before the invited person chooses a password. */
public record AccountActivation(String email, String fullName, LocalDateTime expiresAt) {
}
