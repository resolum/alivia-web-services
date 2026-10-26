package com.resolum.alivia.iam.interfaces.rest.controllers;

import com.resolum.alivia.iam.domain.exceptions.AccountNotFoundException;
import com.resolum.alivia.iam.domain.model.commands.RefreshSessionCommand;
import com.resolum.alivia.iam.domain.model.commands.ResendVerificationCommand;
import com.resolum.alivia.iam.domain.model.commands.SignInCommand;
import com.resolum.alivia.iam.domain.model.commands.SignOutCommand;
import com.resolum.alivia.iam.domain.model.queries.GetUserByAccountIdQuery;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.domain.services.UserQueryService;
import com.resolum.alivia.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.resolum.alivia.iam.interfaces.rest.resources.EmailVerificationStatusResource;
import com.resolum.alivia.iam.interfaces.rest.resources.RefreshTokenResource;
import com.resolum.alivia.iam.interfaces.rest.resources.RegisteredAccountResource;
import com.resolum.alivia.iam.interfaces.rest.resources.ResendVerificationResource;
import com.resolum.alivia.iam.interfaces.rest.resources.SignInResource;
import com.resolum.alivia.iam.interfaces.rest.resources.SignUpResource;
import com.resolum.alivia.iam.interfaces.rest.resources.VerifiedAccountResource;
import com.resolum.alivia.iam.interfaces.rest.resources.VerifyEmailResource;
import com.resolum.alivia.iam.interfaces.rest.transform.AuthenticatedUserResourceFromSessionAssembler;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for account registration and email verification.
 *
 * <p>
 * This layer only translates resources into commands. Token generation, hashing
 * and
 * link building belong to the application service, never to interfaces.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro de cuenta, verificación de correo e inicio de sesión")
public class AuthenticationController {

        private final UserCommandService userCommandService;
        private final UserQueryService userQueryService;

        /**
         * Creates the controller.
         *
         * @param userCommandService the IAM command service
         * @param userQueryService   the IAM query service
         */
        public AuthenticationController(UserCommandService userCommandService,
                        UserQueryService userQueryService) {
                this.userCommandService = userCommandService;
                this.userQueryService = userQueryService;
        }

