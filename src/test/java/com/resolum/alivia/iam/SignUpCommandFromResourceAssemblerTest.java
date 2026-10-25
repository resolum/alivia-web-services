package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.interfaces.rest.resources.SignUpResource;
import com.resolum.alivia.iam.interfaces.rest.transform.SignUpCommandFromResourceAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignUpCommandFromResourceAssemblerTest {

    @Test
    void returnsNullForNullResource() {
        assertNull(SignUpCommandFromResourceAssembler.toCommandFromResource(null));
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN, ADMINISTRATOR",
            "ADMINISTRATOR, ADMINISTRATOR",
            "ADMINISTRADOR, ADMINISTRATOR",
            "CAREGIVER, CAREGIVER",
            "CUIDADOR, CAREGIVER",
            "FAMILIAR, CAREGIVER",
            "FAMILY_MEMBER, CAREGIVER",
            "EMPLOYEE, TECHNICIAN",
            "EMPLEADO, TECHNICIAN",
            "TECHNICIAN, TECHNICIAN",
            "TECNICO, TECHNICIAN",
            "SUBSCRIPTION_MANAGER, SUBSCRIPTION_MANAGER",
            "GESTOR_SUSCRIPCIONES, SUBSCRIPTION_MANAGER",
            "ASSISTED_PERSON, ASSISTED_PERSON",
            "PERSONA_ASISTIDA, ASSISTED_PERSON",
            "PACIENTE, ASSISTED_PERSON"
    })
    void mapsRoleAliasesCorrectly(String inputRole, AccessRole expectedRole) {
        var resource = new SignUpResource("John Doe", "user@example.com", "+51987654321",
                "Password123!", true, inputRole, 10L, 20L);

        var command = SignUpCommandFromResourceAssembler.toCommandFromResource(resource);

        assertNotNull(command);
        assertEquals(expectedRole, command.role());
        assertEquals("user@example.com", command.email());
        assertEquals("Password123!", command.password());
        assertEquals("John Doe", command.fullName());
        assertEquals("+51987654321", command.phoneNumber());
        assertTrue(command.termsAccepted());
        assertEquals(10L, command.businessId());
        assertEquals(20L, command.familiarId());
    }

    @Test
    void defaultsToCaregiverWhenRoleIsBlank() {
        var resource = new SignUpResource("John Doe", "user@example.com", "+51987654321",
                "Password123!", true, "   ", null, null);

        var command = SignUpCommandFromResourceAssembler.toCommandFromResource(resource);

        assertNotNull(command);
        assertEquals(AccessRole.CAREGIVER, command.role());
    }

    @Test
    void throwsValidationExceptionWhenRoleIsInvalid() {
        var resource = new SignUpResource("John Doe", "user@example.com", "+51987654321",
                "Password123!", true, "UNKNOWN_ROLE", null, null);

        assertThrows(SignUpValidationException.class, () ->
                SignUpCommandFromResourceAssembler.toCommandFromResource(resource));
    }
}
