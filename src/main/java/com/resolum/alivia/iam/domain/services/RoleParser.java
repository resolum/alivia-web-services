package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;

import java.util.Arrays;
import java.util.Optional;

/**
 * Domain service to parse role strings and aliases into {@link AccessRole}.
 */
public final class RoleParser {

    private RoleParser() {
    }

    /**
     * Parses a role string or alias into an {@link AccessRole}.
     *
     * @param roleStr the role string or alias
     * @return the matching {@link AccessRole}, or {@link AccessRole#CAREGIVER} if empty
     * @throws IllegalArgumentException if the role string cannot be resolved
     */
    public static AccessRole parse(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return AccessRole.CAREGIVER;
        }
        String normalized = roleStr.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return switch (normalized) {
            case "ADMIN", "ADMINISTRATOR", "ADMINISTRADOR" -> AccessRole.ADMINISTRATOR;
            case "CAREGIVER", "CUIDADOR", "FAMILIAR", "FAMILY_MEMBER", "RELATIVE" -> AccessRole.CAREGIVER;
            case "EMPLOYEE", "EMPLEADO", "TECHNICIAN", "TECNICO" -> AccessRole.TECHNICIAN;
            case "SUBSCRIPTION_MANAGER", "GESTOR_SUSCRIPCIONES" -> AccessRole.SUBSCRIPTION_MANAGER;
            case "ASSISTED_PERSON", "PERSONA_ASISTIDA", "PACIENTE" -> AccessRole.ASSISTED_PERSON;
            default -> AccessRole.valueOf(normalized);
        };
    }

    /**
     * Name of a role in the web front: ADMIN and TECH for ADMINISTRATOR and TECHNICIAN, the same name for the others.
     */
    public static String toApiName(AccessRole role) {
        return switch (role) {
            case ADMINISTRATOR -> "ADMIN";
            case TECHNICIAN -> "TECH";
            default -> role.name();
        };
    }

    /**
     * Role an administrator may grant or revoke from {@code /users}, by its front name.
     *
     * @param apiName ADMIN, TECH, SUBSCRIPTION_MANAGER, CLEANING, RELATIVE or CAREGIVER
     * @return the role, or empty when the name is not one of those
     */
    public static Optional<AccessRole> parseAssignable(String apiName) {
        return Arrays.stream(AccessRole.values())
                .filter(role -> role != AccessRole.ASSISTED_PERSON && toApiName(role).equals(apiName))
                .findFirst();
    }
}
