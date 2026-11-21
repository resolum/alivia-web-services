package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "UserAccount", description = "Cuenta de un usuario del negocio y sus roles")
public record UserAccountResource(
        @Schema(description = "Identificador de la cuenta", example = "3f1c2a9e-8b7d-4c5e-9a1f-2b3c4d5e6f70")
        String id,

        @Schema(description = "Correo electrónico", example = "ana.torres@alivia.pe")
        String email,

        @Schema(description = "Nombre completo; null si la cuenta no tiene perfil", example = "Ana Torres")
        String fullName,

        @Schema(description = "Estado de la cuenta", allowableValues = {"ACTIVE", "PENDING_VERIFICATION", "SUSPENDED"})
        String status,

        @Schema(description = "Roles, con los nombres del front", example = "[\"ADMIN\", \"TECH\"]")
        List<String> roles
) {
}
