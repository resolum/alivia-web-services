package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.application.internal.commandservices.BusinessProfileCommandServiceImpl;
import com.resolum.alivia.profiles.application.internal.commandservices.PersonProfileCommandServiceImpl;
import com.resolum.alivia.profiles.application.internal.commandservices.StaffProfileCommandServiceImpl;
import com.resolum.alivia.profiles.domain.exceptions.*;
import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.commands.*;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.BusinessProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileCommandServicesTest {

    private BusinessProfileRepository businessRepo;
    private PersonProfileRepository personRepo;
    private StaffProfileRepository staffRepo;

    private BusinessProfileCommandServiceImpl businessService;
    private PersonProfileCommandServiceImpl personService;
    private StaffProfileCommandServiceImpl staffService;

    private BusinessHours defaultHours;
    private final byte[] samplePhoto = new byte[]{1, 2, 3};

    @BeforeEach
    void setUp() {
        businessRepo = mock(BusinessProfileRepository.class);
        personRepo = mock(PersonProfileRepository.class);
        staffRepo = mock(StaffProfileRepository.class);

        businessService = new BusinessProfileCommandServiceImpl(businessRepo);
        personService = new PersonProfileCommandServiceImpl(personRepo);
        staffService = new StaffProfileCommandServiceImpl(staffRepo);

        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        defaultHours = new BusinessHours(List.of(new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range)));
    }

    // --- BusinessProfileCommandService ---

    @Test
    void createBusinessProfileSucceeds() {
        var cmd = new CreateBusinessProfileCommand(
                "corp@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "51987654322", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );

        when(businessRepo.existsByEmailAddress(any())).thenReturn(false);
        when(businessRepo.existsByRuc(any())).thenReturn(false);
        when(businessRepo.existsByBusinessName(any())).thenReturn(false);
        when(businessRepo.existsByLegalName(any())).thenReturn(false);

        var mockEntity = mock(BusinessProfile.class);
        when(mockEntity.getId()).thenReturn(10L);
        when(businessRepo.save(any(BusinessProfile.class))).thenReturn(mockEntity);

        var result = businessService.handle(cmd);
        assertEquals(10L, result);
        verify(businessRepo).save(any(BusinessProfile.class));
    }

    @Test
    void createBusinessProfileThrowsWhenEmailExists() {
        var cmd = new CreateBusinessProfileCommand(
                "corp@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "51987654322", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );

        when(businessRepo.existsByEmailAddress(any())).thenReturn(true);
        assertThrows(EmailAddressAlreadyExistsException.class, () -> businessService.handle(cmd));
        verify(businessRepo, never()).save(any());
    }

    @Test
    void createBusinessProfileThrowsWhenRucExists() {
        var cmd = new CreateBusinessProfileCommand(
                "corp@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "51987654322", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );

        when(businessRepo.existsByEmailAddress(any())).thenReturn(false);
        when(businessRepo.existsByRuc(any())).thenReturn(true);
        assertThrows(RucAlreadyExistsException.class, () -> businessService.handle(cmd));
        verify(businessRepo, never()).save(any());
    }

    @Test
    void createBusinessProfileThrowsWhenBusinessNameExists() {
        var cmd = new CreateBusinessProfileCommand(
                "corp@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "51987654322", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );

        when(businessRepo.existsByEmailAddress(any())).thenReturn(false);
        when(businessRepo.existsByRuc(any())).thenReturn(false);
        when(businessRepo.existsByBusinessName(any())).thenReturn(true);
        assertThrows(BusinessNameAlreadyExistsException.class, () -> businessService.handle(cmd));
        verify(businessRepo, never()).save(any());
    }

    @Test
    void createBusinessProfileThrowsWhenLegalNameExists() {
        var cmd = new CreateBusinessProfileCommand(
                "corp@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "51987654322", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );

        when(businessRepo.existsByEmailAddress(any())).thenReturn(false);
        when(businessRepo.existsByRuc(any())).thenReturn(false);
        when(businessRepo.existsByBusinessName(any())).thenReturn(false);
        when(businessRepo.existsByLegalName(any())).thenReturn(true);
        assertThrows(LegalNameAlreadyExistsException.class, () -> businessService.handle(cmd));
        verify(businessRepo, never()).save(any());
    }

    @Test
    void updateBusinessProfileSucceeds() {
        var cmd = new UpdateBusinessProfileCommand(
                1L, "corp@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "20123456789", "Alivia Clinic Updated", "Alivia SAC", "Desc", "Salud",
                "51987654322", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );

        var existingProfile = mock(BusinessProfile.class);
        when(existingProfile.getId()).thenReturn(1L);
        when(businessRepo.findById(1L)).thenReturn(Optional.of(existingProfile));
        when(businessRepo.save(any(BusinessProfile.class))).thenReturn(existingProfile);

        var result = businessService.handle(cmd);
        assertEquals(1L, result);
        verify(existingProfile).updateInformation(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(existingProfile).updateContactInformation(any(), any());
        verify(businessRepo).save(existingProfile);
    }

    @Test
    void updateBusinessProfileThrowsWhenNotFound() {
        var cmd = new UpdateBusinessProfileCommand(
                99L, "corp@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "20123456789", "Alivia Clinic Updated", "Alivia SAC", "Desc", "Salud",
                "+51999111222", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );

        when(businessRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessProfileNotFoundException.class, () -> businessService.handle(cmd));
    }

    // --- PersonProfileCommandService ---

    @Test
    void createPersonProfileSucceeds() {
        var cmd = new CreatePersonProfileCommand(
                "carlos@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "Carlos", "Gomez", "12345678", LocalDate.of(1990, 5, 10)
        );

        when(personRepo.existsByEmailAddress(any())).thenReturn(false);
        when(personRepo.existsByDni(any())).thenReturn(false);

        var mockEntity = mock(PersonProfile.class);
        when(mockEntity.getId()).thenReturn(20L);
        when(personRepo.save(any(PersonProfile.class))).thenReturn(mockEntity);

        var result = personService.handle(cmd);
        assertEquals(20L, result);
        verify(personRepo).save(any(PersonProfile.class));
    }

    @Test
    void createPersonProfileThrowsWhenEmailExists() {
        var cmd = new CreatePersonProfileCommand(
                "carlos@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "Carlos", "Gomez", "12345678", LocalDate.of(1990, 5, 10)
        );

        when(personRepo.existsByEmailAddress(any())).thenReturn(true);
        assertThrows(EmailAddressAlreadyExistsException.class, () -> personService.handle(cmd));
    }

    @Test
    void createPersonProfileThrowsWhenDniExists() {
        var cmd = new CreatePersonProfileCommand(
                "carlos@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "Carlos", "Gomez", "12345678", LocalDate.of(1990, 5, 10)
        );

        when(personRepo.existsByEmailAddress(any())).thenReturn(false);
        when(personRepo.existsByDni(any())).thenReturn(true);
        assertThrows(DniAlreadyExistsException.class, () -> personService.handle(cmd));
    }

    @Test
    void updatePersonProfileSucceeds() {
        var cmd = new UpdatePersonProfileCommand(
                2L, "Carlos Alberto", "Gomez", LocalDate.of(1990, 5, 10),
                "51987654321", samplePhoto, "photo.jpg"
        );

        var existingProfile = mock(PersonProfile.class);
        when(existingProfile.getId()).thenReturn(2L);
        when(personRepo.findById(2L)).thenReturn(Optional.of(existingProfile));
        when(personRepo.save(any(PersonProfile.class))).thenReturn(existingProfile);

        var result = personService.handle(cmd);
        assertEquals(2L, result);
        verify(existingProfile).updatePersonalInformation(any(), any(), any(), any());
        verify(personRepo).save(existingProfile);
    }

    @Test
    void updatePersonProfileThrowsWhenNotFound() {
        var cmd = new UpdatePersonProfileCommand(
                88L, "Carlos", "Gomez", LocalDate.of(1990, 5, 10),
                "51987654321", samplePhoto, "photo.jpg"
        );

        when(personRepo.findById(88L)).thenReturn(Optional.empty());
        assertThrows(PersonProfileNotFoundException.class, () -> personService.handle(cmd));
    }

    // --- StaffProfileCommandService ---

    @Test
    void createStaffProfileSucceeds() {
        var cmd = new CreateStaffProfileCommand(
                "maria@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "Maria", "Rodriguez", "87654321", LocalDate.of(1992, 10, 5)
        );

        when(staffRepo.existsByEmailAddress(any())).thenReturn(false);
        when(staffRepo.existsByDni(any())).thenReturn(false);

        var mockEntity = mock(StaffProfile.class);
        when(mockEntity.getId()).thenReturn(30L);
        when(staffRepo.save(any(StaffProfile.class))).thenReturn(mockEntity);

        var result = staffService.handle(cmd);
        assertEquals(30L, result);
        verify(staffRepo).save(any(StaffProfile.class));
    }

    @Test
    void createStaffProfileThrowsWhenEmailExists() {
        var cmd = new CreateStaffProfileCommand(
                "maria@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "Maria", "Rodriguez", "87654321", LocalDate.of(1992, 10, 5)
        );

        when(staffRepo.existsByEmailAddress(any())).thenReturn(true);
        assertThrows(EmailAddressAlreadyExistsException.class, () -> staffService.handle(cmd));
    }

    @Test
    void createStaffProfileThrowsWhenDniExists() {
        var cmd = new CreateStaffProfileCommand(
                "maria@alivia.com", samplePhoto, "photo.jpg", "51987654321",
                "Maria", "Rodriguez", "87654321", LocalDate.of(1992, 10, 5)
        );

        when(staffRepo.existsByEmailAddress(any())).thenReturn(false);
        when(staffRepo.existsByDni(any())).thenReturn(true);
        assertThrows(DniAlreadyExistsException.class, () -> staffService.handle(cmd));
    }

    @Test
    void updateStaffProfileSucceeds() {
        var cmd = new UpdateStaffProfileCommand(
                3L, "Maria Elena", "Rodriguez", LocalDate.of(1992, 10, 5),
                "51987654321", samplePhoto, "photo.jpg"
        );

        var existingProfile = mock(StaffProfile.class);
        when(existingProfile.getId()).thenReturn(3L);
        when(staffRepo.findById(3L)).thenReturn(Optional.of(existingProfile));
        when(staffRepo.save(any(StaffProfile.class))).thenReturn(existingProfile);

        var result = staffService.handle(cmd);
        assertEquals(3L, result);
        verify(existingProfile).updatePersonalInformation(any(), any(), any(), any());
        verify(staffRepo).save(existingProfile);
    }

    @Test
    void updateStaffProfileThrowsWhenNotFound() {
        var cmd = new UpdateStaffProfileCommand(
                77L, "Maria", "Rodriguez", LocalDate.of(1992, 10, 5),
                "51987654321", samplePhoto, "photo.jpg"
        );

        when(staffRepo.findById(77L)).thenReturn(Optional.empty());
        assertThrows(StaffProfileNotFoundException.class, () -> staffService.handle(cmd));
    }

    @Test
    void createPersonProfileWithPhotoDataUploadsViaStorageService() {
        var storage = mock(com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService.class);
        when(storage.upload(any(), any())).thenReturn(java.util.Map.of("url", "https://cloud.com/p.jpg", "public_id", "pub_123"));
        var serviceWithStorage = new PersonProfileCommandServiceImpl(personRepo, storage);

        when(personRepo.existsByEmailAddress(any())).thenReturn(false);
        when(personRepo.existsByDni(any())).thenReturn(false);
        var mockEntity = mock(PersonProfile.class);
        when(mockEntity.getId()).thenReturn(55L);
        when(personRepo.save(any(PersonProfile.class))).thenReturn(mockEntity);

        var cmd = new CreatePersonProfileCommand(
                "carlos@alivia.com", new byte[]{1, 2}, "avatar.png", "51987654321",
                "Carlos", "Gomez", "12345678", LocalDate.of(1990, 5, 10)
        );

        var result = serviceWithStorage.handle(cmd);
        assertEquals(55L, result);
        verify(storage).upload(any(), eq("avatar.png"));
    }

    @Test
    void createStaffProfileWithPhotoDataUploadsViaStorageService() {
        var storage = mock(com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService.class);
        when(storage.upload(any(), any())).thenReturn(java.util.Map.of("url", "https://cloud.com/s.jpg", "public_id", "pub_staff"));
        var serviceWithStorage = new StaffProfileCommandServiceImpl(staffRepo, storage);

        when(staffRepo.existsByEmailAddress(any())).thenReturn(false);
        when(staffRepo.existsByDni(any())).thenReturn(false);
        var mockEntity = mock(StaffProfile.class);
        when(mockEntity.getId()).thenReturn(66L);
        when(staffRepo.save(any(StaffProfile.class))).thenReturn(mockEntity);

        var cmd = new CreateStaffProfileCommand(
                "maria@alivia.com", new byte[]{3, 4}, "staff.png", "51987654321",
                "Maria", "Rodriguez", "87654321", LocalDate.of(1992, 10, 5)
        );

        var result = serviceWithStorage.handle(cmd);
        assertEquals(66L, result);
        verify(storage).upload(any(), eq("staff.png"));
    }

    @Test
    void createBusinessProfileWithPhotoDataUploadsViaStorageService() {
        var storage = mock(com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService.class);
        when(storage.upload(any(), any())).thenReturn(java.util.Map.of("url", "https://cloud.com/b.jpg", "public_id", "pub_biz"));
        var serviceWithStorage = new BusinessProfileCommandServiceImpl(businessRepo, storage);

        when(businessRepo.existsByEmailAddress(any())).thenReturn(false);
        when(businessRepo.existsByRuc(any())).thenReturn(false);
        when(businessRepo.existsByBusinessName(any())).thenReturn(false);
        when(businessRepo.existsByLegalName(any())).thenReturn(false);
        var mockEntity = mock(BusinessProfile.class);
        when(mockEntity.getId()).thenReturn(77L);
        when(businessRepo.save(any(BusinessProfile.class))).thenReturn(mockEntity);

        var cmd = new CreateBusinessProfileCommand(
                "corp@alivia.com", new byte[]{5, 6}, "logo.png", "51987654321",
                "20123456789", "Alivia Clinic", "Alivia SAC", "Desc", "Salud",
                "51987654322", defaultHours, "Slogan", "support@alivia.com",
                "Av. Brasil 123", -12.05, -77.04, "place_1"
        );

        var result = serviceWithStorage.handle(cmd);
        assertEquals(77L, result);
        verify(storage).upload(any(), eq("logo.png"));
    }
}
