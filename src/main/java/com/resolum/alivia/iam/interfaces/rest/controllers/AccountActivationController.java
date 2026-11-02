package com.resolum.alivia.iam.interfaces.rest.controllers;

import com.resolum.alivia.iam.domain.model.commands.ActivateAccountCommand;
import com.resolum.alivia.iam.domain.model.queries.GetAccountActivationQuery;
import com.resolum.alivia.iam.domain.services.AccountActivationService;
import com.resolum.alivia.iam.interfaces.rest.resources.AccountActivationResource;
import com.resolum.alivia.iam.interfaces.rest.resources.ActivateAccountResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/** Public endpoints of the invitation link that new staff receive by email. */
@RestController
@RequestMapping(value = "/api/v1/auth", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Activación de cuentas", description = "Activación de cuentas del personal invitado por correo")
public class AccountActivationController {
    private final AccountActivationService activationService;

    public AccountActivationController(AccountActivationService activationService) {
        this.activationService = activationService;
    }

    @GetMapping("/activation")
    @Operation(summary = "Consultar a quién pertenece un enlace de activación")
    public AccountActivationResource activation(@RequestParam String token) {
        var activation = activationService.handle(new GetAccountActivationQuery(token));
        return new AccountActivationResource(activation.email(), activation.fullName(), activation.expiresAt());
    }

    @PostMapping(value = "/activate", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Activar la cuenta con la contraseña elegida")
    public void activate(@Valid @RequestBody ActivateAccountResource resource) {
        activationService.handle(new ActivateAccountCommand(resource.token(), resource.password()));
    }
}
