package com.resolum.alivia.profiles;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.resolum.alivia.profiles.application.internal.commandservices.OwnProfileCommandServiceImpl;
import com.resolum.alivia.profiles.domain.exceptions.OwnProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.commands.UpdateOwnProfileCommand;
import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.model.valueobjects.ProfilePreferences;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnProfileCommandServiceTest {

    private static final LocalDate BIRTH = LocalDate.of(1990, 5, 20);

    private PersonProfileRepository personRepository;
    private StaffProfileRepository staffRepository;
    private StorageService storageService;
    private OwnProfileCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        personRepository = mock(PersonProfileRepository.class);
        staffRepository = mock(StaffProfileRepository.class);
        storageService = mock(StorageService.class);
        service = new OwnProfileCommandServiceImpl(personRepository, staffRepository, storageService);
    }

    @Test
    void updatesPersonProfileFieldsAndPreferences() {
        var profile = personProfile();
        when(personRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(personRepository.save(any(PersonProfile.class)))
                .thenAnswer(invocation -> withId(invocation.<PersonProfile>getArgument(0)));

        var command = new UpdateOwnProfileCommand(1L, "Maria Elena", null, "51987654322",
                null, null, Map.of("email", true));

        assertEquals(7L, service.handle(command));
        assertEquals("Maria Elena", profile.getPersonName().firstName());
        assertEquals("Lopez", profile.getPersonName().lastName());
        assertEquals("51987654322", profile.getPhoneNumber().phoneNumber());
        assertEquals("http://old", profile.getPhoto().photoUrl());
        assertEquals(Map.of("email", true), profile.getPreferences().values());
    }

    @Test
    void updatesStaffProfileWhenTheUserOwnsOne() {
        var profile = staffProfile();
        when(personRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(staffRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(staffRepository.save(any(StaffProfile.class)))
                .thenAnswer(invocation -> withId(invocation.<StaffProfile>getArgument(0)));

        var command = new UpdateOwnProfileCommand(2L, null, "Gomez Diaz", null,
                null, null, null);

        assertEquals(7L, service.handle(command));
        assertEquals("Ana", profile.getPersonName().firstName());
        assertEquals("Gomez Diaz", profile.getPersonName().lastName());
        assertEquals("51987654321", profile.getPhoneNumber().phoneNumber());
    }

    @Test
    void rejectsUserWithoutLinkedProfile() {
        when(personRepository.findByUserId(3L)).thenReturn(Optional.empty());
        when(staffRepository.findByUserId(3L)).thenReturn(Optional.empty());

        var command = new UpdateOwnProfileCommand(3L, "Maria", null, null, null, null, null);

        assertThrows(OwnProfileNotFoundException.class, () -> service.handle(command));
    }

    @Test
    void uploadsNewPhotoWhenProvided() {
        var profile = personProfile();
        when(personRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(personRepository.save(any(PersonProfile.class)))
                .thenAnswer(invocation -> withId(invocation.<PersonProfile>getArgument(0)));
        when(storageService.upload(any(), any())).thenReturn(Map.of("url", "http://new", "publicId", "newpid"));

        var command = new UpdateOwnProfileCommand(1L, null, null, null,
                new byte[]{1, 2, 3}, "avatar.jpg", null);

        service.handle(command);

        assertEquals("http://new", profile.getPhoto().photoUrl());
        assertEquals("newpid", profile.getPhoto().photoPublicId());
        verify(storageService).upload(any(), any());
    }

    @Test
    void clearsPreferencesWithAnEmptyMap() {
        var profile = personProfile();
        profile.updatePreferences(new ProfilePreferences(Map.of("sms", true)));
        when(personRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(personRepository.save(any(PersonProfile.class)))
                .thenAnswer(invocation -> withId(invocation.<PersonProfile>getArgument(0)));

        var command = new UpdateOwnProfileCommand(1L, null, null, null, null, null, Map.of());

        service.handle(command);

        assertEquals(Map.of(), profile.getPreferences().values());
    }

    private static PersonProfile withId(PersonProfile profile) {
        ReflectionTestUtils.setField(profile, "id", 7L);
        return profile;
    }

    private static StaffProfile withId(StaffProfile profile) {
        ReflectionTestUtils.setField(profile, "id", 7L);
        return profile;
    }

    private static PersonProfile personProfile() {
        return new PersonProfile(
                new EmailAddress("maria@example.com"),
                new Photo("http://old", "old"),
                new PhoneNumber("51987654321"),
                new PersonName("Maria", "Lopez"),
                new Dni("12345678"),
                new BirthDate(BIRTH));
    }

    private static StaffProfile staffProfile() {
        return new StaffProfile(
                new EmailAddress("ana@example.com"),
                new Photo("http://old", "old"),
                new PhoneNumber("51987654321"),
                new PersonName("Ana", "Gomez"),
                new Dni("87654321"),
                new BirthDate(BIRTH));
    }
}
