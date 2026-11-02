package com.resolum.alivia.profiles;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.resolum.alivia.profiles.application.internal.queryservices.HouseholdQueryServiceImpl;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HouseholdQueryServiceTest {

    private static final Address ADDRESS = new Address(
            "Av. El Derby 250, Santiago de Surco, Lima", -12.109722, -76.974444, "place-1");
    private static final PhoneNumber PHONE = new PhoneNumber("51987654321");
    private static final EmailAddress EMAIL = new EmailAddress("hogar@alivia.com");

    private HouseholdRepository householdRepository;
    private HouseholdQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        householdRepository = mock(HouseholdRepository.class);
        service = new HouseholdQueryServiceImpl(householdRepository);
    }

    @Test
    void returnsHouseholdForActiveMember() {
        var household = household();
        household.addMember(new UserId(10L), HouseholdResponsibility.PRINCIPAL);
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));

        var result = service.handle(new GetHouseholdQuery(1L, 10L));

        assertEquals("Casa", result.getAlias());
        assertEquals(ADDRESS, result.getAddress());
    }

    @Test
    void returnsMembersForActiveMember() {
        var household = household();
        household.addMember(new UserId(10L), HouseholdResponsibility.PRINCIPAL);
        household.addMember(new UserId(11L), HouseholdResponsibility.CAREGIVER);
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));

        var result = service.handle(new GetHouseholdMembersQuery(1L, 10L));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(member -> member.getUserId().userId().equals(11L)));
    }

    @Test
    void rejectsMissingHousehold() {
        when(householdRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(HouseholdNotFoundException.class,
                () -> service.handle(new GetHouseholdQuery(99L, 10L)));
    }

    @Test
    void rejectsNonMember() {
        var household = household();
        household.addMember(new UserId(11L), HouseholdResponsibility.PRINCIPAL);
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));

        assertThrows(HouseholdAccessDeniedException.class,
                () -> service.handle(new GetHouseholdQuery(1L, 10L)));
    }

    @Test
    void rejectsRevokedMember() {
        var household = household();
        var member = household.addMember(new UserId(10L), HouseholdResponsibility.CAREGIVER);
        member.revoke();
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));

        assertThrows(HouseholdAccessDeniedException.class,
                () -> service.handle(new GetHouseholdMembersQuery(1L, 10L)));
    }

    private static Household household() {
        return new Household("Casa", ADDRESS, PHONE, EMAIL);
    }
}
