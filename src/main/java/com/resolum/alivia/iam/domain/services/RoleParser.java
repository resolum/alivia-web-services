package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;

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
        return switch (normalized) {
            case "ADMIN", "ADMINISTRATOR", "ADMINISTRADOR" -> AccessRole.ADMINISTRATOR;
            case "CAREGIVER", "CUIDADOR", "FAMILIAR", "FAMILY_MEMBER" -> AccessRole.CAREGIVER;
            case "EMPLOYEE", "EMPLEADO", "TECHNICIAN", "TECNICO" -> AccessRole.TECHNICIAN;
            case "SUBSCRIPTION_MANAGER", "GESTOR_SUSCRIPCIONES" -> AccessRole.SUBSCRIPTION_MANAGER;
            case "ASSISTED_PERSON", "PERSONA_ASISTIDA", "PACIENTE" -> AccessRole.ASSISTED_PERSON;
            default -> AccessRole.valueOf(normalized);
        };
    }
}
