package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.RolesIncompatibleException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.AssignAccessRoleCommand;
import com.resolum.alivia.iam.domain.model.commands.RevokeAccessRoleCommand;
import com.resolum.alivia.iam.domain.model.queries.GetUsersByBusinessIdQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.model.valueobjects.UserAccount;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.domain.services.UserQueryService;
import com.resolum.alivia.iam.interfaces.rest.controllers.UsersController;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsersControllerTest {

    private UserQueryService queries;
    private UserCommandService commands;
    private MockMvc mvc;
    private User technician;

    @BeforeEach
    void setUp() {
        queries = mock(UserQueryService.class);
        commands = mock(UserCommandService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UsersController(queries, commands))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        technician = new User(new EmailAddress("ana@alivia.pe"), new PasswordHash("hash"),
                AccessRole.TECHNICIAN, LocalDateTime.now());
        ReflectionTestUtils.setField(technician, "id", 7L);
        when(queries.handle(any(GetUsersByBusinessIdQuery.class)))
                .thenReturn(List.of(new UserAccount(technician, "Ana Torres")));
    }

    @Test
    void listsBusinessUsersWithFrontRoleNames() throws Exception {
        mvc.perform(get("/api/v1/users").param("page", "0").param("size", "200").param("businessId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(technician.getAccountId()))
                .andExpect(jsonPath("$.content[0].fullName").value("Ana Torres"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.content[0].roles", contains("TECH")));
    }

    @Test
    void assignsRoleAndKeepsTheOthers() throws Exception {
        when(commands.handle(any(AssignAccessRoleCommand.class))).thenAnswer(invocation -> {
            technician.assignAccessRole(AccessRole.ADMINISTRATOR);
            return technician;
        });

        mvc.perform(put("/api/v1/users/{id}/roles/ADMIN", technician.getAccountId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ADMIN", "TECH")));
        verify(commands).handle(new AssignAccessRoleCommand(7L, AccessRole.ADMINISTRATOR));
    }

    @Test
    void incompatibleRoleIsConflict() throws Exception {
        when(commands.handle(any(AssignAccessRoleCommand.class))).thenThrow(
                new RolesIncompatibleException(AccessRole.SUBSCRIPTION_MANAGER, AccessRole.TECHNICIAN));

        mvc.perform(put("/api/v1/users/{id}/roles/SUBSCRIPTION_MANAGER", technician.getAccountId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("roles_incompatible"));
    }

    @Test
    void userOutsideTheBusinessIsNotFound() throws Exception {
        mvc.perform(delete("/api/v1/users/{id}/roles/TECH", "someone-else"))
                .andExpect(status().isNotFound());
        verify(commands, never()).handle(any(RevokeAccessRoleCommand.class));
    }

    @Test
    void unknownRoleIsBadRequest() throws Exception {
        mvc.perform(put("/api/v1/users/{id}/roles/ADMINISTRATOR", technician.getAccountId()))
                .andExpect(status().isBadRequest());
    }
}
