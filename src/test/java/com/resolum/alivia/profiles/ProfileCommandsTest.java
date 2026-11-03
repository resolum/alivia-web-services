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
    private final byte[] samplePhoto = new byte[]{1, 2, 3};

    @BeforeEach
    void setUp() {
        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var period = new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range);
        defaultHours = new BusinessHours(List.of(period));
    }

    @Test
    void createBusinessProfileCommandValidatesAllFields() {
        var cmd = new CreateBusinessProfileCommand(
                "email@alivia.com", samplePhoto, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );
        assertEquals("Alivia Clinic", cmd.businessName());
        assertArrayEquals(samplePhoto, cmd.photoData());
        assertEquals("logo.png", cmd.photoFileName());

        // Address overload
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");
        var cmdWithAddress = new CreateBusinessProfileCommand(
                "email@alivia.com", samplePhoto, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", address
        );
        assertEquals("Av. Brasil 123", cmdWithAddress.streetAddress());

        // Null Address overload
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand(
                "email@alivia.com", samplePhoto, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", (Address) null
        ));

        // Invalid fields
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand(null, samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand(" ", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", null, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", new byte[0], "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", null, "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", " ", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", null, "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", " ", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", null, "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", " ", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", null, "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", " ", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", null, "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", " ", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", null, "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", " ", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", null, defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", " ", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", null, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", new BusinessHours(Collections.emptyList()), "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, null, "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, " ", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", null, "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", " ", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", null, 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", " ", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", null, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", -90.1, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 90.1, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, null, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, -180.1, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 180.1, "p"));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, null));
        assertThrows(IllegalArgumentException.class, () -> new CreateBusinessProfileCommand("em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, " "));
    }

    @Test
    void updateBusinessProfileCommandValidatesAllFields() {
        var cmd = new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", samplePhoto, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );
        assertEquals(1L, cmd.profileId());

        // Address overload
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");
        var cmdWithAddress = new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", samplePhoto, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", address
        );
        assertEquals(1L, cmdWithAddress.profileId());

        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", samplePhoto, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", (Address) null
        ));

        // Invalid profileId
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(null, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(0L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(-1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));

        // Invalid fields
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, null, samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, " ", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", null, "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", " ", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", null, "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", " ", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", null, "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", " ", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", null, "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", " ", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", null, "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", " ", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", null, "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", " ", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", null, defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", " ", defaultHours, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", null, "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", new BusinessHours(Collections.emptyList()), "s", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, null, "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, " ", "sup", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", null, "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", " ", "st", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", null, 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", " ", 0.0, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", null, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", -90.1, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 90.1, 0.0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, null, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, -180.1, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 180.1, "p"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, null));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessProfileCommand(1L, "em", samplePhoto, "id", "w", "ruc", "b", "l", "d", "m", "e", defaultHours, "s", "sup", "st", 0.0, 0.0, " "));
    }

    @Test
    void createPersonProfileCommandValidatesFields() {
        var birthDate = LocalDate.of(1995, 3, 15);
        var cmd = new CreatePersonProfileCommand(
                "carlos@alivia.com", samplePhoto, "photo.jpg", "+51999888777",
                "Carlos", "Gomez", "12345678", birthDate
        );
        assertEquals("Carlos", cmd.firstName());
        assertArrayEquals(samplePhoto, cmd.photoData());
        assertEquals("photo.jpg", cmd.photoFileName());

        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand(null, samplePhoto, "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand(" ", samplePhoto, "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", null, "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", new byte[0], "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", null, "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", " ", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", "p", null, "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", "p", " ", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", "p", "fn", null, "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", "p", "fn", " ", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", "p", "fn", "ln", null, birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", "p", "fn", "ln", " ", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreatePersonProfileCommand("em", samplePhoto, "id", "p", "fn", "ln", "dni", null));
    }

    @Test
    void updatePersonProfileCommandValidatesFields() {
        var birthDate = LocalDate.of(1995, 3, 15);
        var cmd = new UpdatePersonProfileCommand(
                1L, "Carlos", "Gomez", birthDate, "+51999888777", samplePhoto, "photo.jpg"
        );
        assertEquals(1L, cmd.profileId());

        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(null, "fn", "ln", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(0L, "fn", "ln", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, null, "ln", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, " ", "ln", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", null, birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", " ", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", null, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", birthDate, null, samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdatePersonProfileCommand(1L, "fn", "ln", birthDate, " ", samplePhoto, "id"));
    }

    @Test
    void createStaffProfileCommandValidatesFields() {
        var birthDate = LocalDate.of(1992, 10, 5);
        var cmd = new CreateStaffProfileCommand(
                "maria@alivia.com", samplePhoto, "photo_1.jpg", "+51999888777",
                "Maria", "Rodriguez", "87654321", birthDate
        );
        assertEquals("Maria", cmd.firstName());

        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand(null, samplePhoto, "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand(" ", samplePhoto, "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", null, "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", new byte[0], "id", "p", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", null, "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", " ", "fn", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", "p", null, "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", "p", " ", "ln", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", "p", "fn", null, "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", "p", "fn", " ", "dni", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", "p", "fn", "ln", null, birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", "p", "fn", "ln", " ", birthDate));
        assertThrows(IllegalArgumentException.class, () -> new CreateStaffProfileCommand("em", samplePhoto, "id", "p", "fn", "ln", "dni", null));
    }

    @Test
    void updateStaffProfileCommandValidatesFields() {
        var birthDate = LocalDate.of(1992, 10, 5);
        var cmd = new UpdateStaffProfileCommand(
                2L, "Maria", "Rodriguez", birthDate, "+51999888777", samplePhoto, "photo_1.jpg"
        );
        assertEquals(2L, cmd.profileId());

        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(null, "fn", "ln", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(0L, "fn", "ln", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, null, "ln", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, " ", "ln", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", null, birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", " ", birthDate, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", null, "p", samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", birthDate, null, samplePhoto, "id"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateStaffProfileCommand(2L, "fn", "ln", birthDate, " ", samplePhoto, "id"));
    }

    @Test
    void createBusinessProfileCommandWithPhotoDataSucceeds() {
        byte[] bytes = new byte[]{1, 2, 3};
        var cmd = new CreateBusinessProfileCommand(
                "email@alivia.com", bytes, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );
        assertArrayEquals(bytes, cmd.photoData());
        assertEquals("logo.png", cmd.photoFileName());
        assertNotNull(cmd.toString());
        assertEquals(cmd, new CreateBusinessProfileCommand(
                "email@alivia.com", bytes, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        ));
        assertEquals(cmd.hashCode(), cmd.hashCode());

        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");
        var cmdWithAddress = new CreateBusinessProfileCommand(
                "email@alivia.com", bytes, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", address
        );
        assertEquals("Av. Brasil 123", cmdWithAddress.streetAddress());
    }

    @Test
    void updateBusinessProfileCommandWithPhotoDataSucceeds() {
        byte[] bytes = new byte[]{1, 2, 3};
        var cmd = new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", bytes, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );
        assertArrayEquals(bytes, cmd.photoData());
        assertEquals("logo.png", cmd.photoFileName());
        assertNotNull(cmd.toString());
        assertEquals(cmd, new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", bytes, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        ));

        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");
        var cmdWithAddress = new UpdateBusinessProfileCommand(
                1L, "email@alivia.com", bytes, "logo.png", "+51999888777",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com", address
        );
        assertEquals(1L, cmdWithAddress.profileId());
    }

    @Test
    void createPersonProfileCommandWithPhotoDataSucceeds() {
        byte[] bytes = new byte[]{1, 2, 3};
        var birthDate = LocalDate.of(1995, 3, 15);
        var cmd = new CreatePersonProfileCommand(
                "carlos@alivia.com", bytes, "carlos.jpg", "+51999888777",
                "Carlos", "Gomez", "12345678", birthDate
        );
        assertArrayEquals(bytes, cmd.photoData());
        assertEquals("carlos.jpg", cmd.photoFileName());
        assertNotNull(cmd.toString());
        assertEquals(cmd, new CreatePersonProfileCommand(
                "carlos@alivia.com", bytes, "carlos.jpg", "+51999888777",
                "Carlos", "Gomez", "12345678", birthDate
        ));
    }

    @Test
    void updatePersonProfileCommandWithPhotoDataSucceeds() {
        byte[] bytes = new byte[]{1, 2, 3};
        var birthDate = LocalDate.of(1995, 3, 15);
        var cmd = new UpdatePersonProfileCommand(
                1L, "Carlos", "Gomez", birthDate, "+51999888777", bytes, "carlos.jpg"
        );
        assertArrayEquals(bytes, cmd.photoData());
        assertEquals("carlos.jpg", cmd.photoFileName());
        assertNotNull(cmd.toString());
        assertEquals(cmd, new UpdatePersonProfileCommand(
                1L, "Carlos", "Gomez", birthDate, "+51999888777", bytes, "carlos.jpg"
        ));
    }

    @Test
    void createStaffProfileCommandWithPhotoDataSucceeds() {
        byte[] bytes = new byte[]{1, 2, 3};
        var birthDate = LocalDate.of(1992, 10, 5);
        var cmd = new CreateStaffProfileCommand(
                "maria@alivia.com", bytes, "maria.jpg", "+51999888777",
                "Maria", "Rodriguez", "87654321", birthDate
        );
        assertArrayEquals(bytes, cmd.photoData());
        assertEquals("maria.jpg", cmd.photoFileName());
        assertNotNull(cmd.toString());
        assertEquals(cmd, new CreateStaffProfileCommand(
                "maria@alivia.com", bytes, "maria.jpg", "+51999888777",
                "Maria", "Rodriguez", "87654321", birthDate
        ));
    }

    @Test
    void updateStaffProfileCommandWithPhotoDataSucceeds() {
        byte[] bytes = new byte[]{1, 2, 3};
        var birthDate = LocalDate.of(1992, 10, 5);
        var cmd = new UpdateStaffProfileCommand(
                2L, "Maria", "Rodriguez", birthDate, "+51999888777", bytes, "maria.jpg"
        );
        assertArrayEquals(bytes, cmd.photoData());
        assertEquals("maria.jpg", cmd.photoFileName());
        assertNotNull(cmd.toString());
        assertEquals(cmd, new UpdateStaffProfileCommand(
                2L, "Maria", "Rodriguez", birthDate, "+51999888777", bytes, "maria.jpg"
        ));
    }

    @Test
    void profileCommandValidatorPrivateConstructor() throws Exception {
        var constructor = ProfileCommandValidator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
