package com.resolum.alivia.shared;


import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import jakarta.validation.constraints.Email;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EmailAddressTest {

    @Test
    void storesTheEmail() {
        assertEquals("renato@example.com", new EmailAddress("renato@example.com").email());
    }

    @Test
    void constructorDoesNotValidateByItself() {
        // La validación es declarativa (@Email) y la ejecuta Bean Validation, no el constructor.
        assertDoesNotThrow(() -> new EmailAddress("no-es-un-correo"));
        assertDoesNotThrow(() -> new EmailAddress(null));
    }

    @Test
    void emailComponentIsAnnotatedWithEmail() throws NoSuchFieldException {
        Email annotation = EmailAddress.class.getDeclaredField("email").getAnnotation(Email.class);

        assertNotNull(annotation);
    }

    @Test
    void equalityIsBasedOnEmail() {
        assertEquals(new EmailAddress("a@b.com"), new EmailAddress("a@b.com"));
        assertNotEquals(new EmailAddress("a@b.com"), new EmailAddress("c@d.com"));
    }
}