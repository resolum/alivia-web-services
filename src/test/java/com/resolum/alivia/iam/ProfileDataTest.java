package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.valueobjects.ProfileData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileDataTest {

    @Test
    void twoArgConstructorSetsNullIds() {
        var profile = new ProfileData("Carlos", "Perez", "+51987654321");
        assertEquals("Carlos", profile.firstName());
        assertEquals("Perez", profile.lastName());
        assertEquals("+51987654321", profile.phoneNumber());
        assertNull(profile.businessId());
        assertNull(profile.familiarId());
    }

    @Test
    void fourArgConstructorSetsAllFields() {
        var profile = new ProfileData("Carlos", "Perez", "+51987654321", 5L, 10L);
        assertEquals(5L, profile.businessId());
        assertEquals(10L, profile.familiarId());
    }

    @Test
    void rejectsNullOrBlankFields() {
        assertThrows(IllegalArgumentException.class, () -> new ProfileData(null, "Perez", "+51987654321"));
        assertThrows(IllegalArgumentException.class, () -> new ProfileData("Carlos", "   ", "+51987654321"));
        assertThrows(IllegalArgumentException.class, () -> new ProfileData("Carlos", "Perez", null));
        assertThrows(IllegalArgumentException.class, () -> new ProfileData("Carlos", "Perez", "   "));
    }
}
