package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.services.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordThatSatisfiesEveryRule() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Aa1!aaaa"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("Aa1!" + "a".repeat(68)));
    }

    @Test
    void rejectsNullPassword() {
        assertWeakPassword(null);
    }

    @Test
    void rejectsPasswordsThatDoNotMeetTheRules() {
        assertWeakPassword("Aa1!aaa");
        assertWeakPassword("aa1!aaaa");
        assertWeakPassword("AA1!AAAA");
        assertWeakPassword("Aaa!aaaa");
        assertWeakPassword("Aa1aaaaa");
    }

    @Test
    void rejectsPasswordLongerThanBcryptLimitInBytes() {
        var exception = assertThrows(SignUpValidationException.class,
                () -> PasswordPolicy.validate("Aa1!" + "a".repeat(69)));

        assertEquals("password", exception.violations().getFirst().field());
        assertEquals("too_long", exception.violations().getFirst().code());
    }

    @Test
    void rejectsMultibytePasswordLongerThanBcryptLimitInBytes() {
        var exception = assertThrows(SignUpValidationException.class,
                () -> PasswordPolicy.validate("Aa1!" + "ñ".repeat(35)));

        assertEquals("password", exception.violations().getFirst().field());
        assertEquals("too_long", exception.violations().getFirst().code());
    }

    private static void assertWeakPassword(String password) {
        var exception = assertThrows(SignUpValidationException.class,
                () -> PasswordPolicy.validate(password));

        assertEquals("password", exception.violations().getFirst().field());
        assertEquals("password_weak", exception.violations().getFirst().code());
    }
}
