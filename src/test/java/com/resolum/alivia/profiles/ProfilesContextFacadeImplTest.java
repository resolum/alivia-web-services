package com.resolum.alivia.profiles;

import java.lang.reflect.Proxy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.resolum.alivia.profiles.application.acl.ProfilesContextFacadeImpl;
import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.commands.CreateBusinessProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.CreatePersonProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.CreateStaffProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateBusinessProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdatePersonProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateStaffProfileCommand;
import com.resolum.alivia.profiles.domain.model.queries.ExistsBusinessProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsStaffProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsStaffProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetBusinessProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetPersonProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetStaffProfileByIdQuery;
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
import com.resolum.alivia.profiles.domain.services.StaffProfileCommandService;
import com.resolum.alivia.profiles.domain.services.StaffProfileQueryService;
import com.resolum.alivia.profiles.domain.services.ProfileQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;

import static org.junit.jupiter.api.Assertions.*;

class ProfilesContextFacadeImplTest {

    private final AtomicLong nextPersonId = new AtomicLong(10L);
    private final AtomicLong nextStaffId = new AtomicLong(20L);
    private final AtomicLong nextBusinessId = new AtomicLong(30L);

    private final AtomicBoolean personEmailExists = new AtomicBoolean(false);
    private final AtomicBoolean personDniExists = new AtomicBoolean(false);
    private final AtomicBoolean staffEmailExists = new AtomicBoolean(false);
    private final AtomicBoolean staffDniExists = new AtomicBoolean(false);
    private final AtomicBoolean businessEmailExists = new AtomicBoolean(false);
    private final AtomicBoolean profileIdExists = new AtomicBoolean(false);

    private final AtomicReference<PersonProfile> foundPersonProfile = new AtomicReference<>(null);
    private final AtomicReference<StaffProfile> foundStaffProfile = new AtomicReference<>(null);
    private final AtomicReference<BusinessProfile> foundBusinessProfile = new AtomicReference<>(null);

    private ProfilesContextFacadeImpl facade;
    private BusinessHours defaultBusinessHours;

    @BeforeEach
    void setUp() {
        defaultBusinessHours = new BusinessHours(List.of(
                new WeeklyPeriod(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                        new TimeRange(LocalTime.of(8, 0), LocalTime.of(17, 0)))));

        PersonProfileCommandService personCommandService = new PersonProfileCommandService() {
            @Override
            public Long handle(CreatePersonProfileCommand command) {
                return nextPersonId.get();
            }

            @Override
            public Long handle(UpdatePersonProfileCommand command) {
                return command.profileId();
            }
        };

        PersonProfileQueryService personQueryService = new PersonProfileQueryService() {
            @Override
            public Optional<PersonProfile> handle(GetPersonProfileByIdQuery query) {
                return Optional.ofNullable(foundPersonProfile.get());
            }

            @Override
            public boolean handle(ExistsPersonProfileByEmailAddressQuery query) {
                return personEmailExists.get();
            }

            @Override
            public boolean handle(ExistsPersonProfileByDniQuery query) {
                return personDniExists.get();
            }
        };

        StaffProfileCommandService staffCommandService = new StaffProfileCommandService() {
            @Override
            public Long handle(CreateStaffProfileCommand command) {
                return nextStaffId.get();
            }

            @Override
            public Long handle(UpdateStaffProfileCommand command) {
                return command.profileId();
            }
        };

        StaffProfileQueryService staffQueryService = new StaffProfileQueryService() {
            @Override
            public Optional<StaffProfile> handle(GetStaffProfileByIdQuery query) {
                return Optional.ofNullable(foundStaffProfile.get());
            }

            @Override
            public boolean handle(ExistsStaffProfileByEmailAddressQuery query) {
                return staffEmailExists.get();
            }

            @Override
            public boolean handle(ExistsStaffProfileByDniQuery query) {
                return staffDniExists.get();
            }
        };

        BusinessProfileCommandService businessCommandService = new BusinessProfileCommandService() {
            @Override
            public Long handle(CreateBusinessProfileCommand command) {
                return nextBusinessId.get();
            }

            @Override
            public Long handle(UpdateBusinessProfileCommand command) {
                return command.profileId();
            }
        };

        BusinessProfileQueryService businessQueryService = new BusinessProfileQueryService() {
            @Override
            public Optional<BusinessProfile> handle(GetBusinessProfileByIdQuery query) {
                return Optional.ofNullable(foundBusinessProfile.get());
            }

            @Override
            public boolean handle(ExistsBusinessProfileByEmailAddressQuery query) {
                return businessEmailExists.get();
            }
        };

        ProfileQueryService profileQueryService = query -> profileIdExists.get();

        facade = new ProfilesContextFacadeImpl(
                personCommandService,
                personQueryService,
                staffCommandService,
                staffQueryService,
                businessCommandService,
                businessQueryService,
                profileQueryService);
    }

