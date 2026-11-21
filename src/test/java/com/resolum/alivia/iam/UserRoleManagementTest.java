package com.resolum.alivia.iam;

import com.resolum.alivia.hcm.application.acl.HcmContextFacadeImpl;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import com.resolum.alivia.hcm.interfaces.acl.HcmContextFacade;
import com.resolum.alivia.iam.application.acl.IamContextFacadeImpl;
import com.resolum.alivia.iam.application.internal.commandservices.UserCommandServiceImpl;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalCommunicationsService;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.EmailVerificationTokenService;
import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.application.internal.queryservices.UserQueryServiceImpl;
import com.resolum.alivia.iam.domain.exceptions.AccountNotFoundException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.AssignAccessRoleCommand;
import com.resolum.alivia.iam.domain.model.commands.RevokeAccessRoleCommand;
import com.resolum.alivia.iam.domain.model.queries.GetUsersByBusinessIdQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.AccountActivationService;
import com.resolum.alivia.iam.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.iam.domain.services.EdgeCredentialQueryService;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.domain.services.UserQueryService;
import com.resolum.alivia.iam.interfaces.rest.controllers.UsersController;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role management across IAM's services and its HCM link (US-17). */
class UserRoleManagementTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    @Test
    void commandServiceGrantsAndRevokesRolesOfAnExistingUserOnly() {
        var service = new UserCommandServiceImpl(userRepository, mock(HashingService.class),
                mock(EmailVerificationTokenService.class), mock(ExternalProfilesService.class),
                mock(ExternalCommunicationsService.class), mock(TokenService.class), Clock.systemUTC(), 60);
        var user = user(AccessRole.TECHNICIAN, 7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.handle(new AssignAccessRoleCommand(7L, AccessRole.ADMINISTRATOR));
        assertEquals(Set.of(AccessRole.TECHNICIAN, AccessRole.ADMINISTRATOR), user.getRoles());

        service.handle(new RevokeAccessRoleCommand(7L, AccessRole.TECHNICIAN));
        assertEquals(Set.of(AccessRole.ADMINISTRATOR), user.getRoles());

        assertThrows(AccountNotFoundException.class,
                () -> service.handle(new AssignAccessRoleCommand(99L, AccessRole.TECHNICIAN)));
        assertThrows(AccountNotFoundException.class,
                () -> service.handle(new RevokeAccessRoleCommand(99L, AccessRole.TECHNICIAN)));
    }

    @Test
    void userWithoutRolesHasNoPrimaryRole() {
        var user = user(AccessRole.CLEANING, 7L);
        user.revokeAccessRole(AccessRole.CLEANING);

        assertNull(user.getAccessRole());
    }

    @Test
    void queryServiceListsTheBusinessEmployeesInIdOrderWithTheirNames() {
        var hcm = mock(HcmContextFacade.class);
        var profiles = mock(ExternalProfilesService.class);
        var service = new UserQueryServiceImpl(userRepository, new ExternalHcmService(hcm), profiles);
        var ana = user(AccessRole.TECHNICIAN, 8L);
        ana.assignProfile(30L);
        var luis = user(AccessRole.CLEANING, 3L);
        when(hcm.fetchUserIdsOfBusiness(1L)).thenReturn(Set.of(3L, 8L));
        when(userRepository.findAllByIds(Set.of(3L, 8L))).thenReturn(List.of(ana, luis));
        when(profiles.fetchStaffFullName(30L)).thenReturn(Optional.of("Ana Torres"));
        when(profiles.fetchStaffFullName(null)).thenReturn(Optional.empty());

        var accounts = service.handle(new GetUsersByBusinessIdQuery(1L));

        assertEquals(List.of(luis, ana), accounts.stream().map(account -> account.user()).toList());
        assertNull(accounts.get(0).fullName());
        assertEquals("Ana Torres", accounts.get(1).fullName());
    }

    @Test
    void facadeReplacesOneRoleWithAnother() {
        var commands = mock(UserCommandService.class);
        var facade = new IamContextFacadeImpl(userRepository, commands, mock(AccountActivationService.class),
                mock(EdgeCredentialCommandService.class), mock(EdgeCredentialQueryService.class));

        facade.replaceAccessRole(7L, "TECHNICIAN", "CLEANING");

        var order = inOrder(commands);
        order.verify(commands).handle(new RevokeAccessRoleCommand(7L, AccessRole.TECHNICIAN));
        order.verify(commands).handle(new AssignAccessRoleCommand(7L, AccessRole.CLEANING));
    }

    @Test
    void hcmReturnsTheAccountsOfTheRequestedOrDefaultBusiness() {
        var employees = mock(EmployeeRepository.class);
        when(employees.findAll()).thenReturn(List.of(
                new Employee(1L, 20L, 30L, EmployeeRole.TECH),
                new Employee(1L, 21L, 31L, EmployeeRole.CLEANING),
                new Employee(2L, 22L, 32L, EmployeeRole.TECH)));
        var facade = new HcmContextFacadeImpl(employees, mock(ContractPersistenceRepository.class),
                mock(WorkSchedulePersistenceRepository.class),
                mock(com.resolum.alivia.hcm.application.internal.outboundservices.acl.ExternalProfilesService.class), 1L);

        assertEquals(Set.of(20L, 21L), facade.fetchUserIdsOfBusiness(null));
        assertEquals(Set.of(22L), facade.fetchUserIdsOfBusiness(2L));
    }

    @Test
    void controllerRejectsOutOfRangePages() throws Exception {
        var queries = mock(UserQueryService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UsersController(queries, mock(UserCommandService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/users").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users").param("size", "501")).andExpect(status().isBadRequest());
        verify(queries, never()).handle(any(GetUsersByBusinessIdQuery.class));
    }

    private static User user(AccessRole role, Long id) {
        var user = new User(new EmailAddress("user" + id + "@alivia.pe"), new PasswordHash("hash"), role,
                LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
