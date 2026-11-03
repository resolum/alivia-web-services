package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
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

class HouseholdMemberTest {

    private static final Address ADDRESS = new Address(
            "Av. El Derby 250, Santiago de Surco, Lima", -12.109722, -76.974444, "place-1");
    private static final PhoneNumber PHONE = new PhoneNumber("51987654321");
    private static final EmailAddress EMAIL = new EmailAddress("hogar@alivia.com");

    @Test
    void newMemberStartsActive() {
        var household = household();
        var member = household.addMember(new UserId(1L), HouseholdResponsibility.CAREGIVER);

        assertEquals(HouseholdMemberStatus.ACTIVE, member.getStatus());
        assertTrue(member.isActive());
        assertEquals(new UserId(1L), member.getUserId());
        assertEquals(HouseholdResponsibility.CAREGIVER, member.getResponsibility());
    }

    @Test
    void revokesAndReactivatesTheMembership() {
        var member = household().addMember(new UserId(1L), HouseholdResponsibility.CAREGIVER);

        member.revoke();
        assertEquals(HouseholdMemberStatus.REVOKED, member.getStatus());
        assertFalse(member.isActive());

        member.reactivate();
        assertEquals(HouseholdMemberStatus.ACTIVE, member.getStatus());
        assertTrue(member.isActive());
    }

    @Test
    void rejectsInvalidStateTransitions() {
        var member = household().addMember(new UserId(1L), HouseholdResponsibility.CAREGIVER);

        assertThrows(IllegalStateException.class, member::reactivate);

        member.revoke();

        assertThrows(IllegalStateException.class, member::revoke);
    }

    @Test
    void rejectsMissingMemberData() {
        var household = household();
        var userId = new UserId(1L);

        assertThrows(IllegalArgumentException.class,
                () -> household.addMember(null, HouseholdResponsibility.PRINCIPAL));
        assertThrows(IllegalArgumentException.class,
                () -> household.addMember(userId, null));
        assertThrows(IllegalArgumentException.class,
                () -> new HouseholdMember(null, userId, HouseholdResponsibility.PRINCIPAL));
    }

    private Household household() {
        return new Household("Casa", ADDRESS, PHONE, EMAIL);
    }
}
