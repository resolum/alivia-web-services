package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.model.queries.*;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileQueriesTest {

    @Test
    void getBusinessProfileByIdQueryValidates() {
        var query = new GetBusinessProfileByIdQuery(1L);
        assertEquals(1L, query.profileId());

        assertThrows(IllegalArgumentException.class, () -> new GetBusinessProfileByIdQuery(null));
        assertThrows(IllegalArgumentException.class, () -> new GetBusinessProfileByIdQuery(0L));
        assertThrows(IllegalArgumentException.class, () -> new GetBusinessProfileByIdQuery(-5L));
    }

    @Test
    void getPersonProfileByIdQueryValidates() {
        var query = new GetPersonProfileByIdQuery(2L);
        assertEquals(2L, query.profileId());

        assertThrows(IllegalArgumentException.class, () -> new GetPersonProfileByIdQuery(null));
        assertThrows(IllegalArgumentException.class, () -> new GetPersonProfileByIdQuery(0L));
        assertThrows(IllegalArgumentException.class, () -> new GetPersonProfileByIdQuery(-1L));
    }

    @Test
    void getStaffProfileByIdQueryValidates() {
        var query = new GetStaffProfileByIdQuery(3L);
        assertEquals(3L, query.profileId());

        assertThrows(IllegalArgumentException.class, () -> new GetStaffProfileByIdQuery(null));
        assertThrows(IllegalArgumentException.class, () -> new GetStaffProfileByIdQuery(0L));
        assertThrows(IllegalArgumentException.class, () -> new GetStaffProfileByIdQuery(-10L));
    }

    @Test
    void existsBusinessProfileByEmailAddressQueryValidates() {
        var query = new ExistsBusinessProfileByEmailAddressQuery("corp@alivia.com");
        assertEquals("corp@alivia.com", query.emailAddress());

        var queryFromVo = new ExistsBusinessProfileByEmailAddressQuery(new EmailAddress("corp@alivia.com"));
        assertEquals("corp@alivia.com", queryFromVo.emailAddress());

        assertThrows(IllegalArgumentException.class, () -> new ExistsBusinessProfileByEmailAddressQuery((String) null));
        assertThrows(IllegalArgumentException.class, () -> new ExistsBusinessProfileByEmailAddressQuery("   "));
        assertThrows(IllegalArgumentException.class, () -> new ExistsBusinessProfileByEmailAddressQuery((EmailAddress) null));
    }

    @Test
    void existsPersonProfileByEmailAddressQueryValidates() {
        var query = new ExistsPersonProfileByEmailAddressQuery("person@alivia.com");
        assertEquals("person@alivia.com", query.emailAddress());

        var queryFromVo = new ExistsPersonProfileByEmailAddressQuery(new EmailAddress("person@alivia.com"));
        assertEquals("person@alivia.com", queryFromVo.emailAddress());

        assertThrows(IllegalArgumentException.class, () -> new ExistsPersonProfileByEmailAddressQuery((String) null));
        assertThrows(IllegalArgumentException.class, () -> new ExistsPersonProfileByEmailAddressQuery("   "));
        assertThrows(IllegalArgumentException.class, () -> new ExistsPersonProfileByEmailAddressQuery((EmailAddress) null));
    }

    @Test
    void existsPersonProfileByDniQueryValidates() {
        var query = new ExistsPersonProfileByDniQuery("12345678");
        assertEquals("12345678", query.dni());

        var queryFromVo = new ExistsPersonProfileByDniQuery(new Dni("12345678"));
        assertEquals("12345678", queryFromVo.dni());

        assertThrows(IllegalArgumentException.class, () -> new ExistsPersonProfileByDniQuery((String) null));
        assertThrows(IllegalArgumentException.class, () -> new ExistsPersonProfileByDniQuery("   "));
        assertThrows(IllegalArgumentException.class, () -> new ExistsPersonProfileByDniQuery((Dni) null));
    }

    @Test
    void existsStaffProfileByEmailAddressQueryValidates() {
        var query = new ExistsStaffProfileByEmailAddressQuery("staff@alivia.com");
        assertEquals("staff@alivia.com", query.emailAddress());

        var queryFromVo = new ExistsStaffProfileByEmailAddressQuery(new EmailAddress("staff@alivia.com"));
        assertEquals("staff@alivia.com", queryFromVo.emailAddress());

        assertThrows(IllegalArgumentException.class, () -> new ExistsStaffProfileByEmailAddressQuery((String) null));
        assertThrows(IllegalArgumentException.class, () -> new ExistsStaffProfileByEmailAddressQuery("   "));
        assertThrows(IllegalArgumentException.class, () -> new ExistsStaffProfileByEmailAddressQuery((EmailAddress) null));
    }

    @Test
    void existsStaffProfileByDniQueryValidates() {
        var query = new ExistsStaffProfileByDniQuery("87654321");
        assertEquals("87654321", query.dni());

        var queryFromVo = new ExistsStaffProfileByDniQuery(new Dni("87654321"));
        assertEquals("87654321", queryFromVo.dni());

        assertThrows(IllegalArgumentException.class, () -> new ExistsStaffProfileByDniQuery((String) null));
        assertThrows(IllegalArgumentException.class, () -> new ExistsStaffProfileByDniQuery("   "));
        assertThrows(IllegalArgumentException.class, () -> new ExistsStaffProfileByDniQuery((Dni) null));
    }
}
