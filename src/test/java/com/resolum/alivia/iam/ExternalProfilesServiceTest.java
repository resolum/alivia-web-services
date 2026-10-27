package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.domain.exceptions.DniAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.ProfileData;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExternalProfilesServiceTest {

    private static final String EMAIL = "maria.lopez@example.com";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String DEFAULT_URL = "https://res.cloudinary.com/demo/image/upload/v1/defaults/avatar.png";
    private static final String UPLOADED_URL = "https://res.cloudinary.com/demo/image/upload/v2/profiles/maria_1.jpg";
    private static final byte[] PHOTO = {1, 2, 3};

    private final ProfilesContextFacade profilesContextFacade = mock(ProfilesContextFacade.class);
    private final StorageService storageService = mock(StorageService.class);
    private final ExternalProfilesService service = new ExternalProfilesService(
            profilesContextFacade, storageService, DEFAULT_URL);

    @BeforeEach
    void stubUpload() {
        when(storageService.upload(PHOTO, "maria.jpg")).thenReturn(Map.of("url", UPLOADED_URL, "publicId", "profiles/maria_1"));
    }

    private static ProfileData profileData(String firstName, String lastName, String dni) {
        return new ProfileData(firstName, lastName, "+51987654321", null, null, dni, BIRTH_DATE, PHOTO, "maria.jpg");
    }

    @Test
    void rejectsNullArguments() {
        var data = profileData("Maria", "Lopez", "12345678");

        assertThrows(IllegalArgumentException.class, () -> service.createProfile(null, EMAIL, AccessRole.CAREGIVER, data));
        assertThrows(IllegalArgumentException.class, () -> service.createProfile(1L, null, AccessRole.CAREGIVER, data));
        assertThrows(IllegalArgumentException.class, () -> service.createProfile(1L, EMAIL, null, data));
        assertThrows(IllegalArgumentException.class, () -> service.createProfile(1L, EMAIL, AccessRole.CAREGIVER, null));
    }

    @Test
    void createsPersonProfileWithUploadedPhotoForCaregiverAndAssistedPerson() {
        service.createProfile(1L, EMAIL, AccessRole.CAREGIVER, profileData("Maria Elena", "Lopez Garcia", "12345678"));
        service.createProfile(2L, "ana@example.com", AccessRole.ASSISTED_PERSON, profileData("Ana", "Torres", "87654321"));

        verify(profilesContextFacade).createPersonProfile(EMAIL, UPLOADED_URL, "profiles/maria_1",
                "51987654321", "Maria Elena", "Lopez Garcia", "12345678", BIRTH_DATE);
        verify(profilesContextFacade).createPersonProfile("ana@example.com", UPLOADED_URL, "profiles/maria_1",
                "51987654321", "Ana", "Torres", "87654321", BIRTH_DATE);
    }

    @Test
    void createsStaffProfileForTechnician() {
        when(profilesContextFacade.createStaffProfile(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDate.class))).thenReturn(30L);

        assertEquals(30L, service.createProfile(1L, EMAIL, AccessRole.TECHNICIAN, profileData("Maria", "Lopez", "12345678")));

        verify(profilesContextFacade).createStaffProfile(EMAIL, UPLOADED_URL, "profiles/maria_1",
                "51987654321", "Maria", "Lopez", "12345678", BIRTH_DATE);
    }

    @Test
    void usesDefaultPhotoWhenSignUpHasNone() {
        var data = new ProfileData("Maria", "Lopez", "+51987654321", null, null, "12345678", BIRTH_DATE, null, null);

        service.createProfile(1L, EMAIL, AccessRole.CAREGIVER, data);

        verify(profilesContextFacade).createPersonProfile(EMAIL, DEFAULT_URL, "defaults/avatar",
                "51987654321", "Maria", "Lopez", "12345678", BIRTH_DATE);
        verifyNoInteractions(storageService);
    }

    @Test
    void skipsProfileForAdministrativeRoles() {
        var data = new ProfileData("Admin", "User", "+51987654321");

        assertNull(service.createProfile(1L, EMAIL, AccessRole.ADMINISTRATOR, data));
        assertNull(service.createProfile(1L, EMAIL, AccessRole.SUBSCRIPTION_MANAGER, data));

        verifyNoInteractions(profilesContextFacade, storageService);
    }

    @Test
    void rejectsMissingProfileFieldsWithoutUploading() {
        var missingDni = assertThrows(SignUpValidationException.class,
                () -> service.createProfile(1L, EMAIL, AccessRole.TECHNICIAN, profileData("Maria", "Lopez", null)));

        assertEquals("dni", missingDni.violations().getFirst().field());
        verifyNoInteractions(profilesContextFacade, storageService);
    }

    @Test
    void rejectsInvalidPhoneNumberWithoutUploading() {
        var data = new ProfileData("Maria", "Lopez", "+51 123", null, null, "12345678", BIRTH_DATE, PHOTO, "maria.jpg");

        var error = assertThrows(SignUpValidationException.class,
                () -> service.createProfile(1L, EMAIL, AccessRole.CAREGIVER, data));

        assertEquals("phoneNumber", error.violations().getFirst().field());
        verifyNoInteractions(profilesContextFacade, storageService);
    }

    @Test
    void rejectsDuplicateEmailOfTheSameKindAndDniOfAnyKindWithoutUploading() {
        when(profilesContextFacade.existsPersonProfileByEmailAddress(EMAIL)).thenReturn(true);
        when(profilesContextFacade.existsStaffProfileByDni("12345678")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> service.createProfile(1L, EMAIL, AccessRole.CAREGIVER, profileData("Maria", "Lopez", "12345678")));
        assertThrows(DniAlreadyRegisteredException.class,
                () -> service.createProfile(2L, "tech@example.com", AccessRole.TECHNICIAN, profileData("Maria", "Lopez", "12345678")));
        // The DNI of a staff profile also blocks a new person profile.
        assertThrows(DniAlreadyRegisteredException.class,
                () -> service.createProfile(3L, "ana@example.com", AccessRole.CAREGIVER, profileData("Ana", "Torres", "12345678")));

        verify(profilesContextFacade, never()).createPersonProfile(any(), any(), any(), any(), any(), any(), any(), any());
        verify(profilesContextFacade, never()).createStaffProfile(any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(storageService);
    }

    @Test
    void deletesUploadedPhotoOnlyWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createProfile(1L, EMAIL, AccessRole.CAREGIVER, profileData("Maria", "Lopez", "12345678"));
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();

            synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(storageService, never()).delete(anyString());

            synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storageService).delete("profiles/maria_1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsDefaultPhotoUrlWithoutPublicId() {
        assertThrows(IllegalStateException.class,
                () -> new ExternalProfilesService(profilesContextFacade, storageService, "https://cdn.example.com/"));
    }
}
