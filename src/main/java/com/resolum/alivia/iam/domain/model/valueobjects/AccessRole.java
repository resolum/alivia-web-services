package com.resolum.alivia.iam.domain.model.valueobjects;

/**
 * Access roles available to an IAM user. The declaration order decides the primary role of a user with several roles.
 */
public enum AccessRole {
    ADMINISTRATOR,
    CAREGIVER,
    TECHNICIAN,
    SUBSCRIPTION_MANAGER,
    ASSISTED_PERSON,
    CLEANING,
    RELATIVE
}
