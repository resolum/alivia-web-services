package com.resolum.alivia.profiles;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.resolum.alivia.profiles.domain.exceptions.OwnProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.commands.UpdateOwnProfileCommand;
import com.resolum.alivia.profiles.domain.model.queries.GetOwnProfileQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.model.valueobjects.ProfilePreferences;
import com.resolum.alivia.profiles.domain.services.OwnProfileCommandService;
import com.resolum.alivia.profiles.domain.services.OwnProfileQueryService;
import com.resolum.alivia.profiles.interfaces.rest.controllers.ProfilesController;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfilesControllerTest {

    private static final LocalDate BIRTH = LocalDate.of(1990, 5, 20);

    private OwnProfileQueryService queryService;
    private OwnProfileCommandService commandService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        queryService = mock(OwnProfileQueryService.class);
        commandService = mock(OwnProfileCommandService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProfilesController(queryService, commandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getOwnProfileReturnsPersonProfile() throws Exception {
        var profile = personProfile();
        when(queryService.handle(new GetOwnProfileQuery(1L))).thenReturn(Optional.of(profile));

        mvc.perform(get("/api/v1/profiles/me").principal(() -> "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.type").value("PERSON"))
                .andExpect(jsonPath("$.emailAddress").value("maria@example.com"))
                .andExpect(jsonPath("$.firstName").value("Maria"))
                .andExpect(jsonPath("$.lastName").value("Lopez"))
                .andExpect(jsonPath("$.dni").value("12345678"))
                .andExpect(jsonPath("$.preferences.email").value(true));
    }

    @Test
    void getOwnProfileReturnsStaffProfile() throws Exception {
        var profile = staffProfile();
        when(queryService.handle(new GetOwnProfileQuery(2L))).thenReturn(Optional.of(profile));

        mvc.perform(get("/api/v1/profiles/me").principal(() -> "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("STAFF"))
                .andExpect(jsonPath("$.firstName").value("Ana"));
    }

    @Test
    void getOwnProfileReturns404WhenTheUserHasNoProfile() throws Exception {
        when(queryService.handle(new GetOwnProfileQuery(3L))).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/profiles/me").principal(() -> "3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("profile_not_found"));
    }

    @Test
    void patchOwnProfileUpdatesAllowedFields() throws Exception {
        var profile = personProfile();
        when(commandService.handle(any(UpdateOwnProfileCommand.class))).thenReturn(7L);
        when(queryService.handle(new GetOwnProfileQuery(1L))).thenReturn(Optional.of(profile));

        var photo = new MockMultipartFile("photo", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/profiles/me")
                        .file(photo)
                        .param("firstName", "Maria Elena")
                        .param("phoneNumber", "51987654322")
                        .param("preferences", "{\"email\":true}")
                        .principal(() -> "1")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        var captor = ArgumentCaptor.forClass(UpdateOwnProfileCommand.class);
        verify(commandService).handle(captor.capture());
        var command = captor.getValue();
        assertEquals(1L, command.userId());
        assertEquals("Maria Elena", command.firstName());
        assertEquals("51987654322", command.phoneNumber());
        assertEquals(Map.of("email", true), command.preferences());
        assertNotNull(command.photoData());
    }

    @Test
    void patchOwnProfileIgnoresAUserIdSentByTheClient() throws Exception {
        var profile = personProfile();
        when(commandService.handle(any(UpdateOwnProfileCommand.class))).thenReturn(7L);
        when(queryService.handle(new GetOwnProfileQuery(1L))).thenReturn(Optional.of(profile));

        mvc.perform(multipart("/api/v1/profiles/me")
                        .param("userId", "99")
                        .param("firstName", "Maria Elena")
                        .principal(() -> "1")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(UpdateOwnProfileCommand.class);
        verify(commandService).handle(captor.capture());
        assertEquals(1L, captor.getValue().userId());
    }

    @Test
    void patchOwnProfileRejectsEmailWith400() throws Exception {
        mvc.perform(multipart("/api/v1/profiles/me")
                        .param("email", "new@example.com")
                        .principal(() -> "1")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("email_not_editable"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));

        verifyNoInteractions(commandService);
    }

    @Test
    void patchOwnProfileRejectsDniWith400() throws Exception {
        mvc.perform(multipart("/api/v1/profiles/me")
                        .param("dni", "87654321")
                        .principal(() -> "1")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("dni_not_editable"))
                .andExpect(jsonPath("$.errors[0].field").value("dni"));

        verifyNoInteractions(commandService);
    }

    @Test
    void patchOwnProfileRejectsInvalidPreferencesWith400() throws Exception {
        mvc.perform(multipart("/api/v1/profiles/me")
                        .param("preferences", "[1,2]")
                        .principal(() -> "1")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_argument"));

        verifyNoInteractions(commandService);
    }

    @Test
    void patchOwnProfileReturns404WhenTheUserHasNoProfile() throws Exception {
        when(commandService.handle(any(UpdateOwnProfileCommand.class)))
                .thenThrow(new OwnProfileNotFoundException(3L));

        mvc.perform(multipart("/api/v1/profiles/me")
                        .param("firstName", "Maria")
                        .principal(() -> "3")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("profile_not_found"));

        verify(queryService, never()).handle(any());
    }

    private static PersonProfile personProfile() {
        var profile = new PersonProfile(
                new EmailAddress("maria@example.com"),
                new Photo("http://img", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Maria", "Lopez"),
                new Dni("12345678"),
                new BirthDate(BIRTH));
        profile.updatePreferences(new ProfilePreferences(Map.of("email", true)));
        ReflectionTestUtils.setField(profile, "id", 7L);
        return profile;
    }

    private static StaffProfile staffProfile() {
        var profile = new StaffProfile(
                new EmailAddress("ana@example.com"),
                new Photo("http://img", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Ana", "Gomez"),
                new Dni("87654321"),
                new BirthDate(BIRTH));
        ReflectionTestUtils.setField(profile, "id", 8L);
        return profile;
    }
}
