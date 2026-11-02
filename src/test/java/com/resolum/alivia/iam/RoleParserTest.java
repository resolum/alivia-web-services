package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.services.RoleParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleParserTest {

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
    void parsesValidRolesAndAliases(String alias, AccessRole expected) {
        assertEquals(expected, RoleParser.parse(alias));
    }

    @Test
    void defaultsToCaregiverWhenRoleIsBlankOrNull() {
        assertEquals(AccessRole.CAREGIVER, RoleParser.parse(null));
        assertEquals(AccessRole.CAREGIVER, RoleParser.parse("   "));
    }

    @Test
    void throwsIllegalArgumentExceptionOnUnknownRole() {
        assertThrows(IllegalArgumentException.class, () -> RoleParser.parse("UNKNOWN_ROLE"));
    }
}
