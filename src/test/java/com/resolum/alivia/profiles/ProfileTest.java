package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.model.valueobjects.ProfilePreferences;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileTest {

    @Test
    void profileStartsWithoutUserAndPreferences() {
        var profile = personProfile();

        assertNull(profile.getUserId());
        assertNull(profile.getPreferences());
    }

    @Test
    void profileLinksToUserAndKeepsTheLinkIdempotent() {
        var profile = personProfile();

        profile.linkToUser(new UserId(10L));
        assertEquals(10L, profile.getUserId());

        profile.linkToUser(new UserId(10L));
        assertEquals(10L, profile.getUserId());
    }

    @Test
    void profileCannotBeLinkedToAnotherUser() {
        var profile = personProfile();
        profile.linkToUser(new UserId(10L));

        assertThrows(IllegalStateException.class, () -> profile.linkToUser(new UserId(11L)));
        assertThrows(IllegalArgumentException.class, () -> profile.linkToUser(null));
    }

    @Test
    void profileUpdatesPreferences() {
        var profile = personProfile();
        var preferences = new ProfilePreferences(Map.of("language", "es", "alerts", "email"));

        profile.updatePreferences(preferences);

        assertEquals(preferences, profile.getPreferences());
        assertThrows(IllegalArgumentException.class, () -> profile.updatePreferences(null));
    }

    @Test
    void preferencesAreImmutableAndRejectNulls() {
        var values = new HashMap<String, Object>();
        values.put("language", "es");
        var preferences = new ProfilePreferences(values);

        values.put("language", "en");

        assertEquals("es", preferences.values().get("language"));
        assertThrows(UnsupportedOperationException.class, () -> preferences.values().put("alerts", "sms"));
        assertThrows(IllegalArgumentException.class, () -> new ProfilePreferences(null));

        var withNullValue = new HashMap<String, Object>();
        withNullValue.put("language", null);
        assertThrows(IllegalArgumentException.class, () -> new ProfilePreferences(withNullValue));
    }

    private PersonProfile personProfile() {
        return new PersonProfile(
                new EmailAddress("person@alivia.com"),
                new Photo("https://cloud.com/photo.jpg", "photo-id"),
                new PhoneNumber("51987654321"),
                new PersonName("Carlos", "Gomez"),
                new Dni("12345678"),
                new BirthDate(LocalDate.of(1990, 1, 15)));
    }
}
