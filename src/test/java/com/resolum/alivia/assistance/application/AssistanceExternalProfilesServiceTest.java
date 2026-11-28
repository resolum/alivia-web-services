package com.resolum.alivia.assistance.application;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistanceExternalProfilesServiceTest {

    private static final String DEFAULT_PHOTO =
            "https://res.cloudinary.com/demo/image/upload/v1793056387/default-profile_q0py6r.webp";
    private static final LocalDate BIRTH = LocalDate.of(1940, 5, 1);

    private final ProfilesContextFacade facade = mock(ProfilesContextFacade.class);
    private final ExternalProfilesService service = new ExternalProfilesService(facade, DEFAULT_PHOTO);

    @Test
    void createsWithTheDefaultPhotoWithoutUploadingWhenThereIsNoPhoto() {
        when(facade.createPersonProfile("ana@mail.com", DEFAULT_PHOTO, "default-profile_q0py6r", "51911111111",
                "Ana", "Diaz", "12345678", BIRTH)).thenReturn(7L);

        Long id = service.createPersonProfile("ana@mail.com", null, null, "51911111111", "Ana", "Diaz",
                "12345678", BIRTH);

        assertThat(id).isEqualTo(7L);
        verify(facade, never()).createPersonProfileWithPhoto(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadsThePhotoWhenOneIsGiven() {
        byte[] photo = {9, 9};
        when(facade.createPersonProfileWithPhoto("ana@mail.com", photo, "ana.png", "51911111111",
                "Ana", "Diaz", "12345678", BIRTH)).thenReturn(8L);

        assertThat(service.createPersonProfile("ana@mail.com", photo, "ana.png", "51911111111", "Ana", "Diaz",
                "12345678", BIRTH)).isEqualTo(8L);
    }

    @Test
    void fallsBackToAFixedPublicIdWithoutADefaultPhotoUrl() {
        var withoutDefault = new ExternalProfilesService(facade, "");
        withoutDefault.createPersonProfile("ana@mail.com", new byte[0], null, "51911111111", "Ana", "Diaz",
                "12345678", BIRTH);

        verify(facade).createPersonProfile("ana@mail.com", "", "default-photo", "51911111111",
                "Ana", "Diaz", "12345678", BIRTH);
    }
}
