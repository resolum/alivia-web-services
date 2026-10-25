package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.interfaces.rest.resources.SignUpResource;

/**
 * Assembles {@link SignUpCommand} instances from REST resources.
 */
public final class SignUpCommandFromResourceAssembler {

    private SignUpCommandFromResourceAssembler() {
    }

    /**
     * Creates a sign-up command from a request resource.
     *
     * @param resource the sign-up request resource
     * @return the sign-up command
     */
    public static SignUpCommand toCommandFromResource(SignUpResource resource) {
        if (resource == null) {
            return null;
        }
        AccessRole role = parseRole(resource.role());
        return new SignUpCommand(
                resource.email(),
                resource.password(),
                resource.fullName(),
                resource.phoneNumber(),
                Boolean.TRUE.equals(resource.acceptedTerms()),
                role,
                resource.businessId(),
                resource.familiarId()
        );
    }

    private static AccessRole parseRole(String roleStr) {
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
            default -> {
                try {
                    yield AccessRole.valueOf(normalized);
                } catch (IllegalArgumentException e) {
                    throw new SignUpValidationException("role", "invalid_role");
                }
            }
        };
    }
}
