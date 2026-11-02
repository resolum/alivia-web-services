package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.queryservices.UserQueryServiceImpl;
import com.resolum.alivia.iam.domain.exceptions.AccountNotActiveException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.entities.Role;
import com.resolum.alivia.iam.domain.model.queries.GetUserByAccountIdQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserQueryServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserQueryServiceImpl service = new UserQueryServiceImpl(userRepository);

    @Test
    void findsUserByAccountId() {
        var user = mock(User.class);
        when(userRepository.findByAccountId("account-1")).thenReturn(Optional.of(user));

        assertEquals(Optional.of(user), service.handle(new GetUserByAccountIdQuery("account-1")));
    }

    @Test
    void queryRequiresAccountId() {
        assertThrows(IllegalArgumentException.class, () -> new GetUserByAccountIdQuery(null));
        assertThrows(IllegalArgumentException.class, () -> new GetUserByAccountIdQuery("  "));
    }

    @Test
    void accountNotActiveExceptionKeepsStatus() {
        assertEquals(UserStatus.BLOCKED, new AccountNotActiveException(UserStatus.BLOCKED).status());
    }

    @Test
    void roleKeepsType() {
        var role = new Role(AccessRole.CAREGIVER);

        assertEquals(AccessRole.CAREGIVER, role.getType());
        assertNull(role.getId());
    }
}