    @Test
    void createPersonProfileDelegatesAndReturnsId() {
        Long id = facade.createPersonProfile(
                "john.doe@example.com", "http://photo.com", "pub1", "+51987654321",
                "John", "Doe", "12345678", LocalDate.of(1990, 1, 1));
        assertEquals(10L, id);
    }

    @Test
    void createStaffProfileDelegatesAndReturnsId() {
        Long id = facade.createStaffProfile(
                "nurse.jane@example.com", "http://photo.com", "pub2", "+51987654322",
                "Jane", "Doe", "87654321", LocalDate.of(1992, 2, 2));
        assertEquals(20L, id);
    }

    @Test
    void createBusinessProfileDelegatesAndReturnsId() {
        Long id = facade.createBusinessProfile(
                "biz@example.com", "http://photo.com", "pub3", "+51987654323",
                "20123456789", "Acme Care", "Acme Care S.A.C.", "Care services",
                "Health", "+51987654324", defaultBusinessHours.periods(), "We care",
                "support@acme.com", "Av. Javier Prado 123", -12.08, -77.05, "ChIJ...");
        assertEquals(30L, id);
    }

    @Test
    void createBusinessProfileWithVoOverloadDelegatesAndReturnsId() {
        Long id = facade.createBusinessProfile(
                "biz@example.com", "http://photo.com", "pub3", "+51987654323",
                "20123456789", "Acme Care", "Acme Care S.A.C.", "Care services",
                "Health", "+51987654324", defaultBusinessHours, "We care",
                "support@acme.com", "Av. Javier Prado 123", -12.08, -77.05, "ChIJ...");
        assertEquals(30L, id);
    }

    @Test
    void existsPersonProfileByEmailAddressReturnsTrueWhenExists() {
        personEmailExists.set(true);
        assertTrue(facade.existsPersonProfileByEmailAddress("john.doe@example.com"));
    }

    @Test
    void existsPersonProfileByEmailAddressReturnsFalseWhenDoesNotExist() {
        personEmailExists.set(false);
        assertFalse(facade.existsPersonProfileByEmailAddress("unknown@example.com"));
    }

    @Test
    void existsPersonProfileByEmailAddressReturnsFalseOnNullOrBlank() {
        assertFalse(facade.existsPersonProfileByEmailAddress(null));
        assertFalse(facade.existsPersonProfileByEmailAddress(""));
        assertFalse(facade.existsPersonProfileByEmailAddress("   "));
    }

    @Test
    void existsPersonProfileByEmailAddressReturnsFalseOnInvalidEmailFormat() {
        assertFalse(facade.existsPersonProfileByEmailAddress("invalid-email"));
    }

    @Test
    void existsPersonProfileByDniReturnsTrueWhenExists() {
        personDniExists.set(true);
        assertTrue(facade.existsPersonProfileByDni("12345678"));
    }

    @Test
    void existsPersonProfileByDniReturnsFalseWhenDoesNotExist() {
        personDniExists.set(false);
        assertFalse(facade.existsPersonProfileByDni("87654321"));
    }

    @Test
    void existsPersonProfileByDniReturnsFalseOnNullOrBlank() {
        assertFalse(facade.existsPersonProfileByDni(null));
        assertFalse(facade.existsPersonProfileByDni(""));
        assertFalse(facade.existsPersonProfileByDni("   "));
    }

    @Test
    void existsPersonProfileByDniReturnsFalseOnInvalidDniFormat() {
        assertFalse(facade.existsPersonProfileByDni("123")); // DNI must be 8 digits
    }

    @Test
    void existsStaffProfileByEmailAddressReturnsExpectedValues() {
        staffEmailExists.set(true);
        assertTrue(facade.existsStaffProfileByEmailAddress("staff@example.com"));

        staffEmailExists.set(false);
        assertFalse(facade.existsStaffProfileByEmailAddress("staff@example.com"));

        assertFalse(facade.existsStaffProfileByEmailAddress(null));
        assertFalse(facade.existsStaffProfileByEmailAddress("   "));
        assertFalse(facade.existsStaffProfileByEmailAddress("invalid-email"));
    }

    @Test
    void existsStaffProfileByDniReturnsExpectedValues() {
        staffDniExists.set(true);
        assertTrue(facade.existsStaffProfileByDni("12345678"));

        staffDniExists.set(false);
        assertFalse(facade.existsStaffProfileByDni("12345678"));

        assertFalse(facade.existsStaffProfileByDni(null));
        assertFalse(facade.existsStaffProfileByDni("   "));
        assertFalse(facade.existsStaffProfileByDni("short"));
    }

