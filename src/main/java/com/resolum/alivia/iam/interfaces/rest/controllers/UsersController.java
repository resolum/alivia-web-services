package com.resolum.alivia.iam.interfaces.rest.controllers;

import com.resolum.alivia.iam.domain.exceptions.AccountNotFoundException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.AssignAccessRoleCommand;
import com.resolum.alivia.iam.domain.model.commands.RevokeAccessRoleCommand;
import com.resolum.alivia.iam.domain.model.queries.GetUsersByBusinessIdQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.UserAccount;
import com.resolum.alivia.iam.domain.services.RoleParser;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.domain.services.UserQueryService;
import com.resolum.alivia.iam.interfaces.rest.resources.UserAccountPageResource;
import com.resolum.alivia.iam.interfaces.rest.resources.UserAccountResource;
import com.resolum.alivia.iam.interfaces.rest.transform.UserAccountResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.function.Function;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** Roles of the accounts of a business, managed by its administrator. */
@RestController
@RequestMapping(value = "/api/v1/users", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Usuarios", description = "Roles de las cuentas del negocio")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class UsersController {
    private static final int MAX_PAGE_SIZE = 500;

    private final UserQueryService queryService;
    private final UserCommandService commandService;

    public UsersController(UserQueryService queryService, UserCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping
    @Operation(summary = "Listar las cuentas del negocio con sus roles")
    public UserAccountPageResource getUsers(@RequestParam(required = false) Long businessId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE + ".");
        }
        var content = queryService.handle(new GetUsersByBusinessIdQuery(businessId)).stream()
                .skip((long) page * size).limit(size)
                .map(UserAccountResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return new UserAccountPageResource(content);
    }

    @PutMapping("/{id}/roles/{role}")
    @Operation(summary = "Asignar un rol; si la cuenta ya lo tiene, no cambia nada")
    public UserAccountResource assignRole(@PathVariable String id, @PathVariable String role,
                                          @RequestParam(required = false) Long businessId) {
        var accessRole = parse(role);
        return change(id, businessId, userId -> commandService.handle(new AssignAccessRoleCommand(userId, accessRole)));
    }

    @DeleteMapping("/{id}/roles/{role}")
    @Operation(summary = "Quitar un rol; los demás roles quedan igual")
    public UserAccountResource revokeRole(@PathVariable String id, @PathVariable String role,
                                          @RequestParam(required = false) Long businessId) {
        var accessRole = parse(role);
        return change(id, businessId, userId -> commandService.handle(new RevokeAccessRoleCommand(userId, accessRole)));
    }

    // Only accounts of the administrator's business can be changed; any other id is answered as not found.
    private UserAccountResource change(String accountId, Long businessId, Function<Long, User> command) {
        var target = queryService.handle(new GetUsersByBusinessIdQuery(businessId)).stream()
                .filter(account -> account.user().getAccountId().equals(accountId))
                .findFirst()
                .orElseThrow(AccountNotFoundException::new);
        var updated = command.apply(target.user().getId());
        return UserAccountResourceFromEntityAssembler.toResourceFromEntity(
                new UserAccount(updated, target.fullName()));
    }

    private static AccessRole parse(String role) {
        return RoleParser.parseAssignable(role).orElseThrow(() -> new IllegalArgumentException(
                "role must be one of ADMIN, TECH, SUBSCRIPTION_MANAGER, CLEANING, RELATIVE, CAREGIVER."));
    }
}
