package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.domain.model.valueobjects.ProfileData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalProfilesServiceTest {

    private final ExternalProfilesService service = new ExternalProfilesService();

    @Test
    void rejectsNullArguments() {
        var profileData = new ProfileData("John Doe", "+51987654321");

        assertThrows(IllegalArgumentException.class, () -> service.createProfile(null, profileData));
        assertThrows(IllegalArgumentException.class, () -> service.createProfile(1L, null));
    }

    @Test
    void createsProfileSuccessfully() {
        var profileData = new ProfileData("John Doe", "+51987654321");
        assertDoesNotThrow(() -> service.createProfile(1L, profileData));
    }
}