    @Test
    void existsBusinessProfileByEmailAddressReturnsExpectedValues() {
        businessEmailExists.set(true);
        assertTrue(facade.existsBusinessProfileByEmailAddress("business@example.com"));

        businessEmailExists.set(false);
        assertFalse(facade.existsBusinessProfileByEmailAddress("business@example.com"));

        assertFalse(facade.existsBusinessProfileByEmailAddress(null));
        assertFalse(facade.existsBusinessProfileByEmailAddress("   "));
        assertFalse(facade.existsBusinessProfileByEmailAddress("invalid"));
    }

    @Test
    void existsProfileByIdReturnsExpectedValues() {
        profileIdExists.set(true);
        assertTrue(facade.existsProfileById(1L));

        profileIdExists.set(false);
        assertFalse(facade.existsProfileById(1L));

        assertFalse(facade.existsProfileById(null));
        assertFalse(facade.existsProfileById(0L));
        assertFalse(facade.existsProfileById(-5L));
    }

    @Test
    void fetchPersonProfileFullNameByIdReturnsFullNameWhenPresent() {
        var person = new PersonProfile(
                new EmailAddress("p@example.com"),
                new Photo("http://img", "pid"),
                new PhoneNumber("51987654321"),
                new PersonName("Carlos", "Santana"),
                new Dni("12345678"),
                new BirthDate(LocalDate.of(1985, 5, 20)));
        foundPersonProfile.set(person);

        Optional<String> fullName = facade.fetchPersonProfileFullNameById(1L);
        assertTrue(fullName.isPresent());
        assertEquals("Carlos Santana", fullName.get());
    }

    @Test
    void fetchPersonProfileFullNameByIdReturnsEmptyWhenNotFoundOrInvalidId() {
        foundPersonProfile.set(null);
        assertTrue(facade.fetchPersonProfileFullNameById(1L).isEmpty());
        assertTrue(facade.fetchPersonProfileFullNameById(null).isEmpty());
        assertTrue(facade.fetchPersonProfileFullNameById(0L).isEmpty());
        assertTrue(facade.fetchPersonProfileFullNameById(-1L).isEmpty());
    }

    @Test
    void fetchStaffProfileFullNameByIdReturnsFullNameWhenPresent() {
        var staff = new StaffProfile(
                new EmailAddress("staff@example.com"),
                new Photo("http://img", "pid"),
                new PhoneNumber("51987654322"),
                new PersonName("Ana", "Gomez"),
                new Dni("87654321"),
                new BirthDate(LocalDate.of(1990, 8, 15)));
        foundStaffProfile.set(staff);

        Optional<String> fullName = facade.fetchStaffProfileFullNameById(2L);
        assertTrue(fullName.isPresent());
        assertEquals("Ana Gomez", fullName.get());
    }

    @Test
    void fetchStaffProfileFullNameByIdReturnsEmptyWhenNotFoundOrInvalidId() {
        foundStaffProfile.set(null);
        assertTrue(facade.fetchStaffProfileFullNameById(2L).isEmpty());
        assertTrue(facade.fetchStaffProfileFullNameById(null).isEmpty());
        assertTrue(facade.fetchStaffProfileFullNameById(0L).isEmpty());
        assertTrue(facade.fetchStaffProfileFullNameById(-2L).isEmpty());
    }

    @Test
    void fetchBusinessProfileNameByIdReturnsBusinessNameWhenPresent() {
        var business = new BusinessProfile(
                new EmailAddress("biz@example.com"),
                new Photo("http://img", "pid"),
                new PhoneNumber("51987654323"),
                new Ruc("20123456789"),
                new BusinessName("Alivia Clinica"),
                new LegalName("Alivia Clinica S.A.C."),
                "Clinica medica",
                "Salud",
                new PhoneNumber("51987654324"),
                defaultBusinessHours,
                "Tu salud primero",
                new EmailAddress("support@alivia.com"),
                new Address("Av Principal 456", -12.1, -77.0, "place_123"));
        foundBusinessProfile.set(business);

        Optional<String> name = facade.fetchBusinessProfileNameById(3L);
        assertTrue(name.isPresent());
        assertEquals("Alivia Clinica", name.get());
    }

    @Test
    void fetchBusinessProfileNameByIdReturnsEmptyWhenNotFoundOrInvalidId() {
        foundBusinessProfile.set(null);
        assertTrue(facade.fetchBusinessProfileNameById(3L).isEmpty());
        assertTrue(facade.fetchBusinessProfileNameById(null).isEmpty());
        assertTrue(facade.fetchBusinessProfileNameById(0L).isEmpty());
        assertTrue(facade.fetchBusinessProfileNameById(-3L).isEmpty());
    }
}
