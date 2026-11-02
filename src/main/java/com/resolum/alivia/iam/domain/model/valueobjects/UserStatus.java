package com.resolum.alivia.iam.domain.model.valueobjects;

/**
 * Lifecycle status of an IAM user.
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    BLOCKED,
    INACTIVE
}
