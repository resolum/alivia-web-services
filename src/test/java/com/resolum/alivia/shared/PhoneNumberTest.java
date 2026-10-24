package com.resolum.alivia.shared;

import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberTest {

    @Test
    void rejectsNull() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new PhoneNumber(null));

        assertEquals("Phone number cannot be null.", ex.getMessage());
    }

    @ParameterizedTest(name = "formato inválido: \"{0}\"")
    @ValueSource(strings = {
            "",                  // vacío
            "0123456789",        // empieza con 0
            "+51987654321",      // trae el signo +
            "51 987 654 321",    // trae espacios
            "5198765432a",       // trae letras
            "1234567890123456"   // 16 dígitos
    })
    void rejectsWrongFormat(String value) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new PhoneNumber(value));

        assertEquals("Phone number must contain 1 to 15 digits.", ex.getMessage());
    }

    @ParameterizedTest(name = "número inexistente: {0}")
    @ValueSource(strings = {"12", "519876", "999999999999999"})
    void rejectsNumbersThatAreNotValidInternationally(String value) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new PhoneNumber(value));

        assertEquals("Invalid international phone number.", ex.getMessage());
    }

    @ParameterizedTest(name = "{0} -> código +{1}")
    @CsvSource({
            "51987654321, 51",
            "16502530000, 1"
    })
    void acceptsValidNumbersAndResolvesCountryCode(String value, String expectedCode) {
        var phone = new PhoneNumber(value);

        assertEquals(value, phone.phoneNumber());
        assertEquals(expectedCode, phone.countryCallingCode());
    }

    @Test
    void equalityIsBasedOnNumber() {
        assertEquals(new PhoneNumber("51987654321"), new PhoneNumber("51987654321"));
        assertNotEquals(new PhoneNumber("51987654321"), new PhoneNumber("16502530000"));
    }
}