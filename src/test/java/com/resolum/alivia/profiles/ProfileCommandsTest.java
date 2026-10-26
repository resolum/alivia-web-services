package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.model.commands.*;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProfileCommandsTest {

    private BusinessHours defaultHours;

    @BeforeEach
    void setUp() {
        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var period = new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range);
        defaultHours = new BusinessHours(List.of(period));
    }

    @Test
    void createBusinessProfileCommandValidatesAllFields() {
        var cmd = new CreateBusinessProfileCommand(
                "email@alivia.com", "http://photo.jpg", "public_id", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );
        assertEquals("Alivia Clinic", cmd.businessName());

        // Address overload
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");
        var cmdWithAddress = new CreateBusinessProfileCommand(
                "email@alivia.com", "http://photo.jpg", "public_id", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", address
        );
        assertEquals("Av. Brasil 123", cmdWithAddress.streetAddress());

        // Null Address overload
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand(
                "email@alivia.com", "http://photo.jpg", "public_id", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", (Address) null
        ));

        // Invalid fields
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand(null, "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand(" ", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", null, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", " ", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", null, "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", " ", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", null, "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", " ", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", null, "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", " ", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", null, "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", " ", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", null, "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", " ", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", null, "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", " ", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", null, "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", " ", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", null, defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", " ", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", null, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", new BusinessHours(Collections.emptyList()), "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, null, "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, " ", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", null, "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", " ", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", null, 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", " ", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", null, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", -90.1, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 90.1, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, null, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, -180.1, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 180.1, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, null));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, " "));
    }

    @Test
    void updateBusinessProfileCommandValidatesAllFields() {
        var cmd = new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", "http://photo.jpg", "public_id", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );
        assertEquals(1L, cmd.profileId());

        // Address overload
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");
        var cmdWithAddress = new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", "http://photo.jpg", "public_id", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", address
        );
        assertEquals(1L, cmdWithAddress.profileId());

        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", "http://photo.jpg", "public_id", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", (Address) null
        ));

        // Invalid profileId
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(null, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(0L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(-1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));

        // Invalid fields
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, null, "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, " ", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", null, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", " ", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", null, "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", " ", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", null, "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", " ", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", null, "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", " ", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", null, "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", " ", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", null, "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", " ", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", null, "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", " ", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", null, "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", " ", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", null, defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", " ", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", null, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", new BusinessHours(Collections.emptyList()), "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, null, "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, " ", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", null, "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", " ", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", null, 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", " ", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", null, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", -90.1, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 90.1, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, null, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, -180.1, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 180.1, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, null));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", "url", "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, " "));
    }

    @Test
    void createPersonProfileCommandValidatesFields() {
        var birthDate = LocalDate.of(1995, 3, 15);
        var cmd = new CreatePersonProfileCommand(
                "carlos@alivia.com", "http://photo.jpg", "photo_1", "+51999888777",
                "Carlos", "Gomez", "12345678", birthDate
        );
        assertEquals("Carlos", cmd.firstName());

        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand(null, "url", "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand(" ", "url", "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", null, "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", " ", "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", null, "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", " ", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", null, "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", " ", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", "p", null, "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", "p", " ", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", "p", "fn", null, "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", "p", "fn", " ", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", "p", "fn", "ln", null, birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", "p", "fn", "ln", " ", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", "url", "id", "p", "fn", "ln", "dni", null));
    }

    @Test
    void updatePersonProfileCommandValidatesFields() {
        var birthDate = LocalDate.of(1995, 3, 15);
        var cmd = new UpdatePersonProfileCommand(
                1L, "Carlos", "Gomez", birthDate, "+51999888777", "http://photo.jpg", "photo_1"
        );
        assertEquals(1L, cmd.profileId());

        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(null, "fn", "ln", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(0L, "fn", "ln", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, null, "ln", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, " ", "ln", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", null, birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", " ", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", null, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", birthDate, null, "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", birthDate, " ", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", birthDate, "p", null, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", birthDate, "p", " ", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", birthDate, "p", "u", null));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", birthDate, "p", "u", " "));
    }

    @Test
    void createStaffProfileCommandValidatesFields() {
        var birthDate = LocalDate.of(1992, 10, 5);
        var cmd = new CreateStaffProfileCommand(
                "maria@alivia.com", "http://photo.jpg", "photo_1", "+51999888777",
                "Maria", "Rodriguez", "87654321", birthDate
        );
        assertEquals("Maria", cmd.firstName());

        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand(null, "url", "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand(" ", "url", "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", null, "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", " ", "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", null, "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", " ", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", null, "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", " ", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", "p", null, "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", "p", " ", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", "p", "fn", null, "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", "p", "fn", " ", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", "p", "fn", "ln", null, birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", "p", "fn", "ln", " ", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", "url", "id", "p", "fn", "ln", "dni", null));
    }

    @Test
    void updateStaffProfileCommandValidatesFields() {
        var birthDate = LocalDate.of(1992, 10, 5);
        var cmd = new UpdateStaffProfileCommand(
                2L, "Maria", "Rodriguez", birthDate, "+51999888777", "http://photo.jpg", "photo_1"
        );
        assertEquals(2L, cmd.profileId());

        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(null, "fn", "ln", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(0L, "fn", "ln", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, null, "ln", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, " ", "ln", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", null, birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", " ", birthDate, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", null, "p", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", birthDate, null, "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", birthDate, " ", "u", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", birthDate, "p", null, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", birthDate, "p", " ", "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", birthDate, "p", "u", null));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", birthDate, "p", "u", " "));
    }
}
