package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.valueobjects.*;
import com.resolum.alivia.shared.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProfileAggregatesTest {

    @Test
    void baseProfileValidatesNullFieldsAndUpdatesContact() {
        var email = new EmailAddress("test@alivia.com");
        var photo = new Photo("https://cloud.com/photo.jpg", "photo-id");
        var phone = new PhoneNumber("51987654321");
        var name = new PersonName("Carlos", "Gomez");
        var dni = new Dni("12345678");
        var birthDate = new BirthDate(LocalDate.of(1990, 1, 15));

        assertThrows(IllegalArgumentException.class, () -> new PersonProfile(null, photo, phone, name, dni, birthDate));
        assertThrows(IllegalArgumentException.class, () -> new PersonProfile(email, null, phone, name, dni, birthDate));
        assertThrows(IllegalArgumentException.class, () -> new PersonProfile(email, photo, null, name, dni, birthDate));

        var profile = new PersonProfile(email, photo, phone, name, dni, birthDate);
        assertEquals(email, profile.getEmailAddress());
        assertEquals(photo, profile.getPhoto());
        assertEquals(phone, profile.getPhoneNumber());

        var newPhone = new PhoneNumber("51987654322");
        var newPhoto = new Photo("https://cloud.com/new.jpg", "new-id");
        profile.updateContactInformation(newPhone, newPhoto);

        assertEquals(newPhone, profile.getPhoneNumber());
        assertEquals(newPhoto, profile.getPhoto());

        // Update with null does not overwrite existing values
        profile.updateContactInformation(null, null);
        assertEquals(newPhone, profile.getPhoneNumber());
        assertEquals(newPhoto, profile.getPhoto());
    }

    @Test
    void personProfileCreatesAndUpdatesCorrectly() {
        var email = new EmailAddress("person@alivia.com");
        var photo = new Photo("https://cloud.com/photo.jpg", "photo-id");
        var phone = new PhoneNumber("51987654321");
        var name = new PersonName("Carlos", "Gomez");
        var dni = new Dni("12345678");
        var birthDate = new BirthDate(LocalDate.of(1990, 1, 15));

        var person = new PersonProfile(email, photo, phone, name, dni, birthDate);

        assertEquals(email, person.getEmailAddress());
        assertEquals(photo, person.getPhoto());
        assertEquals(phone, person.getPhoneNumber());
        assertEquals(name, person.getPersonName());
        assertEquals(dni, person.getDni());
        assertEquals(birthDate, person.getBirthDate());

        var updatedName = new PersonName("Carlos Alberto", "Gomez");
        var updatedBirthDate = new BirthDate(LocalDate.of(1990, 1, 20));
        var updatedPhone = new PhoneNumber("51987654323");
        var updatedPhoto = new Photo("https://cloud.com/updated.jpg", "updated-id");

        person.updatePersonalInformation(updatedName, updatedBirthDate, updatedPhone, updatedPhoto);

        assertEquals(updatedName, person.getPersonName());
        assertEquals(updatedBirthDate, person.getBirthDate());
        assertEquals(updatedPhone, person.getPhoneNumber());
        assertEquals(updatedPhoto, person.getPhoto());
    }

    @Test
    void staffProfileCreatesAndUpdatesCorrectly() {
        var email = new EmailAddress("staff@alivia.com");
        var photo = new Photo("https://cloud.com/photo.jpg", "photo-id");
        var phone = new PhoneNumber("51987654321");
        var name = new PersonName("Maria", "Rodriguez");
        var dni = new Dni("87654321");
        var birthDate = new BirthDate(LocalDate.of(1992, 7, 10));

        var staff = new StaffProfile(email, photo, phone, name, dni, birthDate);

        assertEquals(email, staff.getEmailAddress());
        assertEquals(photo, staff.getPhoto());
        assertEquals(phone, staff.getPhoneNumber());
        assertEquals(name, staff.getPersonName());
        assertEquals(dni, staff.getDni());
        assertEquals(birthDate, staff.getBirthDate());

        var updatedName = new PersonName("Maria Elena", "Rodriguez");
        var updatedBirthDate = new BirthDate(LocalDate.of(1992, 7, 15));
        var updatedPhone = new PhoneNumber("51987654324");
        var updatedPhoto = new Photo("https://cloud.com/staff_updated.jpg", "staff-updated-id");

        staff.updatePersonalInformation(updatedName, updatedBirthDate, updatedPhone, updatedPhoto);

        assertEquals(updatedName, staff.getPersonName());
        assertEquals(updatedBirthDate, staff.getBirthDate());
        assertEquals(updatedPhone, staff.getPhoneNumber());
        assertEquals(updatedPhoto, staff.getPhoto());
    }

    @Test
    void businessProfileCreatesUpdatesAndChecksOpenHours() {
        var email = new EmailAddress("clinic@alivia.com");
        var photo = new Photo("https://cloud.com/clinic.jpg", "clinic-id");
        var whatsapp = new PhoneNumber("51987654321");
        var ruc = new Ruc("20123456789");
        var bName = new BusinessName("Clinica Alivia");
        var lName = new LegalName("Clinica Alivia S.A.C.");
        var emergencyPhone = new PhoneNumber("51987654325");

        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(20, 0));
        var period = new WeeklyPeriod(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), range);
        var hours = new BusinessHours(List.of(period));
        var supportEmail = new EmailAddress("soporte@alivia.com");
        var address = new Address("Av. Javier Prado 1234", -12.08, -77.03, "place_123");

        var business = new BusinessProfile(
                email, photo, whatsapp, ruc, bName, lName,
                "Descripcion clinica", "Servicios medicos",
                emergencyPhone, hours, "Cuidando de ti", supportEmail, address
        );

        assertEquals(email, business.getEmailAddress());
        assertEquals(photo, business.getPhoto());
        assertEquals(whatsapp, business.getPhoneNumber());
        assertEquals(ruc, business.getRuc());
        assertEquals(bName, business.getBusinessName());
        assertEquals(lName, business.getLegalName());
        assertEquals("Descripcion clinica", business.getDescription());
        assertEquals("Servicios medicos", business.getMainActivity());
        assertEquals(emergencyPhone, business.getBusinessPhone());
        assertEquals(hours, business.getBusinessHours());
        assertEquals("Cuidando de ti", business.getSlogan());
        assertEquals(supportEmail, business.getSupportEmail());
        assertEquals(address, business.getAddress());

        assertTrue(business.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(10, 0)));
        assertFalse(business.isOpenAt(DayOfWeek.SUNDAY, LocalTime.of(10, 0)));

        var updatedBName = new BusinessName("Clinica Alivia Sur");
        var updatedLName = new LegalName("Clinica Alivia Sur S.A.C.");
        var updatedPhone = new PhoneNumber("51987654326");
        var updatedAddress = new Address("Av. Benavides 456", -12.12, -77.01, "place_456");

        business.updateInformation(
                updatedBName, updatedLName, "Nueva descripcion", "Cirugias",
                updatedPhone, hours, "Tu salud primero", supportEmail, updatedAddress
        );

        assertEquals(updatedBName, business.getBusinessName());
        assertEquals(updatedLName, business.getLegalName());
        assertEquals("Nueva descripcion", business.getDescription());
        assertEquals("Cirugias", business.getMainActivity());
        assertEquals(updatedPhone, business.getBusinessPhone());
        assertEquals("Tu salud primero", business.getSlogan());
        assertEquals(updatedAddress, business.getAddress());
    }
}
