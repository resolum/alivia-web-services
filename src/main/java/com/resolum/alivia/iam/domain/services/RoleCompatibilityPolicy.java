package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;

import java.util.Set;

import static com.resolum.alivia.iam.domain.model.valueobjects.AccessRole.*;

/**
 * Domain service with the roles a user may hold together. Same rules as user-role.entity.ts in the front: change
 * both together.
 */
public final class RoleCompatibilityPolicy {
    private static final Set<AccessRole> STAFF = Set.of(ADMINISTRATOR, TECHNICIAN, SUBSCRIPTION_MANAGER, CLEANING);
    private static final Set<AccessRole> HOUSEHOLD = Set.of(RELATIVE, CAREGIVER, ASSISTED_PERSON);

    private RoleCompatibilityPolicy() {
    }

    /**
     * Whether a user may hold both roles: TECHNICIAN never goes with SUBSCRIPTION_MANAGER, and a staff role never
     * goes with a household one.
     */
    public static boolean areCompatible(AccessRole first, AccessRole second) {
        if (first == second) return true;
        if (Set.of(first, second).equals(Set.of(TECHNICIAN, SUBSCRIPTION_MANAGER))) return false;
        return !(STAFF.contains(first) && HOUSEHOLD.contains(second)
                || HOUSEHOLD.contains(first) && STAFF.contains(second));
    }
}