        /**
         * Registers a new account and sends its email verification link.
         *
         * @param resource the sign-up request
         * @return the registered account
         */
        @PostMapping("/sign-up")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Registrar una cuenta", description = "Crea una cuenta pendiente de verificación y envía un enlace al correo registrado.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Cuenta registrada", content = @Content(schema = @Schema(implementation = RegisteredAccountResource.class))),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                        @ApiResponse(responseCode = "409", description = "Correo ya registrado", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                        @ApiResponse(responseCode = "503", description = "Servicio de registro no disponible", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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
        @Operation(summary = "Verificar el correo de una cuenta", description = "Activa la cuenta asociada al token. Cada token puede usarse una sola vez.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Cuenta verificada", content = @Content(schema = @Schema(implementation = VerifiedAccountResource.class))),
                        @ApiResponse(responseCode = "400", description = "Token inválido, reutilizado o vencido", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
        })
        public VerifiedAccountResource verifyEmail(@RequestBody VerifyEmailResource resource) {
                log.info("Received email verification request");
                var user = userCommandService.handle(
                                VerifyEmailCommandFromResourceAssembler.toCommandFromResource(resource));
                log.info("Email verification request completed for account id {}", user.getId());
                return VerifiedAccountResourceFromEntityAssembler.toResourceFromEntity(user);
        }

        /**
         * Returns whether the email address of an account has been verified.
         *
         * @param accountId the public account identifier
         * @return the verification status of the account
         */
        @GetMapping("/accounts/{accountId}/verification-status")
        @Operation(summary = "Consultar la verificación del correo", description = "Indica si la cuenta ya verificó su correo electrónico.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Estado de verificación", content = @Content(schema = @Schema(implementation = EmailVerificationStatusResource.class))),
                        @ApiResponse(responseCode = "404", description = "Cuenta no encontrada", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
        })
        public EmailVerificationStatusResource getVerificationStatus(@PathVariable String accountId) {
                var user = userQueryService.handle(new GetUserByAccountIdQuery(accountId))
                                .orElseThrow(AccountNotFoundException::new);
                return new EmailVerificationStatusResource(user.getAccountId(),
                                Boolean.TRUE.equals(user.getEmailVerified()), user.getStatus().name());
        }

        /**
         * Requests a new verification link for a pending account.
         *
         * @param resource the resend-verification request
         */
        @PostMapping("/resend-verification")
        @ResponseStatus(HttpStatus.ACCEPTED)
        @Operation(summary = "Reenviar el enlace de verificación", description = "Invalida el enlace anterior y envía uno nuevo. Responde 202 también si el correo no está registrado.")
        @ApiResponses({
                        @ApiResponse(responseCode = "202", description = "Solicitud aceptada"),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                        @ApiResponse(responseCode = "409", description = "La cuenta ya fue verificada", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                        @ApiResponse(responseCode = "429", description = "Reenvío solicitado antes del tiempo permitido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                        @ApiResponse(responseCode = "503", description = "Servicio de correo no disponible", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
        })
        public void resendVerification(@Valid @RequestBody ResendVerificationResource resource) {
                log.info("Received verification resend request");
                userCommandService.handle(new ResendVerificationCommand(resource.email()));
        }

        /**
         * Authenticates an account and issues its session tokens.
         *
         * @param resource the sign-in request
         * @return the issued tokens with the account and its permissions
         */
        @PostMapping("/sign-in")
        @Operation(summary = "Iniciar sesión", description = "Valida las credenciales y el estado de la cuenta y emite un token de acceso y uno de renovación.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Sesión iniciada", content = @Content(schema = @Schema(implementation = AuthenticatedUserResource.class))),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                        @ApiResponse(responseCode = "401", description = "Credenciales incorrectas", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                        @ApiResponse(responseCode = "403", description = "Cuenta bloqueada o inactiva", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
        })
        public AuthenticatedUserResource login(@Valid @RequestBody SignInResource resource) {
                log.info("Received sign-in request");
                var session = userCommandService.handle(new SignInCommand(resource.email(), resource.password()));
                return AuthenticatedUserResourceFromSessionAssembler.toResourceFromSession(session);
        }

        /**
         * Renews a session with its refresh token. The refresh token is rotated.
         *
         * @param resource the refresh request
         * @return the new tokens with the account and its permissions
         */
        @PostMapping("/refresh")
        @Operation(summary = "Renovar la sesión", description = "Emite nuevos tokens. El token de renovación enviado deja de ser válido.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Sesión renovada", content = @Content(schema = @Schema(implementation = AuthenticatedUserResource.class))),
                        @ApiResponse(responseCode = "401", description = "Token de renovación inválido o vencido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                        @ApiResponse(responseCode = "403", description = "Cuenta inactiva", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
        })
        public AuthenticatedUserResource refresh(@Valid @RequestBody RefreshTokenResource resource) {
                log.info("Received session refresh request");
                var session = userCommandService.handle(new RefreshSessionCommand(resource.refreshToken()));
                return AuthenticatedUserResourceFromSessionAssembler.toResourceFromSession(session);
        }

        /**
         * Closes the session bound to a refresh token.
         *
         * @param resource the sign-out request
         */
        @PostMapping("/logout")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Cerrar sesión", description = "Invalida el token de renovación. El token de acceso sigue vigente hasta su vencimiento.")
        @ApiResponse(responseCode = "204", description = "Sesión cerrada")
        public void logout(@Valid @RequestBody RefreshTokenResource resource) {
                log.info("Received sign-out request");
                userCommandService.handle(new SignOutCommand(resource.refreshToken()));
        }
}
