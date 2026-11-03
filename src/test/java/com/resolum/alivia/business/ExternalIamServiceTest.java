package com.resolum.alivia.business;

import com.resolum.alivia.business.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.business.domain.exceptions.EmailAddressAlreadyExists;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalIamService (Business ACL) Tests")
class ExternalIamServiceTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);

    @Mock
    private IamContextFacade iamContextFacade;

    @InjectMocks
    private ExternalIamService externalIamService;

    @Test
    @DisplayName("signUpCompanyUser delegates to IamContextFacade.registerCompanyUser and returns account ID")
    void signUpCompanyUserDelegatesToIamContextFacade() {
        when(iamContextFacade.registerCompanyUser(
                "employee@acme.com",
                "Password123!",
                "John",
                "Doe",
                "+51987654321",
                "EMPLOYEE",
                10L,
                "12345678",
                BIRTH_DATE
        )).thenReturn(42L);

        Long accountId = externalIamService.signUpCompanyUser(
                "employee@acme.com",
                "Password123!",
                "John",
                "Doe",
                "+51987654321",
                "EMPLOYEE",
                10L,
                "12345678",
                BIRTH_DATE
        );

        assertThat(accountId).isEqualTo(42L);
        verify(iamContextFacade).registerCompanyUser(
                "employee@acme.com",
                "Password123!",
                "John",
                "Doe",
                "+51987654321",
                "EMPLOYEE",
                10L,
                "12345678",
                BIRTH_DATE
        );
    }

    @Test
    @DisplayName("existsUserByEmail delegates to IamContextFacade")
    void existsUserByEmailDelegates() {
        when(iamContextFacade.existsUserByEmail("employee@acme.com")).thenReturn(true);

        boolean exists = externalIamService.existsUserByEmail("employee@acme.com");

        assertThat(exists).isTrue();
        verify(iamContextFacade).existsUserByEmail("employee@acme.com");
    }

    @Test
    @DisplayName("signUpAdministrator succeeds when email is not taken")
    void signUpAdministratorSuccess() {
        when(iamContextFacade.existsUserByEmail("admin@corp.com")).thenReturn(false);
        when(iamContextFacade.signUpUser("admin@corp.com", "Secret123!", "Admin", "User", "999888777",
                "ADMINISTRATOR", null, null, null, null)).thenReturn(99L);

        Long accountId = externalIamService.signUpAdministrator("admin@corp.com", "Secret123!", "Admin", "User", "999888777");

        assertThat(accountId).isEqualTo(99L);
        verify(iamContextFacade).signUpUser("admin@corp.com", "Secret123!", "Admin", "User", "999888777",
                "ADMINISTRATOR", null, null, null, null);
    }

    @Test
    @DisplayName("signUpAdministrator throws EmailAddressAlreadyExists when email is already taken")
    void signUpAdministratorThrowsWhenEmailExists() {
        when(iamContextFacade.existsUserByEmail("taken@corp.com")).thenReturn(true);

        assertThatThrownBy(() ->
                externalIamService.signUpAdministrator("taken@corp.com", "Secret123!", "Admin", "User", "999888777"))
                .isInstanceOf(EmailAddressAlreadyExists.class);
    }
}
