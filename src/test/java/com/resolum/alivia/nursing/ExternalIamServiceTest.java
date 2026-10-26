package com.resolum.alivia.nursing;

import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalIamService (Nursing ACL) Tests")
class ExternalIamServiceTest {

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
                "Maria Lopez",
                "+51987654322",
                5L
        )).thenReturn(99L);

        Long accountId = externalIamService.signUpAssistedPerson(
                "patient@example.com",
                "SecurePass123!",
                "Maria Lopez",
                "+51987654322",
                5L
        );

        assertThat(accountId).isEqualTo(99L);
        verify(iamContextFacade).registerAssistedPerson(
                "patient@example.com",
                "SecurePass123!",
                "Maria Lopez",
                "+51987654322",
                5L
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
    @DisplayName("fetchEmails does not call IAM for an empty set")
    void fetchEmailsSkipsIamForNoUsers() {
        assertThat(externalIamService.fetchEmails(Set.of())).isEmpty();

        verifyNoInteractions(iamContextFacade);
    }
}
