package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.application.acl.HouseholdMembershipFacadeImpl;
import com.resolum.alivia.profiles.application.internal.commandservices.HouseholdMemberCommandServiceImpl;
import com.resolum.alivia.profiles.application.internal.queryservices.HouseholdQueryServiceImpl;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdMemberAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.commands.AddHouseholdMemberCommand;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Household membership (command service and facade) Tests")
class HouseholdMembershipTest {

    private static final Address ADDRESS = new Address(
            "Av. El Derby 250, Santiago de Surco, Lima", -12.109722, -76.974444, "place-1");
    private static final PhoneNumber PHONE = new PhoneNumber("51987654321");
    private static final EmailAddress EMAIL = new EmailAddress("hogar@alivia.com");

    private HouseholdRepository householdRepository;
    private HouseholdMemberCommandServiceImpl commandService;
    private HouseholdMembershipFacadeImpl facade;

    @BeforeEach
    void setUp() {
        householdRepository = mock(HouseholdRepository.class);
        commandService = new HouseholdMemberCommandServiceImpl(householdRepository);
        facade = new HouseholdMembershipFacadeImpl(new HouseholdQueryServiceImpl(householdRepository), commandService);
        when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Command rejects a missing household, user or responsibility")
    void commandValidatesItsInput() {
        var user = new UserId(11L);
        assertThrows(IllegalArgumentException.class,
                () -> new AddHouseholdMemberCommand(0L, user, HouseholdResponsibility.GUEST));
        assertThrows(IllegalArgumentException.class,
                () -> new AddHouseholdMemberCommand(1L, null, HouseholdResponsibility.GUEST));
        assertThrows(IllegalArgumentException.class, () -> new AddHouseholdMemberCommand(1L, user, null));
    }

    @Test
    @DisplayName("Command service adds an active member and saves the household")
    void commandServiceAddsTheMember() {
        var household = household(10L);
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));

        var member = commandService.handle(
                new AddHouseholdMemberCommand(1L, new UserId(11L), HouseholdResponsibility.CAREGIVER));

        assertEquals(new UserId(11L), member.getUserId());
        assertEquals(HouseholdResponsibility.CAREGIVER, member.getResponsibility());
        assertTrue(member.isActive());
        verify(householdRepository).save(household);
    }

    @Test
    @DisplayName("Command service reports a missing household and a duplicated member without saving")
    void commandServiceRejectsMissingHouseholdAndDuplicates() {
        when(householdRepository.findById(2L)).thenReturn(Optional.empty());
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household(10L)));

        assertThrows(HouseholdNotFoundException.class, () -> commandService.handle(
                new AddHouseholdMemberCommand(2L, new UserId(11L), HouseholdResponsibility.GUEST)));
        assertThrows(HouseholdMemberAlreadyExistsException.class, () -> commandService.handle(
                new AddHouseholdMemberCommand(1L, new UserId(10L), HouseholdResponsibility.GUEST)));
        verify(householdRepository, never()).save(any());
    }

    @Test
    @DisplayName("Facade lists only the active members")
    void facadeListsActiveMembersOnly() {
        var household = household(10L);
        household.addMember(new UserId(11L), HouseholdResponsibility.CAREGIVER);
        household.addMember(new UserId(12L), HouseholdResponsibility.GUEST).revoke();
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));

        assertEquals(Optional.of(Set.of(10L, 11L)), facade.fetchActiveMemberUserIds(1L));
    }

    @Test
    @DisplayName("Facade answers empty for a household that does not exist")
    void facadeAnswersEmptyForMissingHousehold() {
        when(householdRepository.findById(2L)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), facade.fetchActiveMemberUserIds(2L));
    }

    @Test
    @DisplayName("Facade adds a member by responsibility name and answers false when it already belongs")
    void facadeAddsMemberAndReportsDuplicates() {
        var household = household(10L);
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));

        assertTrue(facade.addMember(1L, 11L, "GUEST"));
        assertFalse(facade.addMember(1L, 11L, "CAREGIVER"));
        assertEquals(HouseholdResponsibility.GUEST, household.findMember(new UserId(11L)).orElseThrow().getResponsibility());
    }

    @Test
    @DisplayName("Facade rejects unknown responsibilities and invalid identifiers; a missing household still fails")
    void facadeRejectsInvalidInput() {
        when(householdRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> facade.addMember(1L, 11L, "OWNER"));
        assertThrows(IllegalArgumentException.class, () -> facade.addMember(1L, null, "GUEST"));
        assertThrows(IllegalArgumentException.class, () -> facade.addMember(0L, 11L, "GUEST"));
        assertThrows(HouseholdNotFoundException.class, () -> facade.addMember(2L, 11L, "GUEST"));
    }

    private static Household household(long principalUserId) {
        var household = new Household("Casa", ADDRESS, PHONE, EMAIL);
        household.addMember(new UserId(principalUserId), HouseholdResponsibility.PRINCIPAL);
        return household;
    }
}
