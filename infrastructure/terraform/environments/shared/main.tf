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
  }
}

# The subscription comes from ARM_SUBSCRIPTION_ID (or az login). No provider is registered automatically: the three
# this configuration needs are registered once by hand (see the README), so a subscription that does not allow
# registering providers is not an obstacle.
provider "azurerm" {
  features {}
  resource_provider_registrations = "none"
}

variable "location" {
  description = "Azure region of every resource (the same one for the API, the registry and PostgreSQL)."
  type        = string
  default     = "brazilsouth"
}

variable "resource_group_name" {
  description = "The only resource group of the project. The staging and production configurations read it."
  type        = string
  default     = "rg-alivia"
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

# The registry name is global in Azure and has to be unique: a stable random suffix does that.
resource "random_string" "suffix" {
  length  = 5
  upper   = false
  special = false
}

resource "azurerm_resource_group" "this" {
  name     = var.resource_group_name
  location = var.location
  tags     = var.tags
}

# One registry for both environments: the image that was tested in staging is the one promoted to production.
module "registry" {
  source = "../../modules/registry"

  name                = "acralivia${random_string.suffix.result}"
  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
  tags                = merge(var.tags, { environment = "shared" })
}

output "resource_group_name" {
  description = "Resource group (RESOURCE_GROUP_NAME of the staging and production configurations)."
  value       = azurerm_resource_group.this.name
}

output "acr_name" {
  description = "Registry name (ACR_NAME of the staging and production configurations and a GitHub variable)."
  value       = module.registry.name
}

output "acr_login_server" {
  description = "Registry host name."
  value       = module.registry.login_server
}

output "acr_id" {
  description = "Registry resource id (scope of the AcrPush role of the deployment identity)."
  value       = module.registry.id
}
