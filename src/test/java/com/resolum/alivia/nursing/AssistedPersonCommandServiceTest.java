package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.application.internal.commandservices.AssistedPersonCommandServiceImpl;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.exceptions.CareMemberProfileNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.commands.RegisterAssistedPersonCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.MembershipStatus;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Assisted person command service Tests")
class AssistedPersonCommandServiceTest {

    private static final Long HOUSEHOLD = 1L;
    private static final UserId REQUESTER = new UserId(10L);
    private static final PersonProfileId REQUESTER_PROFILE = new PersonProfileId(100L);
    private static final UserId ASSISTED_USER = new UserId(20L);
    private static final PersonProfileId ASSISTED_PROFILE = new PersonProfileId(200L);

    @Mock
    private CareMembershipRepository careMembershipRepository;
    @Mock
    private ExternalHouseholdsService externalHouseholdsService;
    @Mock
    private ExternalIamService externalIamService;
    @Mock
    private ExternalProfilesService externalProfilesService;

    private AssistedPersonCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AssistedPersonCommandServiceImpl(careMembershipRepository, externalHouseholdsService,
                externalIamService, externalProfilesService);
    }

    @Test
    @DisplayName("Creates the IAM account, adds it to the household and links the requester as relative")
    void registersTheAssistedPerson() {
        when(externalIamService.fetchPersonProfileIds(Set.of(REQUESTER))).thenReturn(Map.of(REQUESTER, REQUESTER_PROFILE));
        when(externalIamService.signUpAssistedPerson("maria@alivia.com", "Secret#123", "Maria", "Lopez", "51987654321",
                10L, "12345678", LocalDate.of(1950, 3, 4))).thenReturn(20L);
        when(externalIamService.fetchPersonProfileIds(Set.of(ASSISTED_USER))).thenReturn(Map.of(ASSISTED_USER, ASSISTED_PROFILE));
        when(externalProfilesService.fetchPersonNames(Set.of(ASSISTED_PROFILE)))
                .thenReturn(Map.of(ASSISTED_PROFILE, "Maria Lopez"));

        var result = service.handle(command());

        assertThat(result).isEqualTo(new AssistedPersonSummary(ASSISTED_PROFILE, "Maria Lopez"));
        verify(externalHouseholdsService).requireManageable(HOUSEHOLD, REQUESTER);
        verify(externalHouseholdsService).addAssistedPerson(HOUSEHOLD, ASSISTED_USER);
        var saved = ArgumentCaptor.forClass(CareMembership.class);
        verify(careMembershipRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(REQUESTER);
        assertThat(saved.getValue().getMemberPersonProfileId()).isEqualTo(REQUESTER_PROFILE);
        assertThat(saved.getValue().getAssistedPersonProfileId()).isEqualTo(ASSISTED_PROFILE);
        assertThat(saved.getValue().getRole()).isEqualTo(CareRole.RELATIVE);
        assertThat(saved.getValue().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("A missing household or a denied requester never creates an account")
    void authorizationFailuresCreateNothing() {
        when(externalHouseholdsService.requireManageable(HOUSEHOLD, REQUESTER))
                .thenThrow(new CareNetworkHouseholdNotFoundException(HOUSEHOLD))
                .thenThrow(new CareNetworkAccessDeniedException(HOUSEHOLD));

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(CareNetworkHouseholdNotFoundException.class);
        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(CareNetworkAccessDeniedException.class);
        verifyNoInteractions(externalIamService, careMembershipRepository, externalProfilesService);
        verify(externalHouseholdsService, never()).addAssistedPerson(anyLong(), any());
    }

    @Test
    @DisplayName("A requester without person profile is rejected before the account is created")
    void requesterWithoutProfileCreatesNothing() {
        when(externalIamService.fetchPersonProfileIds(Set.of(REQUESTER))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(CareMemberProfileNotFoundException.class);
        verify(externalIamService, never()).signUpAssistedPerson(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(careMembershipRepository);
    }

    @Test
    @DisplayName("An account created without profile fails so the registration rolls back")
    void accountWithoutProfileFails() {
        when(externalIamService.fetchPersonProfileIds(Set.of(REQUESTER))).thenReturn(Map.of(REQUESTER, REQUESTER_PROFILE));
        when(externalIamService.signUpAssistedPerson(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyString(), any())).thenReturn(20L);
        when(externalIamService.fetchPersonProfileIds(Set.of(ASSISTED_USER))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(IllegalStateException.class);
        verify(externalHouseholdsService, never()).addAssistedPerson(anyLong(), any());
        verifyNoInteractions(careMembershipRepository);
    }

    @Test
    @DisplayName("The command requires its fields and does not print the password or personal data")
    void commandValidatesAndHidesSecrets() {
        assertThatThrownBy(() -> new RegisterAssistedPersonCommand(REQUESTER, 0L, "a@b.com", "x", "A", "B", "1", "1",
                LocalDate.of(1950, 1, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegisterAssistedPersonCommand(REQUESTER, HOUSEHOLD, " ", "x", "A", "B", "1", "1",
                LocalDate.of(1950, 1, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegisterAssistedPersonCommand(REQUESTER, HOUSEHOLD, "a@b.com", "x", "A", "B", "1", "1",
                null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(command().toString())
                .doesNotContain("Secret#123", "maria@alivia.com", "12345678", "Maria")
                .contains("householdId=1");
    }

    private static RegisterAssistedPersonCommand command() {
        return new RegisterAssistedPersonCommand(REQUESTER, HOUSEHOLD, " maria@alivia.com ", "Secret#123", "Maria",
                "Lopez", "51987654321", "12345678", LocalDate.of(1950, 3, 4));
    }
}
