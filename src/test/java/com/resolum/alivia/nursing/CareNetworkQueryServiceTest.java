package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.application.internal.queryservices.CareNetworkQueryServiceImpl;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonsQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetCaregiversQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Care network query service Tests")
class CareNetworkQueryServiceTest {

    private static final Long HOUSEHOLD = 1L;
    private static final UserId REQUESTER = new UserId(10L);
    private static final UserId ASSISTED_USER = new UserId(20L);
    private static final UserId CAREGIVER_USER = new UserId(30L);
    private static final PersonProfileId REQUESTER_PROFILE = new PersonProfileId(100L);
    private static final PersonProfileId ASSISTED_PROFILE = new PersonProfileId(200L);
    private static final PersonProfileId CAREGIVER_PROFILE = new PersonProfileId(300L);

    @Mock
    private CareMembershipRepository careMembershipRepository;
    @Mock
    private ExternalHouseholdsService externalHouseholdsService;
    @Mock
    private ExternalIamService externalIamService;
    @Mock
    private ExternalProfilesService externalProfilesService;

    private CareNetworkQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CareNetworkQueryServiceImpl(careMembershipRepository, externalHouseholdsService,
                externalIamService, externalProfilesService);
    }

    @Test
    @DisplayName("Lists the assisted persons whose user belongs to the household, once each, with their name")
    void listsAssistedPersons() {
        givenReadableHousehold();
        when(careMembershipRepository.findActiveByAssistedPersonProfileIds(any())).thenReturn(List.of(
                membership(REQUESTER, REQUESTER_PROFILE, ASSISTED_PROFILE, CareRole.RELATIVE),
                membership(CAREGIVER_USER, CAREGIVER_PROFILE, ASSISTED_PROFILE, CareRole.CAREGIVER)));
        when(externalProfilesService.fetchPersonNames(Set.of(ASSISTED_PROFILE)))
                .thenReturn(Map.of(ASSISTED_PROFILE, "Maria Lopez"));

        var result = service.handle(new GetAssistedPersonsQuery(REQUESTER, HOUSEHOLD));

        assertThat(result).containsExactly(new AssistedPersonSummary(ASSISTED_PROFILE, "Maria Lopez"));
    }

    @Test
    @DisplayName("Looks memberships up by the profiles of the household members only")
    void searchesByMemberProfilesOnly() {
        givenReadableHousehold();
        when(careMembershipRepository.findActiveByAssistedPersonProfileIds(any())).thenReturn(List.of());

        var result = service.handle(new GetAssistedPersonsQuery(REQUESTER, HOUSEHOLD));

        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(careMembershipRepository).findActiveByAssistedPersonProfileIds(
                org.mockito.ArgumentMatchers.argThat(profiles -> Set.copyOf(profiles)
                        .equals(Set.of(REQUESTER_PROFILE, ASSISTED_PROFILE))));
    }

    @Test
    @DisplayName("An unresolved name is left null instead of failing the listing")
    void unresolvedNameIsNull() {
        givenReadableHousehold();
        when(careMembershipRepository.findActiveByAssistedPersonProfileIds(any())).thenReturn(List.of(
                membership(REQUESTER, REQUESTER_PROFILE, ASSISTED_PROFILE, CareRole.RELATIVE)));
        when(externalProfilesService.fetchPersonNames(any())).thenReturn(Map.of());

        assertThat(service.handle(new GetAssistedPersonsQuery(REQUESTER, HOUSEHOLD)))
                .containsExactly(new AssistedPersonSummary(ASSISTED_PROFILE, null));
    }

    @Test
    @DisplayName("Lists only caregivers, with name and email, leaving relatives out")
    void listsCaregiversOnly() {
        givenReadableHousehold();
        when(careMembershipRepository.findActiveByAssistedPersonProfileIds(any())).thenReturn(List.of(
                membership(REQUESTER, REQUESTER_PROFILE, ASSISTED_PROFILE, CareRole.RELATIVE),
                membership(CAREGIVER_USER, CAREGIVER_PROFILE, ASSISTED_PROFILE, CareRole.CAREGIVER)));
        when(externalProfilesService.fetchPersonNames(Set.of(CAREGIVER_PROFILE)))
                .thenReturn(Map.of(CAREGIVER_PROFILE, "Ana Torres"));
        when(externalIamService.fetchEmails(Set.of(CAREGIVER_USER)))
                .thenReturn(Map.of(CAREGIVER_USER, "ana@alivia.com"));

        var result = service.handle(new GetCaregiversQuery(REQUESTER, HOUSEHOLD));

        assertThat(result).singleElement().satisfies(caregiver -> {
            assertThat(caregiver.userId()).isEqualTo(CAREGIVER_USER);
            assertThat(caregiver.personProfileId()).isEqualTo(CAREGIVER_PROFILE);
            assertThat(caregiver.assistedPersonProfileId()).isEqualTo(ASSISTED_PROFILE);
            assertThat(caregiver.fullName()).isEqualTo("Ana Torres");
            assertThat(caregiver.email()).isEqualTo("ana@alivia.com");
        });
    }

    @Test
    @DisplayName("A missing household or a denied user never reaches IAM or the memberships")
    void authorizationFailuresStopTheQuery() {
        when(externalHouseholdsService.requireReadable(HOUSEHOLD, REQUESTER))
                .thenThrow(new CareNetworkHouseholdNotFoundException(HOUSEHOLD))
                .thenThrow(new CareNetworkAccessDeniedException(HOUSEHOLD));

        assertThatThrownBy(() -> service.handle(new GetAssistedPersonsQuery(REQUESTER, HOUSEHOLD)))
                .isInstanceOf(CareNetworkHouseholdNotFoundException.class);
        assertThatThrownBy(() -> service.handle(new GetCaregiversQuery(REQUESTER, HOUSEHOLD)))
                .isInstanceOf(CareNetworkAccessDeniedException.class);
        verifyNoInteractions(careMembershipRepository, externalIamService, externalProfilesService);
    }

    @Test
    @DisplayName("Queries reject a non positive household")
    void queriesValidateTheHousehold() {
        assertThatThrownBy(() -> new GetAssistedPersonsQuery(REQUESTER, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GetCaregiversQuery(REQUESTER, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private void givenReadableHousehold() {
        var members = Set.of(REQUESTER, ASSISTED_USER, CAREGIVER_USER);
        when(externalHouseholdsService.requireReadable(HOUSEHOLD, REQUESTER)).thenReturn(members);
        when(externalIamService.fetchPersonProfileIds(members)).thenReturn(Map.of(
                REQUESTER, REQUESTER_PROFILE, ASSISTED_USER, ASSISTED_PROFILE));
    }

    private static CareMembership membership(UserId user, PersonProfileId memberProfile,
                                             PersonProfileId assistedProfile, CareRole role) {
        return new CareMembership(user, memberProfile, assistedProfile, role);
    }
}
