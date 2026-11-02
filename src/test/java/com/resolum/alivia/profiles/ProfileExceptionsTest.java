package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.exceptions.*;
import com.resolum.alivia.profiles.domain.model.valueobjects.*;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileExceptionsTest {

    @Test
    void businessNameAlreadyExistsExceptionHandlesBothConstructors() {
        var ex1 = new BusinessNameAlreadyExistsException(new BusinessName("Clinica Uno"));
        var ex2 = new BusinessNameAlreadyExistsException("Clinica Dos");

        assertTrue(ex1.getMessage().contains("Clinica Uno"));
        assertTrue(ex2.getMessage().contains("Clinica Dos"));
    }

    @Test
    void businessProfileNotFoundExceptionHandlesBothConstructors() {
        var ex1 = new BusinessProfileNotFoundException(101L);
        var ex2 = new BusinessProfileNotFoundException("Not found message");

        assertTrue(ex1.getMessage().contains("101"));
        assertTrue(ex2.getMessage().contains("Not found message"));
    }

    @Test
    void dniAlreadyExistsExceptionHandlesBothConstructors() {
        var ex1 = new DniAlreadyExistsException(new Dni("12345678"));
        var ex2 = new DniAlreadyExistsException("87654321");

        assertTrue(ex1.getMessage().contains("12345678"));
        assertTrue(ex2.getMessage().contains("87654321"));
    }

    @Test
    void emailAddressAlreadyExistsExceptionHandlesBothConstructors() {
        var ex1 = new EmailAddressAlreadyExistsException(new EmailAddress("test@alivia.com"));
        var ex2 = new EmailAddressAlreadyExistsException("otro@alivia.com");

        assertTrue(ex1.getMessage().contains("test@alivia.com"));
        assertTrue(ex2.getMessage().contains("otro@alivia.com"));
    }

    @Test
    void invalidBusinessHoursExceptionHandlesBothConstructors() {
        var ex1 = new InvalidBusinessHoursException();
        var ex2 = new InvalidBusinessHoursException("Horario invalido");

        assertTrue(ex1.getMessage().contains("Invalid business hours provided"));
        assertTrue(ex2.getMessage().contains("Horario invalido"));
    }

    @Test
    void legalNameAlreadyExistsExceptionHandlesBothConstructors() {
        var ex1 = new LegalNameAlreadyExistsException(new LegalName("Alivia SAC"));
        var ex2 = new LegalNameAlreadyExistsException("Resolum SAC");

        assertTrue(ex1.getMessage().contains("Alivia SAC"));
        assertTrue(ex2.getMessage().contains("Resolum SAC"));
    }

    @Test
    void personProfileNotFoundExceptionHandlesBothConstructors() {
        var ex1 = new PersonProfileNotFoundException(202L);
        var ex2 = new PersonProfileNotFoundException("Person not found");

        assertTrue(ex1.getMessage().contains("202"));
        assertTrue(ex2.getMessage().contains("Person not found"));
    }

    @Test
    void rucAlreadyExistsExceptionHandlesBothConstructors() {
        var ex1 = new RucAlreadyExistsException(new Ruc("20123456789"));
        var ex2 = new RucAlreadyExistsException("20987654321");

        assertTrue(ex1.getMessage().contains("20123456789"));
        assertTrue(ex2.getMessage().contains("20987654321"));
    }

    @Test
    void staffProfileNotFoundExceptionHandlesBothConstructors() {
        var ex1 = new StaffProfileNotFoundException(303L);
        var ex2 = new StaffProfileNotFoundException("Staff not found");

        assertTrue(ex1.getMessage().contains("303"));
        assertTrue(ex2.getMessage().contains("Staff not found"));
    }
}
