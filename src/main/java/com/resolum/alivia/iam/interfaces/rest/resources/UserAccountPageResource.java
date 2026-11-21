package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "UserAccountPage", description = "Página de cuentas del negocio")
public record UserAccountPageResource(List<UserAccountResource> content) {
}
