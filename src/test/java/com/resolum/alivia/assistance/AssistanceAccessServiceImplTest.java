package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.assistance.application.internal.queryservices.AssistanceAccessServiceImpl;
import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.domain.repositories.CareShiftRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.HandoverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistanceAccessServiceImplTest {

    private static final long RELATIVE_USER = 1L;
    private static final long CAREGIVER_USER = 2L;
    private static final long HOUSEHOLD_MEMBER = 3L;
    private static final long OUTSIDER = 9L;

    private AssistedPersonRepository people;
    private RelativeRepository relatives;
    private CaregiverRepository caregivers;
    private CareActivityRepository activities;
    private ExternalHouseholdsService households;
    private AssistanceAccessServiceImpl access;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        people = mock(AssistedPersonRepository.class);
        relatives = mock(RelativeRepository.class);
        caregivers = mock(CaregiverRepository.class);
        activities = mock(CareActivityRepository.class);
        households = mock(ExternalHouseholdsService.class);
        access = new AssistanceAccessServiceImpl(people, relatives, caregivers, activities,
                mock(CareShiftRepository.class), mock(HandoverRepository.class), households);
        var rel = relative(10L); // its user is RELATIVE_USER (see AssistanceFixtures)
        person = assistedPerson(20L, rel);
        when(people.findById(20L)).thenReturn(Optional.of(person));
        when(caregivers.existsByAssistedPersonIdAndUserIdAndStatus(20L, new UserId(CAREGIVER_USER), CaregiverStatus.ACTIVE))
                .thenReturn(true);
    }

    @Test
    void theRelativeInChargeReadsOperatesAndManages() {
        long user = person.getRelative().getUserId().userId();
        assertThat(access.requireRead(20L, user)).isSameAs(person);
        assertThat(access.requireOperate(20L, user)).isSameAs(person);
        assertThat(access.requireManage(20L, user)).isSameAs(person);
    }

    @Test
    void anActiveCaregiverReadsAndOperatesButDoesNotManage() {
        assertThat(access.requireRead(20L, CAREGIVER_USER)).isSameAs(person);
        assertThat(access.requireOperate(20L, CAREGIVER_USER)).isSameAs(person);
        assertThatThrownBy(() -> access.requireManage(20L, CAREGIVER_USER))
                .isInstanceOf(AssistanceAccessDeniedException.class);
    }

    @Test
    void aHouseholdMemberWithReadAccessReadsOnly() {
        var inHousehold = assistedPerson(21L, relative(11L));
        inHousehold.updateCareDetails(5L, null, null, null);
        when(people.findById(21L)).thenReturn(Optional.of(inHousehold));
        when(households.canRead(5L, HOUSEHOLD_MEMBER)).thenReturn(true);

        assertThat(access.requireRead(21L, HOUSEHOLD_MEMBER)).isSameAs(inHousehold);
        assertThatThrownBy(() -> access.requireOperate(21L, HOUSEHOLD_MEMBER))
                .isInstanceOf(AssistanceAccessDeniedException.class);
    }

    @Test
    void thePrincipalOfTheHouseholdManagesThePerson() {
        var inHousehold = assistedPerson(21L, relative(11L));
        inHousehold.updateCareDetails(5L, null, null, null);
        when(people.findById(21L)).thenReturn(Optional.of(inHousehold));
        when(households.canManage(5L, HOUSEHOLD_MEMBER)).thenReturn(true);

        assertThat(access.requireManage(21L, HOUSEHOLD_MEMBER)).isSameAs(inHousehold);
        assertThat(access.requireOperate(21L, HOUSEHOLD_MEMBER)).isSameAs(inHousehold);
    }

    @Test
    void anOutsiderIsRefusedAtEveryLevel() {
        assertThatThrownBy(() -> access.requireRead(20L, OUTSIDER)).isInstanceOf(AssistanceAccessDeniedException.class);
        assertThatThrownBy(() -> access.requireOperate(20L, OUTSIDER)).isInstanceOf(AssistanceAccessDeniedException.class);
        assertThatThrownBy(() -> access.requireManage(20L, OUTSIDER)).isInstanceOf(AssistanceAccessDeniedException.class);
        assertThat(access.canOperate(person, null)).isFalse();
    }

    @Test
    void aMissingPersonOrActivityIsNotFoundNotDenied() {
        when(people.findById(99L)).thenReturn(Optional.empty());
        when(activities.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireRead(99L, OUTSIDER)).isInstanceOf(AssistedPersonNotFoundException.class);
        assertThatThrownBy(() -> access.requireRead(null, OUTSIDER)).isInstanceOf(AssistedPersonNotFoundException.class);
        assertThatThrownBy(() -> access.requireReadActivity(99L, OUTSIDER)).isInstanceOf(CareActivityNotFoundException.class);
    }

    @Test
    void readableByListsOnlyTheOwnAndCaredForPersonsWithoutDuplicates() {
        long user = person.getRelative().getUserId().userId();
        when(relatives.findByUserId(new UserId(user))).thenReturn(Optional.of(person.getRelative()));
        when(people.findAllByRelativeId(person.getRelative().getId())).thenReturn(List.of(person));
        when(caregivers.findAllByUserId(new UserId(user))).thenReturn(List.of());

        assertThat(access.readableBy(user)).containsExactly(person);
        assertThat(access.readableBy(OUTSIDER)).isEmpty();
        assertThat(access.readableBy(null)).isEmpty();
    }
}
