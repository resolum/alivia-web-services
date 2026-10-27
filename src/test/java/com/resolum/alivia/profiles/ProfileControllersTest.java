package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.commands.*;
import com.resolum.alivia.profiles.domain.model.queries.GetBusinessProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetPersonProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetStaffProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.*;
import com.resolum.alivia.profiles.domain.services.*;
import com.resolum.alivia.profiles.application.internal.commandservices.PersonProfileCommandServiceImpl;
import com.resolum.alivia.profiles.domain.exceptions.*;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.interfaces.rest.controllers.BusinessProfilesController;
import com.resolum.alivia.profiles.interfaces.rest.controllers.PersonProfilesController;
import com.resolum.alivia.profiles.interfaces.rest.controllers.StaffProfilesController;
import com.resolum.alivia.shared.domain.model.valueobjects.*;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllersTest {

    private BusinessProfileCommandService businessCmdService;
    private BusinessProfileQueryService businessQueryService;
    private MockMvc businessMvc;

    private PersonProfileCommandService personCmdService;
    private PersonProfileQueryService personQueryService;
    private MockMvc personMvc;

    private StaffProfileCommandService staffCmdService;
    private StaffProfileQueryService staffQueryService;
    private MockMvc staffMvc;

    @BeforeEach
    void setUp() {
        businessCmdService = mock(BusinessProfileCommandService.class);
        businessQueryService = mock(BusinessProfileQueryService.class);
        businessMvc = MockMvcBuilders.standaloneSetup(
                new BusinessProfilesController(businessCmdService, businessQueryService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();

        personCmdService = mock(PersonProfileCommandService.class);
        personQueryService = mock(PersonProfileQueryService.class);
        personMvc = MockMvcBuilders.standaloneSetup(
                new PersonProfilesController(personCmdService, personQueryService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();

        staffCmdService = mock(StaffProfileCommandService.class);
        staffQueryService = mock(StaffProfileQueryService.class);
        staffMvc = MockMvcBuilders.standaloneSetup(
                new StaffProfilesController(staffCmdService, staffQueryService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // --- BusinessProfilesController ---

    @Test
    void createBusinessProfileReturns201WhenSuccessful() throws Exception {
        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var hours = new BusinessHours(List.of(new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range)));
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");

        var profile = new BusinessProfile(
                new EmailAddress("corp@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new Ruc("20123456789"),
                new BusinessName("Alivia Lab"),
                new LegalName("Alivia Lab SAC"),
                "Desc", "Salud",
                new PhoneNumber("51987654322"),
                hours, "Slogan",
                new EmailAddress("support@alivia.com"),
                address
        );

        when(businessCmdService.handle(any(CreateBusinessProfileCommand.class))).thenReturn(1L);
        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(1L))).thenReturn(Optional.of(profile));

        String json = """
                {
                    "emailAddress": "corp@alivia.com",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1",
                    "whatsappNum": "51987654321",
                    "ruc": "20123456789",
                    "businessName": "Alivia Lab",
                    "legalName": "Alivia Lab SAC",
                    "description": "Desc",
                    "mainActivity": "Salud",
                    "emergencyPhone": "51987654322",
                    "businessHours": {
                        "periods": [
                            {
                                "days": ["MONDAY"],
                                "range": { "start": "08:00:00", "end": "18:00:00" }
                            }
                        ]
                    },
                    "slogan": "Slogan",
                    "supportEmail": "support@alivia.com",
                    "streetAddress": "Av. Brasil 123",
                    "latitude": -12.05,
                    "longitude": -77.04,
                    "placeId": "place_1"
                }
                """;

        businessMvc.perform(post("/api/v1/business-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailAddress").value("corp@alivia.com"))
                .andExpect(jsonPath("$.businessName").value("Alivia Lab"));
    }

    @Test
    void createBusinessProfileReturns500WhenNotFoundAfterCreation() throws Exception {
        when(businessCmdService.handle(any(CreateBusinessProfileCommand.class))).thenReturn(1L);
        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(1L))).thenReturn(Optional.empty());

        String json = """
                {
                    "emailAddress": "corp@alivia.com",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1",
                    "whatsappNum": "51987654321",
                    "ruc": "20123456789",
                    "businessName": "Alivia Lab",
                    "legalName": "Alivia Lab SAC",
                    "description": "Desc",
                    "mainActivity": "Salud",
                    "emergencyPhone": "51987654322",
                    "businessHours": {
                        "periods": [
                            {
                                "days": ["MONDAY"],
                                "range": { "start": "08:00:00", "end": "18:00:00" }
                            }
                        ]
                    },
                    "slogan": "Slogan",
                    "supportEmail": "support@alivia.com",
                    "streetAddress": "Av. Brasil 123",
                    "latitude": -12.05,
                    "longitude": -77.04,
                    "placeId": "place_1"
                }
                """;

        businessMvc.perform(post("/api/v1/business-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"));
    }

    @Test
    void getBusinessProfileByIdReturns200Or404() throws Exception {
        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var hours = new BusinessHours(List.of(new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range)));
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");

        var profile = new BusinessProfile(
                new EmailAddress("corp@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new Ruc("20123456789"),
                new BusinessName("Alivia Lab"),
                new LegalName("Alivia Lab SAC"),
                "Desc", "Salud",
                new PhoneNumber("51987654322"),
                hours, "Slogan",
                new EmailAddress("support@alivia.com"),
                address
        );

        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(1L))).thenReturn(Optional.of(profile));
        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(2L))).thenReturn(Optional.empty());

        businessMvc.perform(get("/api/v1/business-profiles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Alivia Lab"));

        businessMvc.perform(get("/api/v1/business-profiles/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("business_profile_not_found"));
    }

    @Test
    void updateBusinessProfileReturns200Or404() throws Exception {
        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var hours = new BusinessHours(List.of(new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range)));
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");

        var profile = new BusinessProfile(
                new EmailAddress("corp@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new Ruc("20123456789"),
                new BusinessName("Alivia Lab"),
                new LegalName("Alivia Lab SAC"),
                "Desc", "Salud",
                new PhoneNumber("51987654322"),
                hours, "Slogan",
                new EmailAddress("support@alivia.com"),
                address
        );

        when(businessCmdService.handle(any(UpdateBusinessProfileCommand.class))).thenReturn(1L);
        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(1L))).thenReturn(Optional.of(profile));
        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(2L))).thenReturn(Optional.empty());

        String json = """
                {
                    "emailAddress": "corp@alivia.com",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1",
                    "whatsappNum": "51987654321",
                    "ruc": "20123456789",
                    "businessName": "Alivia Lab",
                    "legalName": "Alivia Lab SAC",
                    "description": "Desc",
                    "mainActivity": "Salud",
                    "emergencyPhone": "51987654322",
                    "businessHours": {
                        "periods": [
                            {
                                "days": ["MONDAY"],
                                "range": { "start": "08:00:00", "end": "18:00:00" }
                            }
                        ]
                    },
                    "slogan": "Slogan",
                    "supportEmail": "support@alivia.com",
                    "streetAddress": "Av. Brasil 123",
                    "latitude": -12.05,
                    "longitude": -77.04,
                    "placeId": "place_1"
                }
                """;

        businessMvc.perform(put("/api/v1/business-profiles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Alivia Lab"));

        // If after update query returns empty
        when(businessCmdService.handle(any(UpdateBusinessProfileCommand.class))).thenReturn(2L);
        businessMvc.perform(put("/api/v1/business-profiles/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("business_profile_not_found"));
    }

    // --- PersonProfilesController ---

    @Test
    void createPersonProfileReturns201Or500() throws Exception {
        var person = new PersonProfile(
                new EmailAddress("juan@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Juan", "Perez"),
                new Dni("12345678"),
                new BirthDate(LocalDate.of(1990, 5, 10))
        );

        when(personCmdService.handle(any(CreatePersonProfileCommand.class))).thenReturn(10L);
        when(personQueryService.handle(new GetPersonProfileByIdQuery(10L))).thenReturn(Optional.of(person));

        String json = """
                {
                    "emailAddress": "juan@alivia.com",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1",
                    "phoneNumber": "51987654321",
                    "firstName": "Juan",
                    "lastName": "Perez",
                    "dni": "12345678",
                    "birthDate": "1990-05-10"
                }
                """;

        personMvc.perform(post("/api/v1/person-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Juan"));

        // Server-side inconsistency when the created profile cannot be read back
        when(personCmdService.handle(any(CreatePersonProfileCommand.class))).thenReturn(99L);
        when(personQueryService.handle(new GetPersonProfileByIdQuery(99L))).thenReturn(Optional.empty());

        personMvc.perform(post("/api/v1/person-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"));
    }

    @Test
    void getPersonProfileByIdReturns200Or404() throws Exception {
        var person = new PersonProfile(
                new EmailAddress("juan@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Juan", "Perez"),
                new Dni("12345678"),
                new BirthDate(LocalDate.of(1990, 5, 10))
        );

        when(personQueryService.handle(new GetPersonProfileByIdQuery(10L))).thenReturn(Optional.of(person));
        when(personQueryService.handle(new GetPersonProfileByIdQuery(20L))).thenReturn(Optional.empty());

        personMvc.perform(get("/api/v1/person-profiles/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Juan"));

        personMvc.perform(get("/api/v1/person-profiles/20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("person_profile_not_found"));
    }

    @Test
    void updatePersonProfileReturns200Or404() throws Exception {
        var person = new PersonProfile(
                new EmailAddress("juan@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Juan", "Perez"),
                new Dni("12345678"),
                new BirthDate(LocalDate.of(1990, 5, 10))
        );

        when(personCmdService.handle(any(UpdatePersonProfileCommand.class))).thenReturn(10L);
        when(personQueryService.handle(new GetPersonProfileByIdQuery(10L))).thenReturn(Optional.of(person));
        when(personQueryService.handle(new GetPersonProfileByIdQuery(20L))).thenReturn(Optional.empty());

        String json = """
                {
                    "firstName": "Juan",
                    "lastName": "Perez",
                    "birthDate": "1990-05-10",
                    "phoneNumber": "51987654321",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1"
                }
                """;

        personMvc.perform(put("/api/v1/person-profiles/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Juan"));

        when(personCmdService.handle(any(UpdatePersonProfileCommand.class))).thenReturn(20L);
        personMvc.perform(put("/api/v1/person-profiles/20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("person_profile_not_found"));
    }

    // --- StaffProfilesController ---

    @Test
    void createStaffProfileReturns201Or500() throws Exception {
        var staff = new StaffProfile(
                new EmailAddress("ana@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Ana", "Torres"),
                new Dni("87654321"),
                new BirthDate(LocalDate.of(1988, 12, 1))
        );

        when(staffCmdService.handle(any(CreateStaffProfileCommand.class))).thenReturn(30L);
        when(staffQueryService.handle(new GetStaffProfileByIdQuery(30L))).thenReturn(Optional.of(staff));

        String json = """
                {
                    "emailAddress": "ana@alivia.com",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1",
                    "phoneNumber": "51987654321",
                    "firstName": "Ana",
                    "lastName": "Torres",
                    "dni": "87654321",
                    "birthDate": "1988-12-01"
                }
                """;

        staffMvc.perform(post("/api/v1/staff-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ana"));

        when(staffCmdService.handle(any(CreateStaffProfileCommand.class))).thenReturn(99L);
        when(staffQueryService.handle(new GetStaffProfileByIdQuery(99L))).thenReturn(Optional.empty());

        staffMvc.perform(post("/api/v1/staff-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"));
    }

    @Test
    void getStaffProfileByIdReturns200Or404() throws Exception {
        var staff = new StaffProfile(
                new EmailAddress("ana@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Ana", "Torres"),
                new Dni("87654321"),
                new BirthDate(LocalDate.of(1988, 12, 1))
        );

        when(staffQueryService.handle(new GetStaffProfileByIdQuery(30L))).thenReturn(Optional.of(staff));
        when(staffQueryService.handle(new GetStaffProfileByIdQuery(40L))).thenReturn(Optional.empty());

        staffMvc.perform(get("/api/v1/staff-profiles/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"));

        staffMvc.perform(get("/api/v1/staff-profiles/40"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("staff_profile_not_found"));
    }

    @Test
    void updateStaffProfileReturns200Or404() throws Exception {
        var staff = new StaffProfile(
                new EmailAddress("ana@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Ana", "Torres"),
                new Dni("87654321"),
                new BirthDate(LocalDate.of(1988, 12, 1))
        );

        when(staffCmdService.handle(any(UpdateStaffProfileCommand.class))).thenReturn(30L);
        when(staffQueryService.handle(new GetStaffProfileByIdQuery(30L))).thenReturn(Optional.of(staff));
        when(staffQueryService.handle(new GetStaffProfileByIdQuery(40L))).thenReturn(Optional.empty());

        String json = """
                {
                    "firstName": "Ana",
                    "lastName": "Torres",
                    "birthDate": "1988-12-01",
                    "phoneNumber": "51987654321",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1"
                }
                """;

        staffMvc.perform(put("/api/v1/staff-profiles/30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"));

        when(staffCmdService.handle(any(UpdateStaffProfileCommand.class))).thenReturn(40L);
        staffMvc.perform(put("/api/v1/staff-profiles/40")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("staff_profile_not_found"));
    }

    @Test
    void createPersonProfileWithDuplicateEmailReturns409() throws Exception {
        when(personCmdService.handle(any(CreatePersonProfileCommand.class)))
                .thenThrow(new EmailAddressAlreadyExistsException("juan@alivia.com"));

        String json = """
                {
                    "emailAddress": "juan@alivia.com",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1",
                    "phoneNumber": "51987654321",
                    "firstName": "Juan",
                    "lastName": "Perez",
                    "dni": "12345678",
                    "birthDate": "1990-05-10"
                }
                """;

        personMvc.perform(post("/api/v1/person-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_registered"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void updatePersonProfileWithRealServiceReturns404WhenMissing() throws Exception {
        var repository = mock(PersonProfileRepository.class);
        when(repository.findById(99L)).thenReturn(Optional.empty());
        var realMvc = MockMvcBuilders.standaloneSetup(
                        new PersonProfilesController(new PersonProfileCommandServiceImpl(repository), personQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String json = """
                {
                    "firstName": "Juan",
                    "lastName": "Perez",
                    "birthDate": "1990-05-10",
                    "phoneNumber": "51987654321",
                    "photoUrl": "http://img.jpg",
                    "photoPublicId": "img_1"
                }
                """;

        realMvc.perform(put("/api/v1/person-profiles/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("person_profile_not_found"));
    }
}
