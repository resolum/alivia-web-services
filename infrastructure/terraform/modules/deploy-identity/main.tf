variable "name" {
  description = "Name of the identity, for example id-alivia-staging-deploy."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group that holds the identity."
  type        = string
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository whose workflows may sign in as this identity, as owner/name (case as GitHub writes it)."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "The repository must be written as owner/name."
  }
}

variable "github_owner_id" {
  description = <<-EOT
    GitHub's numeric id of the repository owner. Set it together with github_repository_id when GitHub presents the
    immutable form of the subject (owner@id/repo@id), which survives a rename or a transfer of the repository. Look
    at the "subject claim" the sign-in step prints, or at https://api.github.com/repos/<owner>/<repository>.
  EOT
  type        = number
  default     = null
}

variable "github_repository_id" {
  description = "GitHub's numeric id of the repository (see github_owner_id). Null together with github_owner_id: the classic subject."
  type        = number
  default     = null

  validation {
    condition     = (var.github_owner_id == null) == (var.github_repository_id == null)
    error_message = "github_owner_id and github_repository_id are set together or not at all."
  }
}

variable "github_environment" {
  description = "GitHub environment (staging or production). Only jobs that run in it can sign in as this identity."
  type        = string
}

variable "acr_id" {
  description = "Resource id of the registry the identity pushes images to."
  type        = string
}

variable "webapp_id" {
  description = "Resource id of the Web App the identity deploys to: the only Web App it can change."
  type        = string
}

variable "postgres_id" {
  description = "Resource id of the PostgreSQL server of this environment: the only server the identity can start."
  type        = string
}

variable "tags" {
  description = "Tags for the identity."
  type        = map(string)
  default     = {}
}

# The identity the GitHub workflow signs in as. There is no secret: GitHub proves who the job is with a short-lived token
# and Azure trusts it only for the subject below. It is an Azure resource, so creating it does not need permission to
# register applications in Microsoft Entra ID.
resource "azurerm_user_assigned_identity" "this" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  tags                = var.tags
}

# GitHub presents the subject in one of two forms and Azure compares it literally: repo:owner/name:environment:<env> or,
# with immutable ids, repo:owner@<owner id>/name@<repository id>:environment:<env>. A mismatch is rejected with
# AADSTS700213 "No matching federated identity record".
locals {
  repository_parts = split("/", var.github_repository)
  subject_repository = var.github_owner_id != null ? join("/", [
    "${local.repository_parts[0]}@${var.github_owner_id}",
    "${local.repository_parts[1]}@${var.github_repository_id}",
  ]) : var.github_repository
}

# One credential, for one GitHub environment: a job of another environment (or of a pull request) is refused. Production
# also requires the approval configured on its GitHub environment before the job starts.
resource "azurerm_federated_identity_credential" "github" {
  name                      = "github-${var.github_environment}"
  user_assigned_identity_id = azurerm_user_assigned_identity.this.id
  issuer                    = "https://token.actions.githubusercontent.com"
  subject                   = "repo:${local.subject_repository}:environment:${var.github_environment}"
  audience                  = ["api://AzureADTokenExchange"]
}

# Least privilege: push images to the shared registry and change this environment's Web App. Nothing on the resource
# group, so an identity made for staging cannot touch production.
resource "azurerm_role_assignment" "acr_push" {
  scope                = var.acr_id
  role_definition_name = "AcrPush"
  principal_id         = azurerm_user_assigned_identity.this.principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "webapp_contributor" {
  scope                = var.webapp_id
  role_definition_name = "Website Contributor"
  principal_id         = azurerm_user_assigned_identity.this.principal_id
  principal_type       = "ServicePrincipal"
}

# The server is stopped between sessions to save cost (workflow stop-staging), and a stopped server makes the application
# fail to start (deploy.sh starts it). No built-in role grants only this, and Contributor would also allow deleting the
# server or resetting its password, so the role is custom: read, start and stop this one server.
resource "azurerm_role_definition" "postgres_power" {
  name        = "${var.name}-postgres-power"
  scope       = var.postgres_id
  description = "Read, start and stop the PostgreSQL server of one environment (used by its deployment identity)."

  permissions {
    actions = [
      "Microsoft.DBforPostgreSQL/flexibleServers/read",
      "Microsoft.DBforPostgreSQL/flexibleServers/start/action",
      "Microsoft.DBforPostgreSQL/flexibleServers/stop/action",
    ]
    not_actions = []
  }

  assignable_scopes = [var.postgres_id]
}

resource "azurerm_role_assignment" "postgres_power" {
  scope              = var.postgres_id
  role_definition_id = azurerm_role_definition.postgres_power.role_definition_resource_id
  principal_id       = azurerm_user_assigned_identity.this.principal_id
  principal_type     = "ServicePrincipal"
}

output "name" {
  description = "Name of the identity."
  value       = azurerm_user_assigned_identity.this.name
}

output "client_id" {
  description = "AZURE_CLIENT_ID (GitHub variable of the environment). It is an identifier, not a secret."
  value       = azurerm_user_assigned_identity.this.client_id
}

output "subject" {
  description = "The only GitHub subject that can sign in as this identity."
  value       = azurerm_federated_identity_credential.github.subject
}

output "role_assignments" {
  description = "Every role the identity holds, by role name, with its scope."
  value = {
    (azurerm_role_assignment.acr_push.role_definition_name)           = azurerm_role_assignment.acr_push.scope
    (azurerm_role_assignment.webapp_contributor.role_definition_name) = azurerm_role_assignment.webapp_contributor.scope
    (azurerm_role_definition.postgres_power.name)                     = azurerm_role_assignment.postgres_power.scope
  }
}
