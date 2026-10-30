package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.application.acl.ProfilesContextFacadeImpl;
import com.resolum.alivia.profiles.domain.exceptions.BusinessNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.BusinessProfileNotFoundException;
import com.resolum.alivia.profiles.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.LegalNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.RucAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.LegalName;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Ruc;
import com.resolum.alivia.profiles.domain.services.BusinessProfileCommandService;
import com.resolum.alivia.profiles.domain.services.BusinessProfileQueryService;
import com.resolum.alivia.profiles.domain.services.PersonProfileCommandService;
import com.resolum.alivia.profiles.domain.services.PersonProfileQueryService;
import com.resolum.alivia.profiles.domain.services.ProfileQueryService;
import com.resolum.alivia.profiles.domain.services.StaffProfileCommandService;
import com.resolum.alivia.profiles.domain.services.StaffProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.BusinessProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the repository-backed branches of {@link ProfilesContextFacadeImpl}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfilesContextFacadeImpl repository path Tests")
class ProfilesContextFacadeRepositoryPathTest {

    private static final LocalDate BIRTH = LocalDate.of(1990, 5, 20);
    private static final List<WeeklyPeriod> PERIODS = List.of(new WeeklyPeriod(
            Set.of(DayOfWeek.MONDAY), new TimeRange(LocalTime.of(8, 0), LocalTime.of(17, 0))));

    @Mock private PersonProfileCommandService personCommandService;
    @Mock private PersonProfileQueryService personQueryService;
    @Mock private StaffProfileCommandService staffCommandService;
    @Mock private StaffProfileQueryService staffQueryService;
    @Mock private BusinessProfileCommandService businessCommandService;
    @Mock private BusinessProfileQueryService businessQueryService;
    @Mock private ProfileQueryService profileQueryService;
    @Mock private PersonProfileRepository personRepository;
    @Mock private StaffProfileRepository staffRepository;
    @Mock private BusinessProfileRepository businessRepository;

    private ProfilesContextFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new ProfilesContextFacadeImpl(personCommandService, personQueryService, staffCommandService,
                staffQueryService, businessCommandService, businessQueryService, profileQueryService,
                personRepository, staffRepository, businessRepository);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static StaffProfile staff() {
        return new StaffProfile(new EmailAddress("ana@example.com"), new Photo("http://img", "pid"),
                new PhoneNumber("51987654322"), new PersonName("Ana", "Gomez"), new Dni("12345678"),
                new BirthDate(BIRTH));
    }

    private static BusinessProfile business() {
        return new BusinessProfile(new EmailAddress("biz@example.com"), new Photo("http://img", "pid"),
                new PhoneNumber("51987654323"), new Ruc("20123456789"), new BusinessName("Alivia Clinica"),
                new LegalName("Alivia Clinica S.A.C."), "Clinica", "Salud", new PhoneNumber("51987654324"),
                new BusinessHours(PERIODS), "Slogan", new EmailAddress("support@alivia.com"),
                new Address("Av Principal 456", -12.1, -77.0, "place_123"));
    }

    // ---------- Person ----------

