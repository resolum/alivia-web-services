package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.valueobjects.*;
import com.resolum.alivia.profiles.infrastructure.adapters.ProfileRepositoryImpl;
import com.resolum.alivia.profiles.interfaces.rest.controllers.ProfilesController;
import com.resolum.alivia.profiles.interfaces.rest.resources.*;
import com.resolum.alivia.profiles.interfaces.rest.transform.*;
import com.resolum.alivia.shared.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProfileAssemblersTest {

    private void invokePrivateConstructor(Class<?> clazz) throws Exception {
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    void businessProfileResourceFromEntityAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(BusinessProfileResourceFromEntityAssembler.class);

        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var period = new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range);
        var hours = new BusinessHours(List.of(period));
        var address = new Address("Av. Arequipa 100", -12.07, -77.03, "place_1");

        var business = new BusinessProfile(
                new EmailAddress("corp@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new Ruc("20123456789"),
                new BusinessName("Alivia Lab"),
                new LegalName("Alivia Lab SAC"),
                "Lab desc", "Analisis clinicos",
                new PhoneNumber("51987654322"),
                hours, "Tu laboratorio de confianza",
                new EmailAddress("support@alivia.com"),
                address
        );

        var resource = BusinessProfileResourceFromEntityAssembler.toResourceFromEntity(business);

        assertEquals("corp@alivia.com", resource.emailAddress());
        assertEquals("http://img.jpg", resource.photoUrl());
        assertEquals("img_1", resource.photoPublicId());
        assertEquals("51987654321", resource.whatsappNum());
        assertEquals("20123456789", resource.ruc());
        assertEquals("Alivia Lab", resource.businessName());
        assertEquals("Alivia Lab SAC", resource.legalName());
        assertEquals("Lab desc", resource.description());
        assertEquals("Analisis clinicos", resource.mainActivity());
        assertEquals("51987654322", resource.businessPhone());
        assertEquals(hours, resource.businessHours());
        assertEquals("Tu laboratorio de confianza", resource.slogan());
        assertEquals("support@alivia.com", resource.supportEmail());
        assertEquals(address, resource.address());

        // Test with null fields
        business.setEmailAddress(null);
        business.setPhoto(null);
        business.setPhoneNumber(null);
        business.setRuc(null);
        business.setBusinessName(null);
        business.setLegalName(null);
        business.setBusinessPhone(null);
        business.setSupportEmail(null);

        var nullResource = BusinessProfileResourceFromEntityAssembler.toResourceFromEntity(business);
        assertNull(nullResource.emailAddress());
        assertNull(nullResource.photoUrl());
        assertNull(nullResource.photoPublicId());
        assertNull(nullResource.whatsappNum());
        assertNull(nullResource.ruc());
        assertNull(nullResource.businessName());
        assertNull(nullResource.legalName());
        assertNull(nullResource.businessPhone());
        assertNull(nullResource.supportEmail());
    }

    @Test
    void createBusinessProfileCommandFromResourceAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(CreateBusinessProfileCommandFromResourceAssembler.class);

        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var hours = new BusinessHours(List.of(new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range)));

        var resource = new CreateBusinessProfileResource(
                "corp@alivia.com", "http://img.jpg", "img_1", "+51999888777",
                "20123456789", "Alivia Lab", "Alivia Lab SAC", "Lab desc",
                "Analisis", "+51911112222", hours, "Slogan", "support@alivia.com",
                "Av. Arequipa 100", -12.07, -77.03, "place_1"
        );

        var command = CreateBusinessProfileCommandFromResourceAssembler.toCommandFromResource(resource);
        assertEquals("corp@alivia.com", command.emailAddress());
        assertEquals("Alivia Lab", command.businessName());
    }

    @Test
    void updateBusinessProfileCommandFromResourceAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(UpdateBusinessProfileCommandFromResourceAssembler.class);

        var range = new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0));
        var hours = new BusinessHours(List.of(new WeeklyPeriod(Set.of(DayOfWeek.MONDAY), range)));

        var resource = new UpdateBusinessProfileResource(
                "corp@alivia.com", "http://img.jpg", "img_1", "+51999888777",
                "20123456789", "Alivia Lab", "Alivia Lab SAC", "Lab desc",
                "Analisis", "+51911112222", hours, "Slogan", "support@alivia.com",
                "Av. Arequipa 100", -12.07, -77.03, "place_1"
        );

        var command = UpdateBusinessProfileCommandFromResourceAssembler.toCommandFromResource(10L, resource);
        assertEquals(10L, command.profileId());
        assertEquals("Alivia Lab", command.businessName());
    }

    @Test
    void personProfileResourceFromEntityAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(PersonProfileResourceFromEntityAssembler.class);

        var person = new PersonProfile(
                new EmailAddress("juan@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Juan", "Perez"),
                new Dni("12345678"),
                new BirthDate(LocalDate.of(1990, 5, 10))
        );

        var resource = PersonProfileResourceFromEntityAssembler.toResourceFromEntity(person);
        assertEquals("juan@alivia.com", resource.emailAddress());
        assertEquals("Juan", resource.firstName());
        assertEquals("Perez", resource.lastName());
        assertEquals("12345678", resource.dni());
        assertEquals(LocalDate.of(1990, 5, 10), resource.birthDate());
        assertEquals("51987654321", resource.phoneNumber());

        // Null fields branch coverage
        person.setEmailAddress(null);
        person.setPhoto(null);
        person.setPhoneNumber(null);
        person.setPersonName(null);
        person.setDni(null);
        person.setBirthDate(null);

        var nullResource = PersonProfileResourceFromEntityAssembler.toResourceFromEntity(person);
        assertNull(nullResource.emailAddress());
        assertNull(nullResource.photoUrl());
        assertNull(nullResource.photoPublicId());
        assertNull(nullResource.phoneNumber());
        assertNull(nullResource.firstName());
        assertNull(nullResource.lastName());
        assertNull(nullResource.dni());
        assertNull(nullResource.birthDate());
    }

    @Test
    void createPersonProfileCommandFromResourceAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(CreatePersonProfileCommandFromResourceAssembler.class);

        var resource = new CreatePersonProfileResource(
                "juan@alivia.com", "http://img.jpg", "img_1", "51987654321",
                "Juan", "Perez", "12345678", LocalDate.of(1990, 5, 10)
        );

        var command = CreatePersonProfileCommandFromResourceAssembler.toCommandFromResource(resource);
        assertEquals("juan@alivia.com", command.emailAddress());
        assertEquals("Juan", command.firstName());
    }

    @Test
    void updatePersonProfileCommandFromResourceAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(UpdatePersonProfileCommandFromResourceAssembler.class);

        var resource = new UpdatePersonProfileResource(
                "Juan", "Perez", LocalDate.of(1990, 5, 10), "51987654321",
                "http://img.jpg", "img_1"
        );

        var command = UpdatePersonProfileCommandFromResourceAssembler.toCommandFromResource(5L, resource);
        assertEquals(5L, command.profileId());
        assertEquals("Juan", command.firstName());
    }

    @Test
    void staffProfileResourceFromEntityAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(StaffProfileResourceFromEntityAssembler.class);

        var staff = new StaffProfile(
                new EmailAddress("ana@alivia.com"),
                new Photo("http://img.jpg", "img_1"),
                new PhoneNumber("51987654321"),
                new PersonName("Ana", "Torres"),
                new Dni("87654321"),
                new BirthDate(LocalDate.of(1988, 12, 1))
        );

        var resource = StaffProfileResourceFromEntityAssembler.toResourceFromEntity(staff);
        assertEquals("ana@alivia.com", resource.emailAddress());
        assertEquals("Ana", resource.firstName());
        assertEquals("Torres", resource.lastName());
        assertEquals("87654321", resource.dni());
        assertEquals(LocalDate.of(1988, 12, 1), resource.birthDate());
        assertEquals("51987654321", resource.phoneNumber());

        // Null fields branch coverage
        staff.setEmailAddress(null);
        staff.setPhoto(null);
        staff.setPhoneNumber(null);
        staff.setPersonName(null);
        staff.setDni(null);
        staff.setBirthDate(null);

        var nullResource = StaffProfileResourceFromEntityAssembler.toResourceFromEntity(staff);
        assertNull(nullResource.emailAddress());
        assertNull(nullResource.photoUrl());
        assertNull(nullResource.photoPublicId());
        assertNull(nullResource.phoneNumber());
        assertNull(nullResource.firstName());
        assertNull(nullResource.lastName());
        assertNull(nullResource.dni());
        assertNull(nullResource.birthDate());
    }

    @Test
    void createStaffProfileCommandFromResourceAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(CreateStaffProfileCommandFromResourceAssembler.class);

        var resource = new CreateStaffProfileResource(
                "ana@alivia.com", "http://img.jpg", "img_1", "51987654321",
                "Ana", "Torres", "87654321", LocalDate.of(1988, 12, 1)
        );

        var command = CreateStaffProfileCommandFromResourceAssembler.toCommandFromResource(resource);
        assertEquals("ana@alivia.com", command.emailAddress());
        assertEquals("Ana", command.firstName());
    }

    @Test
    void updateStaffProfileCommandFromResourceAssemblerMapsFields() throws Exception {
        invokePrivateConstructor(UpdateStaffProfileCommandFromResourceAssembler.class);

        var resource = new UpdateStaffProfileResource(
                "Ana", "Torres", LocalDate.of(1988, 12, 1), "51987654321",
                "http://img.jpg", "img_1"
        );

        var command = UpdateStaffProfileCommandFromResourceAssembler.toCommandFromResource(7L, resource);
        assertEquals(7L, command.profileId());
        assertEquals("Ana", command.firstName());
    }

    @Test
    void emptyClassesInstantiateWithoutErrors() {
        assertNotNull(new ProfilesController());
        assertNotNull(new ProfileRepositoryImpl());
    }
}
