package com.resolum.alivia.iam.interfaces.rest.controllers;

import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.interfaces.rest.resources.RegisteredAccountResource;
import com.resolum.alivia.iam.interfaces.rest.resources.SignUpResource;
import com.resolum.alivia.iam.interfaces.rest.resources.VerifiedAccountResource;
import com.resolum.alivia.iam.interfaces.rest.resources.VerifyEmailResource;
import com.resolum.alivia.iam.interfaces.rest.transform.RegisteredAccountResourceFromEntityAssembler;
import com.resolum.alivia.iam.interfaces.rest.transform.SignUpCommandFromResourceAssembler;
import com.resolum.alivia.iam.interfaces.rest.transform.VerifiedAccountResourceFromEntityAssembler;
import com.resolum.alivia.iam.interfaces.rest.transform.VerifyEmailCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for account registration and email verification.
 *
 * <p>This layer only translates resources into commands. Token generation, hashing and
 * link building belong to the application service, never to interfaces.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro de cuenta y verificación de correo")
public class AuthenticationController {

    private final UserCommandService userCommandService;

    /**
     * Creates the controller.
     *
     * @param userCommandService the IAM command service
     */
    public AuthenticationController(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    /**
     * Registers a new account and sends its email verification link.
     *
     * @param resource the sign-up request
     * @return the registered account
     */
    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una cuenta",
            description = "Crea una cuenta pendiente de verificación y envía un enlace al correo registrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta registrada",
                    content = @Content(schema = @Schema(implementation = RegisteredAccountResource.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Correo ya registrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Servicio de registro no disponible",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public RegisteredAccountResource signUp(@Valid @RequestBody SignUpResource resource) {
        log.info("Received sign-up request");
        var user = userCommandService.handle(
                SignUpCommandFromResourceAssembler.toCommandFromResource(resource));
        log.info("Sign-up request completed for account id {}", user.getId());
        return RegisteredAccountResourceFromEntityAssembler.toResourceFromEntity(user);
    }

    /**
     * Verifies the email address associated with a verification token.
     *
     * @param resource the verify-email request
     * @return the verified account
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Verificar el correo de una cuenta",
            description = "Activa la cuenta asociada al token. Cada token puede usarse una sola vez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta verificada",
                    content = @Content(schema = @Schema(implementation = VerifiedAccountResource.class))),
            @ApiResponse(responseCode = "400", description = "Token inválido, reutilizado o vencido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public VerifiedAccountResource verifyEmail(@RequestBody VerifyEmailResource resource) {
        log.info("Received email verification request");
        var user = userCommandService.handle(
                VerifyEmailCommandFromResourceAssembler.toCommandFromResource(resource));
        log.info("Email verification request completed for account id {}", user.getId());
        return VerifiedAccountResourceFromEntityAssembler.toResourceFromEntity(user);
    }
}
