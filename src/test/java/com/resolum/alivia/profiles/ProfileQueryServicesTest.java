package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.application.internal.queryservices.BusinessProfileQueryServiceImpl;
import com.resolum.alivia.profiles.application.internal.queryservices.PersonProfileQueryServiceImpl;
import com.resolum.alivia.profiles.application.internal.queryservices.StaffProfileQueryServiceImpl;
import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.queries.*;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.BusinessProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileQueryServicesTest {

    private BusinessProfileRepository businessRepo;
    private PersonProfileRepository personRepo;
    private StaffProfileRepository staffRepo;

    private BusinessProfileQueryServiceImpl businessService;
    private PersonProfileQueryServiceImpl personService;
    private StaffProfileQueryServiceImpl staffService;

    @BeforeEach
    void setUp() {
        businessRepo = mock(BusinessProfileRepository.class);
        personRepo = mock(PersonProfileRepository.class);
        staffRepo = mock(StaffProfileRepository.class);

        businessService = new BusinessProfileQueryServiceImpl(businessRepo);
        personService = new PersonProfileQueryServiceImpl(personRepo);
        staffService = new StaffProfileQueryServiceImpl(staffRepo);
    }

    // --- BusinessProfileQueryService ---

    @Test
    void businessProfileQueryServiceFindsByIdAndChecksExistence() {
        var mockProfile = mock(BusinessProfile.class);
        when(businessRepo.findById(1L)).thenReturn(Optional.of(mockProfile));
        when(businessRepo.findById(2L)).thenReturn(Optional.empty());

        assertEquals(Optional.of(mockProfile), businessService.handle(new GetBusinessProfileByIdQuery(1L)));
        assertEquals(Optional.empty(), businessService.handle(new GetBusinessProfileByIdQuery(2L)));

        when(businessRepo.existsByEmailAddress(any())).thenReturn(true);
        assertTrue(businessService.handle(new ExistsBusinessProfileByEmailAddressQuery("corp@alivia.com")));

        when(businessRepo.existsByEmailAddress(any())).thenReturn(false);
        assertFalse(businessService.handle(new ExistsBusinessProfileByEmailAddressQuery("nonexistent@alivia.com")));
    }

    // --- PersonProfileQueryService ---

    @Test
    void personProfileQueryServiceFindsByIdAndChecksExistence() {
        var mockProfile = mock(PersonProfile.class);
        when(personRepo.findById(10L)).thenReturn(Optional.of(mockProfile));
        when(personRepo.findById(20L)).thenReturn(Optional.empty());

        assertEquals(Optional.of(mockProfile), personService.handle(new GetPersonProfileByIdQuery(10L)));
        assertEquals(Optional.empty(), personService.handle(new GetPersonProfileByIdQuery(20L)));

        when(personRepo.existsByEmailAddress(any())).thenReturn(true);
        assertTrue(personService.handle(new ExistsPersonProfileByEmailAddressQuery("person@alivia.com")));

        when(personRepo.existsByDni(any())).thenReturn(true);
        assertTrue(personService.handle(new ExistsPersonProfileByDniQuery("12345678")));

        when(personRepo.existsByDni(any())).thenReturn(false);
        assertFalse(personService.handle(new ExistsPersonProfileByDniQuery("87654321")));
    }

    // --- StaffProfileQueryService ---

    @Test
    void staffProfileQueryServiceFindsByIdAndChecksExistence() {
        var mockProfile = mock(StaffProfile.class);
        when(staffRepo.findById(30L)).thenReturn(Optional.of(mockProfile));
        when(staffRepo.findById(40L)).thenReturn(Optional.empty());

        assertEquals(Optional.of(mockProfile), staffService.handle(new GetStaffProfileByIdQuery(30L)));
        assertEquals(Optional.empty(), staffService.handle(new GetStaffProfileByIdQuery(40L)));

        when(staffRepo.existsByEmailAddress(any())).thenReturn(true);
        assertTrue(staffService.handle(new ExistsStaffProfileByEmailAddressQuery("staff@alivia.com")));

        when(staffRepo.existsByDni(any())).thenReturn(true);
        assertTrue(staffService.handle(new ExistsStaffProfileByDniQuery("87654321")));

        when(staffRepo.existsByDni(any())).thenReturn(false);
        assertFalse(staffService.handle(new ExistsStaffProfileByDniQuery("12345678")));
    }
}
