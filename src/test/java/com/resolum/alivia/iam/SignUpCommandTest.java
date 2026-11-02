package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignUpCommandTest {

    @Test
    void fiveArgConstructorSetsDefaultRoleAndNullIds() {
        var command = new SignUpCommand("a@b.com", "pass", "Name", "Last", "+51999999999", true);

        assertEquals("a@b.com", command.email());
        assertEquals("pass", command.password());
        assertEquals("Name", command.firstName());
        assertEquals("Last", command.lastName());
        assertEquals("+51999999999", command.phoneNumber());
        assertTrue(command.termsAccepted());
        assertEquals(AccessRole.CAREGIVER, command.role());
        assertNull(command.businessId());
        assertNull(command.familiarId());
    }

    @Test
    void sixArgConstructorSetsSpecifiedRoleAndNullIds() {
        var command = new SignUpCommand("a@b.com", "pass", "Name", "Last", "+51999999999", true, AccessRole.TECHNICIAN);

        assertEquals(AccessRole.TECHNICIAN, command.role());
        assertNull(command.businessId());
        assertNull(command.familiarId());
    }
}
