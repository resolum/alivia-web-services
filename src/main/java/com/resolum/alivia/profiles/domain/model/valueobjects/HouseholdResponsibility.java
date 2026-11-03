package com.resolum.alivia.profiles.domain.model.valueobjects;

/**
 * Responsibility of a household member.
 *
 * <p>The authorization matrix lives in
 * {@link com.resolum.alivia.profiles.domain.services.HouseholdAccessService}: {@code PRINCIPAL}
 * reads and manages, while {@code CAREGIVER} and {@code GUEST} only read.</p>
 */
public enum HouseholdResponsibility {
    PRINCIPAL,
    CAREGIVER,
    GUEST
}
