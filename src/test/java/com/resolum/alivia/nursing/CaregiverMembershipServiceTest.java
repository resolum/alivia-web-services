package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.application.internal.commandservices.CaregiverMembershipCommandServiceImpl;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonNotInHouseholdException;
import com.resolum.alivia.nursing.domain.exceptions.CareMemberProfileNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.CaregiverAlreadyLinkedException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.commands.LinkCaregiverCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonsQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.CaregiverSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.MembershipStatus;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.CareNetworkQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Caregiver membership service Tests")
class CaregiverMembershipServiceTest {

    private static final Long HOUSEHOLD = 1L;
    private static final UserId REQUESTER = new UserId(10L);
    private static final UserId CAREGIVER = new UserId(30L);
    private static final PersonProfileId ASSISTED_PROFILE = new PersonProfileId(200L);
    private static final PersonProfileId CAREGIVER_PROFILE = new PersonProfileId(300L);

    @Mock
    private CareMembershipRepository careMembershipRepository;
    @Mock
    private CareNetworkQueryService careNetworkQueryService;
    @Mock
    private ExternalHouseholdsService externalHouseholdsService;
    @Mock
    private ExternalIamService externalIamService;
    @Mock
    private ExternalProfilesService externalProfilesService;

    private CaregiverMembershipCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CaregiverMembershipCommandServiceImpl(careMembershipRepository, careNetworkQueryService,
                externalHouseholdsService, externalIamService, externalProfilesService);
    }

    @Test
    @DisplayName("Links the caregiver, adds it to the household and returns its name and email")
    void linksTheCaregiver() {
        givenAssistedPersonInHousehold();
        givenCaregiverProfile();
        when(careMembershipRepository.existsByUserIdAndAssistedPersonProfileIdAndRole(CAREGIVER, ASSISTED_PROFILE,
                CareRole.CAREGIVER)).thenReturn(false);
        when(externalProfilesService.fetchPersonNames(Set.of(CAREGIVER_PROFILE)))
                .thenReturn(Map.of(CAREGIVER_PROFILE, "Ana Torres"));
        when(externalIamService.fetchEmails(Set.of(CAREGIVER))).thenReturn(Map.of(CAREGIVER, "ana@alivia.com"));

        var result = service.handle(command());

        assertThat(result).isEqualTo(new CaregiverSummary(CAREGIVER, CAREGIVER_PROFILE, ASSISTED_PROFILE,
                "Ana Torres", "ana@alivia.com"));
        verify(externalHouseholdsService).requireManageable(HOUSEHOLD, REQUESTER);
        verify(externalHouseholdsService).addCaregiver(HOUSEHOLD, CAREGIVER);
        var saved = ArgumentCaptor.forClass(CareMembership.class);
        verify(careMembershipRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(CAREGIVER);
        assertThat(saved.getValue().getMemberPersonProfileId()).isEqualTo(CAREGIVER_PROFILE);
        assertThat(saved.getValue().getAssistedPersonProfileId()).isEqualTo(ASSISTED_PROFILE);
        assertThat(saved.getValue().getRole()).isEqualTo(CareRole.CAREGIVER);
        assertThat(saved.getValue().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("A requester who cannot manage the household links nothing")
    void deniedRequesterLinksNothing() {
        when(externalHouseholdsService.requireManageable(HOUSEHOLD, REQUESTER))
                .thenThrow(new CareNetworkAccessDeniedException(HOUSEHOLD));

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(CareNetworkAccessDeniedException.class);
        verifyNoInteractions(careNetworkQueryService, careMembershipRepository, externalIamService);
    }

    @Test
    @DisplayName("An assisted person outside the household is not found")
    void assistedPersonOutsideHousehold() {
        when(careNetworkQueryService.handle(new GetAssistedPersonsQuery(REQUESTER, HOUSEHOLD)))
                .thenReturn(List.of(new AssistedPersonSummary(new PersonProfileId(999L), "Otra Persona")));

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(AssistedPersonNotInHouseholdException.class);
        verify(externalHouseholdsService, never()).addCaregiver(any(), any());
        verify(careMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("A caregiver without person profile is rejected")
    void caregiverWithoutProfile() {
        givenAssistedPersonInHousehold();
        when(externalIamService.fetchPersonProfileIds(Set.of(CAREGIVER))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(CareMemberProfileNotFoundException.class);
        verify(careMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("The assisted person cannot be their own caregiver")
    void assistedPersonCannotCareForThemselves() {
        givenAssistedPersonInHousehold();
        when(externalIamService.fetchPersonProfileIds(Set.of(CAREGIVER)))
                .thenReturn(Map.of(CAREGIVER, ASSISTED_PROFILE));

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(IllegalArgumentException.class);
        verify(careMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("A caregiver already linked answers a conflict without touching the household")
    void alreadyLinkedCaregiver() {
        givenAssistedPersonInHousehold();
        givenCaregiverProfile();
        when(careMembershipRepository.existsByUserIdAndAssistedPersonProfileIdAndRole(CAREGIVER, ASSISTED_PROFILE,
                CareRole.CAREGIVER)).thenReturn(true);

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(CaregiverAlreadyLinkedException.class);
        verify(externalHouseholdsService, never()).addCaregiver(any(), any());
        verify(careMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("A concurrent duplicate rejected by the unique constraint is also a conflict")
    void concurrentDuplicate() {
        givenAssistedPersonInHousehold();
        givenCaregiverProfile();
        when(careMembershipRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(CaregiverAlreadyLinkedException.class);
    }

    private void givenAssistedPersonInHousehold() {
        when(careNetworkQueryService.handle(new GetAssistedPersonsQuery(REQUESTER, HOUSEHOLD)))
                .thenReturn(List.of(new AssistedPersonSummary(ASSISTED_PROFILE, "Maria Lopez")));
    }

    private void givenCaregiverProfile() {
        when(externalIamService.fetchPersonProfileIds(Set.of(CAREGIVER))).thenReturn(Map.of(CAREGIVER, CAREGIVER_PROFILE));
    }

    private static LinkCaregiverCommand command() {
        return new LinkCaregiverCommand(REQUESTER, HOUSEHOLD, ASSISTED_PROFILE, CAREGIVER);
    }
}
