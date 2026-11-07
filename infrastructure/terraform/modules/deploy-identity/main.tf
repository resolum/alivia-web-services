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

# One credential, for one GitHub environment: a job of another environment (or of a pull request) is refused. Production
# also requires the approval configured on its GitHub environment before the job starts.
resource "azurerm_federated_identity_credential" "github" {
  name                      = "github-${var.github_environment}"
  user_assigned_identity_id = azurerm_user_assigned_identity.this.id
  issuer                    = "https://token.actions.githubusercontent.com"
  subject                   = "repo:${var.github_repository}:environment:${var.github_environment}"
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
  }
}
