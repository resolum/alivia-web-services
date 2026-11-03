package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdMembershipFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalHouseholdsService (Nursing ACL) Tests")
class ExternalHouseholdsServiceTest {

    private static final UserId USER = new UserId(10L);

    @Mock
    private HouseholdAccessFacade householdAccessFacade;
    @Mock
    private HouseholdMembershipFacade householdMembershipFacade;

    @InjectMocks
    private ExternalHouseholdsService service;

    @Test
    @DisplayName("A readable household returns its active members as user ids")
    void readableReturnsMembers() {
        when(householdMembershipFacade.fetchActiveMemberUserIds(1L)).thenReturn(Optional.of(Set.of(10L, 11L)));
        when(householdAccessFacade.canReadHousehold(1L, 10L)).thenReturn(true);

        assertThat(service.requireReadable(1L, USER)).containsExactlyInAnyOrder(new UserId(10L), new UserId(11L));
    }

    @Test
    @DisplayName("A missing household answers not found before asking for access")
    void missingHouseholdIsNotFound() {
        when(householdMembershipFacade.fetchActiveMemberUserIds(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireReadable(1L, USER))
                .isInstanceOf(CareNetworkHouseholdNotFoundException.class);
        assertThatThrownBy(() -> service.requireManageable(1L, USER))
                .isInstanceOf(CareNetworkHouseholdNotFoundException.class);
        verifyNoInteractions(householdAccessFacade);
    }

    @Test
    @DisplayName("Reading is denied when Profiles does not grant read access")
    void readDenied() {
        when(householdMembershipFacade.fetchActiveMemberUserIds(1L)).thenReturn(Optional.of(Set.of(11L)));
        when(householdAccessFacade.canReadHousehold(1L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireReadable(1L, USER))
                .isInstanceOf(CareNetworkAccessDeniedException.class);
    }

    @Test
    @DisplayName("Managing asks for manage access, not read access, and is denied without it")
    void manageDenied() {
        when(householdMembershipFacade.fetchActiveMemberUserIds(1L)).thenReturn(Optional.of(Set.of(10L)));
        when(householdAccessFacade.canManageHousehold(1L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireManageable(1L, USER))
                .isInstanceOf(CareNetworkAccessDeniedException.class);
        verify(householdAccessFacade).canManageHousehold(1L, 10L);
    }

    @Test
    @DisplayName("Assisted persons join as GUEST and caregivers as CAREGIVER")
    void addsMembersWithTheirResponsibility() {
        when(householdMembershipFacade.addMember(1L, 20L, "GUEST")).thenReturn(true);
        when(householdMembershipFacade.addMember(1L, 21L, "CAREGIVER")).thenReturn(false);

        assertThat(service.addAssistedPerson(1L, new UserId(20L))).isTrue();
        assertThat(service.addCaregiver(1L, new UserId(21L))).isFalse();
    }
}
