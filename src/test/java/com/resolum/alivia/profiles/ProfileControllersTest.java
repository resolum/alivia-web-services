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
import org.springframework.mock.web.MockMultipartFile;
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
        var conversionService = new org.springframework.format.support.DefaultFormattingConversionService();
        conversionService.addConverter(new com.resolum.alivia.profiles.interfaces.rest.transform.StringToBusinessHoursConverter());
        businessMvc = MockMvcBuilders.standaloneSetup(
                new BusinessProfilesController(businessCmdService, businessQueryService)
        ).setConversionService(conversionService)
        .setControllerAdvice(new GlobalExceptionHandler()).build();

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

        var file = new MockMultipartFile("photo", "logo.png", "image/png", new byte[]{1, 2, 3});

        businessMvc.perform(multipart("/api/v1/business-profiles")
                        .file(file)
                        .param("emailAddress", "corp@alivia.com")
                        .param("whatsappNum", "51987654321")
                        .param("ruc", "20123456789")
                        .param("businessName", "Alivia Lab")
                        .param("legalName", "Alivia Lab SAC")
                        .param("description", "Desc")
                        .param("mainActivity", "Salud")
                        .param("emergencyPhone", "51987654322")
                        .param("slogan", "Slogan")
                        .param("supportEmail", "support@alivia.com")
                        .param("streetAddress", "Av. Brasil 123")
                        .param("latitude", "-12.05")
                        .param("longitude", "-77.04")
                        .param("placeId", "place_1")
                        .param("businessHours", "{\"periods\":[{\"days\":[\"MONDAY\"],\"range\":{\"start\":\"08:00\",\"end\":\"18:00\"}}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailAddress").value("corp@alivia.com"))
                .andExpect(jsonPath("$.businessName").value("Alivia Lab"));
    }

    @Test
    void createBusinessProfileReturns500WhenNotFoundAfterCreation() throws Exception {
        when(businessCmdService.handle(any(CreateBusinessProfileCommand.class))).thenReturn(1L);
        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(1L))).thenReturn(Optional.empty());

        var file = new MockMultipartFile("photo", "logo.png", "image/png", new byte[]{1, 2, 3});

        businessMvc.perform(multipart("/api/v1/business-profiles")
                        .file(file)
                        .param("emailAddress", "corp@alivia.com")
                        .param("whatsappNum", "51987654321")
                        .param("ruc", "20123456789")
                        .param("businessName", "Alivia Lab")
                        .param("legalName", "Alivia Lab SAC")
                        .param("description", "Desc")
                        .param("mainActivity", "Salud")
                        .param("emergencyPhone", "51987654322")
                        .param("slogan", "Slogan")
                        .param("supportEmail", "support@alivia.com")
                        .param("streetAddress", "Av. Brasil 123")
                        .param("latitude", "-12.05")
                        .param("longitude", "-77.04")
                        .param("placeId", "place_1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"));
    }

    @Test
    void createBusinessProfileRejectsJsonWithUnsupportedMediaType() throws Exception {
        businessMvc.perform(post("/api/v1/business-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
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

        var file = new MockMultipartFile("photo", "logo_up.png", "image/png", new byte[]{1, 2, 3});

        businessMvc.perform(multipart("/api/v1/business-profiles/1")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("emailAddress", "corp@alivia.com")
                        .param("whatsappNum", "51987654321")
                        .param("ruc", "20123456789")
                        .param("businessName", "Alivia Lab")
                        .param("legalName", "Alivia Lab SAC")
                        .param("description", "Desc")
                        .param("mainActivity", "Salud")
                        .param("emergencyPhone", "51987654322")
                        .param("slogan", "Slogan")
                        .param("supportEmail", "support@alivia.com")
                        .param("streetAddress", "Av. Brasil 123")
                        .param("latitude", "-12.05")
                        .param("longitude", "-77.04")
                        .param("placeId", "place_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Alivia Lab"));

        // If after update query returns empty
        when(businessCmdService.handle(any(UpdateBusinessProfileCommand.class))).thenReturn(2L);
        businessMvc.perform(multipart("/api/v1/business-profiles/2")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("emailAddress", "corp@alivia.com")
                        .param("whatsappNum", "51987654321")
                        .param("ruc", "20123456789")
                        .param("businessName", "Alivia Lab")
                        .param("legalName", "Alivia Lab SAC")
                        .param("description", "Desc")
                        .param("mainActivity", "Salud")
                        .param("emergencyPhone", "51987654322")
                        .param("slogan", "Slogan")
                        .param("supportEmail", "support@alivia.com")
                        .param("streetAddress", "Av. Brasil 123")
                        .param("latitude", "-12.05")
                        .param("longitude", "-77.04")
                        .param("placeId", "place_1"))
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

        var file = new MockMultipartFile("photo", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        personMvc.perform(multipart("/api/v1/person-profiles")
                        .file(file)
                        .param("emailAddress", "juan@alivia.com")
                        .param("phoneNumber", "51987654321")
                        .param("firstName", "Juan")
                        .param("lastName", "Perez")
                        .param("dni", "12345678")
                        .param("birthDate", "1990-05-10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Juan"));

        // Server-side inconsistency when the created profile cannot be read back
        when(personCmdService.handle(any(CreatePersonProfileCommand.class))).thenReturn(99L);
        when(personQueryService.handle(new GetPersonProfileByIdQuery(99L))).thenReturn(Optional.empty());

        personMvc.perform(multipart("/api/v1/person-profiles")
                        .file(file)
                        .param("emailAddress", "juan@alivia.com")
                        .param("phoneNumber", "51987654321")
                        .param("firstName", "Juan")
                        .param("lastName", "Perez")
                        .param("dni", "12345678")
                        .param("birthDate", "1990-05-10"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"));
    }

    @Test
    void createPersonProfileRejectsJsonWithUnsupportedMediaType() throws Exception {
        personMvc.perform(post("/api/v1/person-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
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

        var file = new MockMultipartFile("photo", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        personMvc.perform(multipart("/api/v1/person-profiles/10")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("firstName", "Juan")
                        .param("lastName", "Perez")
                        .param("birthDate", "1990-05-10")
                        .param("phoneNumber", "51987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Juan"));

        when(personCmdService.handle(any(UpdatePersonProfileCommand.class))).thenReturn(20L);
        personMvc.perform(multipart("/api/v1/person-profiles/20")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("firstName", "Juan")
                        .param("lastName", "Perez")
                        .param("birthDate", "1990-05-10")
                        .param("phoneNumber", "51987654321"))
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

        var file = new MockMultipartFile("photo", "nurse.jpg", "image/jpeg", new byte[]{1, 2, 3});

        staffMvc.perform(multipart("/api/v1/staff-profiles")
                        .file(file)
                        .param("emailAddress", "ana@alivia.com")
                        .param("phoneNumber", "51987654321")
                        .param("firstName", "Ana")
                        .param("lastName", "Torres")
                        .param("dni", "87654321")
                        .param("birthDate", "1988-12-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ana"));

        when(staffCmdService.handle(any(CreateStaffProfileCommand.class))).thenReturn(99L);
        when(staffQueryService.handle(new GetStaffProfileByIdQuery(99L))).thenReturn(Optional.empty());

        staffMvc.perform(multipart("/api/v1/staff-profiles")
                        .file(file)
                        .param("emailAddress", "ana@alivia.com")
                        .param("phoneNumber", "51987654321")
                        .param("firstName", "Ana")
                        .param("lastName", "Torres")
                        .param("dni", "87654321")
                        .param("birthDate", "1988-12-01"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"));
    }

    @Test
    void createStaffProfileRejectsJsonWithUnsupportedMediaType() throws Exception {
        staffMvc.perform(post("/api/v1/staff-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
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

        var file = new MockMultipartFile("photo", "nurse.jpg", "image/jpeg", new byte[]{1, 2, 3});

        staffMvc.perform(multipart("/api/v1/staff-profiles/30")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("firstName", "Ana")
                        .param("lastName", "Torres")
                        .param("birthDate", "1988-12-01")
                        .param("phoneNumber", "51987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"));

        when(staffCmdService.handle(any(UpdateStaffProfileCommand.class))).thenReturn(40L);
        staffMvc.perform(multipart("/api/v1/staff-profiles/40")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("firstName", "Ana")
                        .param("lastName", "Torres")
                        .param("birthDate", "1988-12-01")
                        .param("phoneNumber", "51987654321"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("staff_profile_not_found"));
    }

    @Test
    void createPersonProfileWithDuplicateEmailReturns409() throws Exception {
        when(personCmdService.handle(any(CreatePersonProfileCommand.class)))
                .thenThrow(new EmailAddressAlreadyExistsException("juan@alivia.com"));

        var file = new MockMultipartFile("photo", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        personMvc.perform(multipart("/api/v1/person-profiles")
                        .file(file)
                        .param("emailAddress", "juan@alivia.com")
                        .param("phoneNumber", "51987654321")
                        .param("firstName", "Juan")
                        .param("lastName", "Perez")
                        .param("dni", "12345678")
                        .param("birthDate", "1990-05-10"))
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

        var file = new MockMultipartFile("photo", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        realMvc.perform(multipart("/api/v1/person-profiles/99")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("firstName", "Juan")
                        .param("lastName", "Perez")
                        .param("birthDate", "1990-05-10")
                        .param("phoneNumber", "51987654321"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("person_profile_not_found"));
    }

    @Test
    void createPersonProfileWithMultipartFileReturns201() throws Exception {
        var person = new PersonProfile(
                new EmailAddress("juan.mp@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Juan", "Perez"),
                new Dni("12345678"),
                new BirthDate(LocalDate.of(1990, 5, 10))
        );

        when(personCmdService.handle(any(CreatePersonProfileCommand.class))).thenReturn(11L);
        when(personQueryService.handle(new GetPersonProfileByIdQuery(11L))).thenReturn(Optional.of(person));

        var file = new MockMultipartFile("photo", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        personMvc.perform(multipart("/api/v1/person-profiles")
                        .file(file)
                        .param("emailAddress", "juan.mp@alivia.com")
                        .param("phoneNumber", "51987654321")
                        .param("firstName", "Juan")
                        .param("lastName", "Perez")
                        .param("dni", "12345678")
                        .param("birthDate", "1990-05-10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Juan"));
    }

    @Test
    void updatePersonProfileWithMultipartFileReturns200() throws Exception {
        var person = new PersonProfile(
                new EmailAddress("juan.mp@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Juan", "Perez"),
                new Dni("12345678"),
                new BirthDate(LocalDate.of(1990, 5, 10))
        );

        when(personCmdService.handle(any(UpdatePersonProfileCommand.class))).thenReturn(11L);
        when(personQueryService.handle(new GetPersonProfileByIdQuery(11L))).thenReturn(Optional.of(person));

        var file = new MockMultipartFile("photo", "avatar_update.jpg", "image/jpeg", new byte[]{4, 5, 6});

        personMvc.perform(multipart("/api/v1/person-profiles/11")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("firstName", "Juan")
                        .param("lastName", "Perez")
                        .param("phoneNumber", "51987654321")
                        .param("birthDate", "1990-05-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Juan"));
    }

    @Test
    void createStaffProfileWithMultipartFileReturns201() throws Exception {
        var staff = new StaffProfile(
                new EmailAddress("ana.mp@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Ana", "Torres"),
                new Dni("87654321"),
                new BirthDate(LocalDate.of(1988, 12, 1))
        );

        when(staffCmdService.handle(any(CreateStaffProfileCommand.class))).thenReturn(31L);
        when(staffQueryService.handle(new GetStaffProfileByIdQuery(31L))).thenReturn(Optional.of(staff));

        var file = new MockMultipartFile("photo", "nurse.jpg", "image/jpeg", new byte[]{1, 2, 3});

        staffMvc.perform(multipart("/api/v1/staff-profiles")
                        .file(file)
                        .param("emailAddress", "ana.mp@alivia.com")
                        .param("phoneNumber", "51987654321")
                        .param("firstName", "Ana")
                        .param("lastName", "Torres")
                        .param("dni", "87654321")
                        .param("birthDate", "1988-12-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ana"));
    }

    @Test
    void updateStaffProfileWithMultipartFileReturns200() throws Exception {
        var staff = new StaffProfile(
                new EmailAddress("ana.mp@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Ana", "Torres"),
                new Dni("87654321"),
                new BirthDate(LocalDate.of(1988, 12, 1))
        );

        when(staffCmdService.handle(any(UpdateStaffProfileCommand.class))).thenReturn(31L);
        when(staffQueryService.handle(new GetStaffProfileByIdQuery(31L))).thenReturn(Optional.of(staff));

        var file = new MockMultipartFile("photo", "nurse_update.jpg", "image/jpeg", new byte[]{4, 5, 6});

        staffMvc.perform(multipart("/api/v1/staff-profiles/31")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("firstName", "Ana")
                        .param("lastName", "Torres")
                        .param("phoneNumber", "51987654321")
                        .param("birthDate", "1988-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"));
    }

    @Test
    void createBusinessProfileWithMultipartFileReturns201() throws Exception {
        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var hours = new BusinessHours(List.of(new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range)));
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");

        var profile = new BusinessProfile(
                new EmailAddress("corp.mp@alivia.com"),
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

        when(businessCmdService.handle(any(CreateBusinessProfileCommand.class))).thenReturn(15L);
        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(15L))).thenReturn(Optional.of(profile));

        var file = new MockMultipartFile("photo", "clinic_logo.png", "image/png", new byte[]{1, 2, 3});

        businessMvc.perform(multipart("/api/v1/business-profiles")
                        .file(file)
                        .param("emailAddress", "corp.mp@alivia.com")
                        .param("whatsappNum", "51987654321")
                        .param("ruc", "20123456789")
                        .param("businessName", "Alivia Lab")
                        .param("legalName", "Alivia Lab SAC")
                        .param("description", "Desc")
                        .param("mainActivity", "Salud")
                        .param("emergencyPhone", "51987654322")
                        .param("slogan", "Slogan")
                        .param("supportEmail", "support@alivia.com")
                        .param("streetAddress", "Av. Brasil 123")
                        .param("latitude", "-12.05")
                        .param("longitude", "-77.04")
                        .param("placeId", "place_1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessName").value("Alivia Lab"));
    }

    @Test
    void updateBusinessProfileWithMultipartFileReturns200() throws Exception {
        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var hours = new BusinessHours(List.of(new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range)));
        var address = new Address("Av. Brasil 123", -12.05, -77.04, "place_1");

        var profile = new BusinessProfile(
                new EmailAddress("corp.mp@alivia.com"),
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

        when(businessCmdService.handle(any(UpdateBusinessProfileCommand.class))).thenReturn(15L);
        when(businessQueryService.handle(new GetBusinessProfileByIdQuery(15L))).thenReturn(Optional.of(profile));

        var file = new MockMultipartFile("photo", "clinic_logo_v2.png", "image/png", new byte[]{4, 5, 6});

        businessMvc.perform(multipart("/api/v1/business-profiles/15")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("emailAddress", "corp.mp@alivia.com")
                        .param("whatsappNum", "51987654321")
                        .param("ruc", "20123456789")
                        .param("businessName", "Alivia Lab")
                        .param("legalName", "Alivia Lab SAC")
                        .param("description", "Desc")
                        .param("mainActivity", "Salud")
                        .param("emergencyPhone", "51987654322")
                        .param("slogan", "Slogan")
                        .param("supportEmail", "support@alivia.com")
                        .param("streetAddress", "Av. Brasil 123")
                        .param("latitude", "-12.05")
                        .param("longitude", "-77.04")
                        .param("placeId", "place_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Alivia Lab"));
    }
}
