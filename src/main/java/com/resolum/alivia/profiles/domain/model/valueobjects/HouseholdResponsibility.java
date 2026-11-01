package com.resolum.alivia.profiles.domain.model.valueobjects;

/**
 * Responsibility of a household member.
 *
 * <p>The values are provisional: AP-355 must confirm the final responsibility matrix before
 * the authorization service depends on them.</p>
 */
public enum HouseholdResponsibility {
    PRINCIPAL,
    CAREGIVER,
    GUEST
}
