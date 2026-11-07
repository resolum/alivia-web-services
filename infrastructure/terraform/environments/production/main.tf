terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 5.13"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
    mongodbatlas = {
      source  = "mongodb/mongodbatlas"
      version = "~> 2.21"
    }
  }
}

# The subscription comes from ARM_SUBSCRIPTION_ID (or az login). No provider is registered automatically: the three
# this configuration needs are registered once by hand (see the README).
provider "azurerm" {
  features {}
  resource_provider_registrations = "none"
}

# The Atlas keys of the organization come from MONGODB_ATLAS_PUBLIC_KEY and MONGODB_ATLAS_PRIVATE_KEY in the
# environment of whoever applies. They never go in a file.
provider "mongodbatlas" {}

locals {
  environment = "production"
  tags        = merge(var.tags, { environment = local.environment })
}

variable "location" {
  description = "Azure region (the same one as the shared configuration)."
  type        = string
  default     = "brazilsouth"
}

variable "resource_group_name" {
  description = "The only resource group of the project, created by the shared configuration."
  type        = string
  default     = "rg-alivia"
}

variable "acr_name" {
  description = "Name of the registry created by the shared configuration (its acr_name output)."
  type        = string
}

variable "app_service_sku" {
  description = "App Service plan SKU. The free tier cannot run custom containers."
  type        = string
  default     = "B1"
}

variable "postgres_version" {
  description = "PostgreSQL major version (17 at least; the highest the region offers)."
  type        = string
  default     = "17"
}

variable "postgres_sku_name" {
  description = "PostgreSQL server SKU."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "atlas_organization_id" {
  description = "Id of the Atlas organization."
  type        = string
}

variable "atlas_region" {
  description = "Atlas name of the Azure region of the MongoDB cluster (the closest one that offers M0)."
  type        = string
  default     = "US_EAST"
}

variable "atlas_instance_size" {
  description = "MongoDB cluster tier (M0 is the free one)."
  type        = string
  default     = "M0"
}

variable "operator_ips" {
  description = "Public addresses of whoever checks the database connections by hand. Empty once the check is done."
  type        = list(string)
  default     = []
}

variable "github_repository" {
  description = "GitHub repository (owner/name) whose workflows deploy to this environment."
  type        = string
  default     = "resolum/alivia-web-services"
}

variable "tags" {
  description = "Tags for every resource."
  type        = map(string)
  default = {
    project    = "alivia"
    ticket     = "TS-41"
    managed_by = "terraform"
  }
}

data "azurerm_resource_group" "this" {
  name = var.resource_group_name
}

# The tenant and subscription the GitHub workflow signs in to (outputs below).
data "azurerm_client_config" "current" {}

data "azurerm_container_registry" "this" {
  name                = var.acr_name
  resource_group_name = data.azurerm_resource_group.this.name
}

# Web App and server names are global in Azure: a stable random suffix keeps them unique.
resource "random_string" "suffix" {
  length  = 5
  upper   = false
  special = false
}

module "platform" {
  source = "../../modules/platform"

  plan_name           = "asp-alivia-${local.environment}"
  app_name            = "app-alivia-${local.environment}-${random_string.suffix.result}"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = data.azurerm_resource_group.this.location
  sku_name            = var.app_service_sku
  acr_id              = data.azurerm_container_registry.this.id
  acr_login_server    = data.azurerm_container_registry.this.login_server
  tags                = local.tags
}

module "postgres" {
  source = "../../modules/postgres"

  server_name         = "psql-alivia-${local.environment}-${random_string.suffix.result}"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = data.azurerm_resource_group.this.location
  postgres_version    = var.postgres_version
  sku_name            = var.postgres_sku_name
  allowed_ips         = distinct(concat(module.platform.outbound_ips, var.operator_ips))
  tags                = local.tags
}

module "mongodb_atlas" {
  source = "../../modules/mongodb-atlas"

  organization_id = var.atlas_organization_id
  project_name    = "alivia-${local.environment}"
  cluster_name    = "alivia-${local.environment}"
  instance_size   = var.atlas_instance_size
  region_name     = var.atlas_region
  allowed_ips     = distinct(concat(module.platform.outbound_ips, var.operator_ips))
  tags            = local.tags
}

# The identity the GitHub workflow signs in as (OIDC, no secret). It can push to the registry and change this
# environment's Web App, and only jobs that run in this GitHub environment can use it.
module "deploy_identity" {
  source = "../../modules/deploy-identity"

  name                = "id-alivia-${local.environment}-deploy"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = data.azurerm_resource_group.this.location
  github_repository   = var.github_repository
  github_environment  = local.environment
  acr_id              = data.azurerm_container_registry.this.id
  webapp_id           = module.platform.app_id
  tags                = local.tags
}

# What deploy.sh and the GitHub environment need. The sensitive ones are read once with `terraform output -raw` and
# stored as GitHub secrets (see the deployment document); they are never printed by a plain `terraform output`.

output "resource_group_name" {
  description = "AZURE_RESOURCE_GROUP (GitHub variable of the environment)."
  value       = data.azurerm_resource_group.this.name
}

output "acr_name" {
  description = "ACR_NAME (GitHub variable)."
  value       = data.azurerm_container_registry.this.name
}

output "webapp_name" {
  description = "AZURE_WEBAPP_NAME (GitHub variable of the environment)."
  value       = module.platform.app_name
}

output "webapp_id" {
  description = "Resource id of the Web App (scope of the Website Contributor role of the deployment identity)."
  value       = module.platform.app_id
}

output "deploy_identity_client_id" {
  description = "AZURE_CLIENT_ID (GitHub variable of the environment). An identifier, not a secret."
  value       = module.deploy_identity.client_id
}

output "azure_tenant_id" {
  description = "AZURE_TENANT_ID (GitHub variable of the environment)."
  value       = data.azurerm_client_config.current.tenant_id
}

output "azure_subscription_id" {
  description = "AZURE_SUBSCRIPTION_ID (GitHub variable of the environment)."
  value       = data.azurerm_client_config.current.subscription_id
}

output "default_hostname" {
  description = "Host name of the API. Its URL is https://<default_hostname>."
  value       = module.platform.default_hostname
}

output "postgres_fqdn" {
  description = "Host name of the PostgreSQL server."
  value       = module.postgres.fqdn
}

output "postgres_jdbc_url" {
  description = "DB_URL (GitHub variable of the environment)."
  value       = module.postgres.jdbc_url
}

output "postgres_administrator_login" {
  description = "DB_USERNAME (GitHub variable of the environment)."
  value       = module.postgres.administrator_login
}

output "postgres_administrator_password" {
  description = "DB_PASSWORD (GitHub secret of the environment)."
  value       = module.postgres.administrator_password
  sensitive   = true
}

output "mongodb_connection_string" {
  description = "MONGODB_URI (GitHub secret of the environment)."
  value       = module.mongodb_atlas.connection_string
  sensitive   = true
}

output "mongodb_database" {
  description = "MONGODB_DATABASE (GitHub variable of the environment)."
  value       = module.mongodb_atlas.database_name
}
