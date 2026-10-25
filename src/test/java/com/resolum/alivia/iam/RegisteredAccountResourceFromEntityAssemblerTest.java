package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.interfaces.rest.transform.RegisteredAccountResourceFromEntityAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisteredAccountResourceFromEntityAssemblerTest {

    @Test
    void masksTheLocalPartAndKeepsTheDomain() {
        var user = userWith("maria.lopez@example.com");

        var resource = RegisteredAccountResourceFromEntityAssembler.toResourceFromEntity(user);

        assertEquals(Long.valueOf(9L), resource.accountId());
        assertEquals("m***@example.com", resource.maskedEmail());
        assertEquals("PENDING_VERIFICATION", resource.status());
    }

    @Test
    void masksShortLocalPartWithoutFailing() {
        assertEquals("a***@b.c",
                RegisteredAccountResourceFromEntityAssembler
                        .toResourceFromEntity(userWith("a@b.c")).maskedEmail());
    }

    @Test
    void masksTwoCharacterLocalPartWithoutFailing() {
        assertEquals("a***@c.d",
                RegisteredAccountResourceFromEntityAssembler
                        .toResourceFromEntity(userWith("ab@c.d")).maskedEmail());
    }

    @Test
    void masksEdgeCaseEmails() {
        var userAtStart = mock(User.class);
        when(userAtStart.getId()).thenReturn(1L);
        var emailAddress = mock(EmailAddress.class);
        when(emailAddress.email()).thenReturn("@domain.com");
        when(userAtStart.getEmail()).thenReturn(emailAddress);
        when(userAtStart.getStatus()).thenReturn(UserStatus.PENDING_VERIFICATION);

        assertEquals("***", RegisteredAccountResourceFromEntityAssembler.toResourceFromEntity(userAtStart).maskedEmail());

        when(emailAddress.email()).thenReturn("");
        assertEquals("***", RegisteredAccountResourceFromEntityAssembler.toResourceFromEntity(userAtStart).maskedEmail());

        when(emailAddress.email()).thenReturn(null);
        assertEquals("***", RegisteredAccountResourceFromEntityAssembler.toResourceFromEntity(userAtStart).maskedEmail());
    }

    private static User userWith(String email) {
        var user = mock(User.class);
        when(user.getId()).thenReturn(9L);
        when(user.getEmail()).thenReturn(new EmailAddress(email));
        when(user.getStatus()).thenReturn(UserStatus.PENDING_VERIFICATION);
        return user;
    }
}
