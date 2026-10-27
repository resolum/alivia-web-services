package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.commandservices.UserCommandServiceImpl;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalCommunicationsService;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.domain.exceptions.DniAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.infrastructure.adapters.UserRepositoryImpl;
import com.resolum.alivia.iam.infrastructure.persistence.jpa.repositories.UserPersistenceRepository;
import com.resolum.alivia.profiles.application.acl.ProfilesContextFacadeImpl;
import com.resolum.alivia.profiles.application.internal.commandservices.PersonProfileCommandServiceImpl;
import com.resolum.alivia.profiles.application.internal.commandservices.StaffProfileCommandServiceImpl;
import com.resolum.alivia.profiles.application.internal.queryservices.PersonProfileQueryServiceImpl;
import com.resolum.alivia.profiles.application.internal.queryservices.ProfileQueryServiceImpl;
import com.resolum.alivia.profiles.application.internal.queryservices.StaffProfileQueryServiceImpl;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.services.BusinessProfileCommandService;
import com.resolum.alivia.profiles.domain.services.BusinessProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sign-up against a real database: IAM and Profiles share one transaction, so the user and
 * its profile are stored together or not at all.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED) // the service must commit or roll back on its own
@Import({UserCommandServiceImpl.class, UserRepositoryImpl.class, ExternalProfilesService.class,
        ProfilesContextFacadeImpl.class, PersonProfileCommandServiceImpl.class, PersonProfileQueryServiceImpl.class,
        StaffProfileCommandServiceImpl.class, StaffProfileQueryServiceImpl.class, ProfileQueryServiceImpl.class,
        ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        // H2 in PostgreSQL mode, closer to the production database than the default embedded one.
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:signup;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "alivia.iam.profiles.default-photo-url=https://cdn.example.com/default.png"})
class SignUpProfileIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final byte[] JUAN_PHOTO = {1, 2, 3};

    @Autowired
    private UserCommandService userCommandService;
    @Autowired
    private UserPersistenceRepository users;
    @Autowired
    private PersonProfileRepository personProfiles;
    @Autowired
    private StaffProfileRepository staffProfiles;
    @Autowired
    private ProfilesContextFacade profilesContextFacade;

    @MockitoBean
    private HashingService hashingService;
    @MockitoBean
    private EmailVerificationTokenService emailVerificationTokenService;
    @MockitoBean
    private ExternalCommunicationsService externalCommunicationsService;
    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private StorageService storageService;
    @MockitoBean
    private BusinessProfileCommandService businessProfileCommandService;
    @MockitoBean
    private BusinessProfileQueryService businessProfileQueryService;

    @BeforeEach
    void stubCredentials() {
        when(hashingService.encode(anyString())).thenReturn("bcrypt-hash");
        when(emailVerificationTokenService.generate()).thenAnswer(invocation -> new EmailVerificationTokenService.GeneratedToken(
                "raw-" + System.nanoTime(), new EmailVerificationToken("hash-" + System.nanoTime(),
                        LocalDateTime.now().plusHours(24))));
        when(emailVerificationTokenService.buildVerificationLink(anyString())).thenReturn("http://localhost/verify");
        when(storageService.upload(JUAN_PHOTO, "juan.jpg")).thenReturn(Map.of(
                "url", "https://res.cloudinary.com/demo/image/upload/v1/profiles/juan_1.jpg", "publicId", "profiles/juan_1"));
    }

    @AfterEach
    void cleanDatabase() {
        users.deleteAllInBatch();
        personProfiles.deleteAllInBatch();
        staffProfiles.deleteAllInBatch();
    }

    private static SignUpCommand caregiver(String email, String dni) {
        return new SignUpCommand(email, "Strong1!", "Maria Elena", "Lopez Garcia", "+51987654321", true,
                AccessRole.CAREGIVER, null, null, dni, BIRTH_DATE, null, null);
    }

    private static SignUpCommand technician(String email, String dni) {
        return new SignUpCommand(email, "Strong1!", "Juan", "Perez", "+51 912 345 678", true,
                AccessRole.TECHNICIAN, 10L, null, dni, BIRTH_DATE, JUAN_PHOTO, "juan.jpg");
    }

    @Test
    void signUpStoresUserLinkedToItsPersonProfile() {
        var user = userCommandService.handle(caregiver("maria@example.com", "12345678"));

        var stored = users.findById(user.getId()).orElseThrow();
        var profile = personProfiles.findById(stored.getProfileId()).orElseThrow();
        assertEquals("maria@example.com", profile.getEmailAddress().email());
        assertEquals("Maria Elena", profile.getPersonName().firstName());
        assertEquals("Lopez Garcia", profile.getPersonName().lastName());
        assertEquals("51987654321", profile.getPhoneNumber().phoneNumber());
        assertEquals("https://cdn.example.com/default.png", profile.getPhoto().photoUrl());
        assertTrue(profilesContextFacade.existsProfileById(stored.getProfileId()));
        assertEquals("Maria Elena Lopez Garcia",
                profilesContextFacade.fetchPersonProfileFullNameById(stored.getProfileId()).orElseThrow());
    }

    @Test
    void technicianSignUpStoresStaffProfile() {
        var user = userCommandService.handle(technician("juan@example.com", "87654321"));

        var stored = users.findById(user.getId()).orElseThrow();
        var profile = staffProfiles.findById(stored.getProfileId()).orElseThrow();
        assertEquals("Perez", profile.getPersonName().lastName());
        assertEquals("51912345678", profile.getPhoneNumber().phoneNumber());
        assertEquals("https://res.cloudinary.com/demo/image/upload/v1/profiles/juan_1.jpg", profile.getPhoto().photoUrl());
        assertEquals("profiles/juan_1", profile.getPhoto().photoPublicId());
        assertEquals(0, personProfiles.count());
    }

    @Test
    void dniIsUniqueAcrossPersonAndStaffProfiles() {
        userCommandService.handle(caregiver("maria@example.com", "12345678"));

        assertThrows(DniAlreadyRegisteredException.class,
                () -> userCommandService.handle(technician("juan@example.com", "12345678")));

        assertEquals(1, users.count());
        assertEquals(0, staffProfiles.count());
    }

    @Test
    void duplicateDniLeavesNoSecondUser() {
        userCommandService.handle(caregiver("maria@example.com", "12345678"));

        assertThrows(DniAlreadyRegisteredException.class,
                () -> userCommandService.handle(caregiver("other@example.com", "12345678")));

        assertEquals(1, users.count());
        assertEquals(1, personProfiles.count());
    }

    @Test
    void rolledBackSignUpDeletesUploadedPhoto() {
        doThrow(new IllegalStateException("mail provider down"))
                .when(externalCommunicationsService).sendVerificationEmail(anyString(), anyString());

        assertThrows(RegistrationUnavailableException.class,
                () -> userCommandService.handle(technician("juan@example.com", "87654321")));

        verify(storageService).delete("profiles/juan_1");
        assertEquals(0, staffProfiles.count());
    }

    @Test
    void failureAfterProfileCreationRollsBackUserAndProfile() {
        doThrow(new IllegalStateException("mail provider down"))
                .when(externalCommunicationsService).sendVerificationEmail(anyString(), anyString());

        assertThrows(RegistrationUnavailableException.class,
                () -> userCommandService.handle(caregiver("maria@example.com", "12345678")));

        assertEquals(0, users.count());
        assertEquals(0, personProfiles.count());
    }

    @Test
    void databaseRejectsSecondPersonProfileWithSameDni() {
        personProfiles.saveAndFlush(personProfile("first@example.com"));

        // Two concurrent sign-ups can both pass the existence check; the unique key stops the second insert.
        assertThrows(DataIntegrityViolationException.class,
                () -> personProfiles.saveAndFlush(personProfile("second@example.com")));
        assertEquals(1, personProfiles.count());
    }

    private static PersonProfile personProfile(String email) {
        return new PersonProfile(new EmailAddress(email), new Photo("https://cdn.example.com/a.png", "a"),
                new PhoneNumber("51987654321"), new PersonName("Maria", "Lopez"), new Dni("12345678"),
                new BirthDate(BIRTH_DATE));
    }
}
