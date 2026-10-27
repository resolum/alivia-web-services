package com.resolum.alivia.nursing;

import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalIamService (Nursing ACL) Tests")
class ExternalIamServiceTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);

    @Mock
    private IamContextFacade iamContextFacade;

    @InjectMocks
    private ExternalIamService externalIamService;

    @Test
    @DisplayName("signUpAssistedPerson delegates to IamContextFacade.registerAssistedPerson and returns account ID")
    void signUpAssistedPersonDelegatesToIamContextFacade() {
        when(iamContextFacade.registerAssistedPerson(
                "patient@example.com",
                "SecurePass123!",
                "Maria",
                "Lopez",
                "+51987654322",
                5L,
                "12345678",
                BIRTH_DATE
        )).thenReturn(99L);

        Long accountId = externalIamService.signUpAssistedPerson(
                "patient@example.com",
                "SecurePass123!",
                "Maria",
                "Lopez",
                "+51987654322",
                5L,
                "12345678",
                BIRTH_DATE
        );

        assertThat(accountId).isEqualTo(99L);
        verify(iamContextFacade).registerAssistedPerson(
                "patient@example.com",
                "SecurePass123!",
                "Maria",
                "Lopez",
                "+51987654322",
                5L,
                "12345678",
                BIRTH_DATE
        );
    }

    @Test
    @DisplayName("existsUserByEmail delegates to IamContextFacade")
    void existsUserByEmailDelegates() {
        when(iamContextFacade.existsUserByEmail("patient@example.com")).thenReturn(false);

        boolean exists = externalIamService.existsUserByEmail("patient@example.com");

        assertThat(exists).isFalse();
        verify(iamContextFacade).existsUserByEmail("patient@example.com");
    }

    @Test
    @DisplayName("fetchEmails reads the emails from IamContextFacade in one batch")
    void fetchEmailsDelegatesInOneBatch() {
        when(iamContextFacade.fetchEmailsByUserIds(Set.of(7L, 9L))).thenReturn(Map.of(7L, "ana.torres@example.com"));

        var emails = externalIamService.fetchEmails(Set.of(new UserId(7L), new UserId(9L)));

        assertThat(emails).containsExactly(Map.entry(new UserId(7L), "ana.torres@example.com"));
    }

    @Test
    @DisplayName("fetchPersonProfileIds reads the person profiles from IamContextFacade in one batch")
    void fetchPersonProfileIdsDelegatesInOneBatch() {
        when(iamContextFacade.fetchPersonProfileIdsByUserIds(Set.of(7L, 9L))).thenReturn(Map.of(7L, 15L));

        var profiles = externalIamService.fetchPersonProfileIds(Set.of(new UserId(7L), new UserId(9L)));

        assertThat(profiles).containsExactly(Map.entry(new UserId(7L), new PersonProfileId(15L)));
    }

    @Test
    @DisplayName("fetchEmails does not call IAM for an empty set")
    void fetchEmailsSkipsIamForNoUsers() {
        assertThat(externalIamService.fetchEmails(Set.of())).isEmpty();

        verifyNoInteractions(iamContextFacade);
    }

    @Test
    @DisplayName("fetchStaffProfileIds reads the staff profiles from IamContextFacade in one batch")
    void fetchStaffProfileIdsDelegatesInOneBatch() {
        when(iamContextFacade.fetchStaffProfileIdsByUserIds(Set.of(7L, 9L))).thenReturn(Map.of(9L, 40L));

        var profiles = externalIamService.fetchStaffProfileIds(Set.of(new UserId(7L), new UserId(9L)));

        assertThat(profiles).containsExactly(Map.entry(new UserId(9L), 40L));
        assertThat(externalIamService.fetchStaffProfileIds(Set.of())).isEmpty();
    }
}
