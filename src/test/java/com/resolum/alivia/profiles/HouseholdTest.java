package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdMemberAlreadyExistsException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdMemberStatus;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdTest {

    private static final Address ADDRESS = new Address(
            "Av. El Derby 250, Santiago de Surco, Lima", -12.109722, -76.974444, "place-1");
    private static final PhoneNumber PHONE = new PhoneNumber("51987654321");
    private static final EmailAddress EMAIL = new EmailAddress("hogar@alivia.com");

    @Test
    void createsHouseholdWithRequiredData() {
        var household = new Household("  Casa  ", ADDRESS, PHONE, EMAIL);

        assertEquals("Casa", household.getAlias());
        assertEquals(ADDRESS, household.getAddress());
        assertEquals(PHONE, household.getContactPhone());
        assertEquals(EMAIL, household.getContactEmail());
        assertTrue(household.getMembers().isEmpty());
    }

    @Test
    void rejectsMissingRequiredData() {
        assertThrows(IllegalArgumentException.class, () -> new Household(null, ADDRESS, PHONE, EMAIL));
        assertThrows(IllegalArgumentException.class, () -> new Household("   ", ADDRESS, PHONE, EMAIL));
        assertThrows(IllegalArgumentException.class, () -> new Household("Casa", null, PHONE, EMAIL));
        assertThrows(IllegalArgumentException.class, () -> new Household("Casa", ADDRESS, null, EMAIL));
        assertThrows(IllegalArgumentException.class, () -> new Household("Casa", ADDRESS, PHONE, null));
    }

    @Test
    void rejectsAliasLongerThanTheColumn() {
        assertThrows(IllegalArgumentException.class,
                () -> new Household("a".repeat(121), ADDRESS, PHONE, EMAIL));
    }

    @Test
    void updatesGeneralInformationAndLocation() {
        var household = new Household("Casa", ADDRESS, PHONE, EMAIL);
        var newPhone = new PhoneNumber("51987654322");
        var newEmail = new EmailAddress("nuevo@alivia.com");
        var newAddress = new Address("Av. Benavides 456, Miraflores, Lima", -12.12, -77.01, "place-2");

        household.updateGeneralInformation("Casa de playa", newPhone, newEmail);
        household.updateLocation(newAddress);

        assertEquals("Casa de playa", household.getAlias());
        assertEquals(newPhone, household.getContactPhone());
        assertEquals(newEmail, household.getContactEmail());
        assertEquals(newAddress, household.getAddress());
    }

    @Test
    void addsMembersAndRejectsDuplicates() {
        var household = new Household("Casa", ADDRESS, PHONE, EMAIL);
        var userId = new UserId(1L);

        var member = household.addMember(userId, HouseholdResponsibility.PRINCIPAL);

        assertEquals(HouseholdMemberStatus.ACTIVE, member.getStatus());
        assertEquals(userId, member.getUserId());
        assertEquals(1, household.getMembers().size());
        assertTrue(household.findMember(userId).isPresent());

        assertThrows(HouseholdMemberAlreadyExistsException.class,
                () -> household.addMember(userId, HouseholdResponsibility.CAREGIVER));
        assertEquals(1, household.getMembers().size());
    }

    @Test
    void findMemberReturnsEmptyForUnknownUser() {
        var household = new Household("Casa", ADDRESS, PHONE, EMAIL);

        assertFalse(household.findMember(new UserId(99L)).isPresent());
    }

    @Test
    void memberListIsReadOnly() {
        var household = new Household("Casa", ADDRESS, PHONE, EMAIL);
        household.addMember(new UserId(1L), HouseholdResponsibility.PRINCIPAL);

        assertThrows(UnsupportedOperationException.class, () -> household.getMembers().clear());
    }
}
