package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignUpValidationExceptionTest {

    @Test
    void createsExceptionWithSingleViolation() {
        var ex = new SignUpValidationException("email", SignUpValidationException.INVALID_EMAIL);

        assertEquals("Sign-up data is invalid.", ex.getMessage());
        assertEquals(1, ex.violations().size());
        assertEquals("email", ex.violations().getFirst().field());
        assertEquals(SignUpValidationException.INVALID_EMAIL, ex.violations().getFirst().code());
    }

    @Test
    void createsExceptionWithMultipleViolations() {
        var violations = List.of(
                new SignUpValidationException.FieldViolation("email", "required"),
                new SignUpValidationException.FieldViolation("password", "password_weak")
        );
        var ex = new SignUpValidationException(violations);

        assertEquals(2, ex.violations().size());
    }

    @Test
    void rejectsEmptyOrNullViolations() {
        assertThrows(IllegalArgumentException.class, () -> new SignUpValidationException((List<SignUpValidationException.FieldViolation>) null));
        assertThrows(IllegalArgumentException.class, () -> new SignUpValidationException(List.of()));
    }

    @Test
    void fieldViolationRejectsNullOrBlankFields() {
        assertThrows(IllegalArgumentException.class, () -> new SignUpValidationException.FieldViolation(null, "code"));
        assertThrows(IllegalArgumentException.class, () -> new SignUpValidationException.FieldViolation("   ", "code"));
        assertThrows(IllegalArgumentException.class, () -> new SignUpValidationException.FieldViolation("field", null));
        assertThrows(IllegalArgumentException.class, () -> new SignUpValidationException.FieldViolation("field", "   "));
    }
}
