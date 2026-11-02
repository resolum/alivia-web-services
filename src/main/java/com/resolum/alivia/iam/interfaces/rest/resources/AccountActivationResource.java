package com.resolum.alivia.iam.interfaces.rest.resources;

import java.time.LocalDateTime;

/** Who an invitation link belongs to, shown before choosing the password. */
public record AccountActivationResource(String email, String fullName, LocalDateTime expiresAt) {
}
