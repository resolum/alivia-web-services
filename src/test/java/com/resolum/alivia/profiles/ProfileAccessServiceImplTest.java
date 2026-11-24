package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.application.internal.outboundservices.acl.ExternalAssistanceService;
import com.resolum.alivia.profiles.application.internal.queryservices.ProfileAccessServiceImpl;
import com.resolum.alivia.profiles.domain.exceptions.ProfileAccessDeniedException;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileAccessServiceImplTest {

    private PersonProfileRepository people;
    private StaffProfileRepository staff;
    private ExternalAssistanceService assistance;
    private ProfileAccessServiceImpl access;

    @BeforeEach
    void setUp() {
        people = mock(PersonProfileRepository.class);
        staff = mock(StaffProfileRepository.class);
        assistance = mock(ExternalAssistanceService.class);
        access = new ProfileAccessServiceImpl(people, staff, assistance);
    }

    private static PersonProfile person(long id) {
        var profile = mock(PersonProfile.class);
        when(profile.getId()).thenReturn(id);
        return profile;
    }

    @Test
    void anAdministratorMayDoEverythingWithoutAnyLookup() {
        assertThatCode(() -> {
            access.assertCanReadPersonProfile(7L, 1L, true);
            access.assertCanChangePersonProfile(7L, 1L, true);
            access.assertCanAccessStaffProfile(7L, 1L, true);
        }).doesNotThrowAnyException();
        verifyNoInteractions(people, staff, assistance);
    }

    @Test
    void theOwnerOfAPersonProfileReadsAndChangesIt() {
        var own = person(7L);
        when(people.findByUserId(5L)).thenReturn(Optional.of(own));

        assertThatCode(() -> {
            access.assertCanReadPersonProfile(7L, 5L, false);
            access.assertCanChangePersonProfile(7L, 5L, false);
        }).doesNotThrowAnyException();
    }

    @Test
    void theCareNetworkOfAnAssistedPersonReadsAndOperatesTheirProfile() {
        when(people.findByUserId(5L)).thenReturn(Optional.empty());
        when(assistance.canReadPersonProfile(7L, 5L)).thenReturn(true);
        when(assistance.canOperatePersonProfile(8L, 5L)).thenReturn(false);

        assertThatCode(() -> access.assertCanReadPersonProfile(7L, 5L, false)).doesNotThrowAnyException();
        // Reading is not changing: a reader of the household that does not operate cannot edit.
        assertThatThrownBy(() -> access.assertCanChangePersonProfile(8L, 5L, false))
                .isInstanceOf(ProfileAccessDeniedException.class);
    }

    @Test
    void anotherUserIsRefusedAndSoIsAProfileThatDoesNotExist() {
        var other = person(70L);
        when(people.findByUserId(9L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> access.assertCanReadPersonProfile(7L, 9L, false))
                .isInstanceOf(ProfileAccessDeniedException.class);
        assertThatThrownBy(() -> access.assertCanChangePersonProfile(999L, 9L, false))
                .isInstanceOf(ProfileAccessDeniedException.class);
        assertThatThrownBy(() -> access.assertCanReadPersonProfile(7L, null, false))
                .isInstanceOf(ProfileAccessDeniedException.class);
    }

    @Test
    void aStaffProfileIsOpenToItsEmployeeAndNoOneElse() {
        var own = mock(StaffProfile.class);
        when(own.getId()).thenReturn(7L);
        when(staff.findByUserId(5L)).thenReturn(Optional.of(own));
        when(staff.findByUserId(6L)).thenReturn(Optional.empty());

        assertThatCode(() -> access.assertCanAccessStaffProfile(7L, 5L, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> access.assertCanAccessStaffProfile(8L, 5L, false))
                .isInstanceOf(ProfileAccessDeniedException.class);
        assertThatThrownBy(() -> access.assertCanAccessStaffProfile(7L, 6L, false))
                .isInstanceOf(ProfileAccessDeniedException.class);
    }
}