    @Test
    void createPersonProfilePersistsThroughRepository() {
        when(personRepository.existsByEmailAddress(any())).thenReturn(false);
        when(personRepository.existsByDni(any())).thenReturn(false);
        when(personRepository.save(any(PersonProfile.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));

        Long id = facade.createPersonProfile("p@example.com", "http://img", "pid", "51987654321",
                "Juan", "Perez", "87654321", BIRTH);

        assertThat(id).isEqualTo(7L);
        verify(personCommandService, never()).handle(any(com.resolum.alivia.profiles.domain.model.commands.CreatePersonProfileCommand.class));
    }

    @Test
    void createPersonProfileRejectsDuplicates() {
        when(personRepository.existsByEmailAddress(any())).thenReturn(true);
        assertThatThrownBy(() -> facade.createPersonProfile("p@example.com", "http://img", "pid",
                "51987654321", "Juan", "Perez", "87654321", BIRTH))
                .isInstanceOf(EmailAddressAlreadyExistsException.class);

        when(personRepository.existsByEmailAddress(any())).thenReturn(false);
        when(personRepository.existsByDni(any())).thenReturn(true);
        assertThatThrownBy(() -> facade.createPersonProfile("p@example.com", "http://img", "pid",
                "51987654321", "Juan", "Perez", "87654321", BIRTH))
                .isInstanceOf(DniAlreadyExistsException.class);
    }

    // ---------- Staff ----------

    @Test
    void createStaffProfilePersistsThroughRepository() {
        when(staffRepository.existsByEmailAddress(any())).thenReturn(false);
        when(staffRepository.existsByDni(any())).thenReturn(false);
        when(staffRepository.save(any(StaffProfile.class))).thenAnswer(inv -> withId(inv.getArgument(0), 8L));

        Long id = facade.createStaffProfile("s@example.com", "http://img", "pid", "51987654321",
                "Ana", "Gomez", "12345678", BIRTH);

        assertThat(id).isEqualTo(8L);
    }

    @Test
    void createStaffProfileRejectsDuplicates() {
        when(staffRepository.existsByEmailAddress(any())).thenReturn(true);
        assertThatThrownBy(() -> facade.createStaffProfile("s@example.com", "http://img", "pid",
                "51987654321", "Ana", "Gomez", "12345678", BIRTH))
                .isInstanceOf(EmailAddressAlreadyExistsException.class);

        when(staffRepository.existsByEmailAddress(any())).thenReturn(false);
        when(staffRepository.existsByDni(any())).thenReturn(true);
        assertThatThrownBy(() -> facade.createStaffProfile("s@example.com", "http://img", "pid",
                "51987654321", "Ana", "Gomez", "12345678", BIRTH))
                .isInstanceOf(DniAlreadyExistsException.class);
    }

    @Test
    void updateStaffProfileUpdatesPhotoAndPhone() {
        StaffProfile existing = withId(staff(), 2L);
        when(staffRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(staffRepository.save(existing)).thenReturn(existing);

        Long id = facade.updateStaffProfile(2L, "Ana Maria", "Gomez", BIRTH, "51987654321", "http://new", "newpid");

        assertThat(id).isEqualTo(2L);
        assertThat(existing.getPhoto().photoUrl()).isEqualTo("http://new");
        assertThat(existing.getPhoneNumber().phoneNumber()).isEqualTo("51987654321");
    }

    @Test
    void updateStaffProfileKeepsPhotoAndPhoneWhenBlank() {
        StaffProfile existing = withId(staff(), 2L);
        when(staffRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(staffRepository.save(existing)).thenReturn(existing);

        facade.updateStaffProfile(2L, "Ana", "Gomez", BIRTH, " ", null, null);

        assertThat(existing.getPhoto().photoUrl()).isEqualTo("http://img");
        assertThat(existing.getPhoneNumber().phoneNumber()).isEqualTo("51987654322");
    }

    @Test
    void updateStaffProfileThrowsWhenMissing() {
        when(staffRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.updateStaffProfile(99L, "Ana", "Gomez", BIRTH, "51987654321", null, null))
                .isInstanceOf(StaffProfileNotFoundException.class);
    }

    // ---------- Business ----------

    @Test
    void createBusinessProfilePersistsThroughRepository() {
        when(businessRepository.save(any(BusinessProfile.class))).thenAnswer(inv -> withId(inv.getArgument(0), 9L));

        Long id = facade.createBusinessProfile("biz@example.com", "http://img", "pid", "51987654323",
                "20123456789", "Alivia Clinica", "Alivia Clinica S.A.C.", "Clinica", "Salud",
                "51987654324", PERIODS, "Slogan", "support@alivia.com", "Av Principal 456",
                -12.1, -77.0, "place_123");

        assertThat(id).isEqualTo(9L);
    }

    @Test
    void createBusinessProfileWithBusinessHoursOverloadDelegates() {
        when(businessRepository.save(any(BusinessProfile.class))).thenAnswer(inv -> withId(inv.getArgument(0), 10L));

        Long id = facade.createBusinessProfile("biz@example.com", "http://img", "pid", "51987654323",
                "20123456789", "Alivia Clinica", "Alivia Clinica S.A.C.", "Clinica", "Salud",
                "51987654324", new BusinessHours(PERIODS), "Slogan", "support@alivia.com",
                "Av Principal 456", -12.1, -77.0, "place_123");

        assertThat(id).isEqualTo(10L);
    }

    @Test
    void createBusinessProfileRejectsEachDuplicate() {
        when(businessRepository.existsByEmailAddress(any())).thenReturn(true);
        assertThatThrownBy(this::createDefaultBusiness).isInstanceOf(EmailAddressAlreadyExistsException.class);

        when(businessRepository.existsByEmailAddress(any())).thenReturn(false);
        when(businessRepository.existsByRuc(any())).thenReturn(true);
        assertThatThrownBy(this::createDefaultBusiness).isInstanceOf(RucAlreadyExistsException.class);

        when(businessRepository.existsByRuc(any())).thenReturn(false);
        when(businessRepository.existsByBusinessName(any())).thenReturn(true);
        assertThatThrownBy(this::createDefaultBusiness).isInstanceOf(BusinessNameAlreadyExistsException.class);

        when(businessRepository.existsByBusinessName(any())).thenReturn(false);
        when(businessRepository.existsByLegalName(any())).thenReturn(true);
        assertThatThrownBy(this::createDefaultBusiness).isInstanceOf(LegalNameAlreadyExistsException.class);
    }

    private void createDefaultBusiness() {
        facade.createBusinessProfile("biz@example.com", "http://img", "pid", "51987654323",
                "20123456789", "Alivia Clinica", "Alivia Clinica S.A.C.", "Clinica", "Salud",
                "51987654324", PERIODS, "Slogan", "support@alivia.com", "Av Principal 456",
                -12.1, -77.0, "place_123");
    }

    @Test
    void updateBusinessProfileUpdatesProvidedFields() {
        BusinessProfile existing = withId(business(), 3L);
        when(businessRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(businessRepository.save(existing)).thenReturn(existing);

        Long id = facade.updateBusinessProfile(3L, "http://new", "newpid", "51987654321",
                "Alivia Sur", "Alivia Sur S.A.C.", "Nueva", "Salud", "51987654325",
                PERIODS, "Nuevo", "help@alivia.com");

        assertThat(id).isEqualTo(3L);
        assertThat(existing.getBusinessName().businessName()).isEqualTo("Alivia Sur");
        assertThat(existing.getPhoto().photoUrl()).isEqualTo("http://new");
        assertThat(existing.getSupportEmail().email()).isEqualTo("help@alivia.com");
    }

    @Test
    void updateBusinessProfileKeepsExistingValuesWhenBlank() {
        BusinessProfile existing = withId(business(), 3L);
        when(businessRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(businessRepository.save(existing)).thenReturn(existing);

        facade.updateBusinessProfile(3L, null, null, "", "Alivia Clinica", "Alivia Clinica S.A.C.",
                "Clinica", "Salud", null, null, "Slogan", " ");

        assertThat(existing.getPhoto().photoUrl()).isEqualTo("http://img");
        assertThat(existing.getSupportEmail().email()).isEqualTo("support@alivia.com");
        assertThat(existing.getPhoneNumber().phoneNumber()).isEqualTo("51987654323");
    }

    @Test
    void updateBusinessProfileThrowsWhenMissing() {
        when(businessRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.updateBusinessProfile(99L, null, null, null, "Alivia", "Alivia S.A.C.",
                null, null, null, null, null, null))
                .isInstanceOf(BusinessProfileNotFoundException.class);
    }

    @Test
    void existsBusinessProfileChecksDelegateToRepository() {
        when(businessRepository.existsByRuc(any())).thenReturn(true);
        when(businessRepository.existsByBusinessName(any())).thenReturn(true);
        when(businessRepository.existsByLegalName(any())).thenReturn(false);

        assertThat(facade.existsBusinessProfileByRuc("20123456789")).isTrue();
        assertThat(facade.existsBusinessProfileByBusinessName("Alivia Clinica")).isTrue();
        assertThat(facade.existsBusinessProfileByLegalName("Alivia Clinica S.A.C.")).isFalse();
        assertThat(facade.existsBusinessProfileByRuc("invalid")).isFalse();
    }
}
