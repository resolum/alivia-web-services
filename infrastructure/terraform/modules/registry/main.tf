variable "name" {
  description = "Name of the registry. Globally unique, 5 to 50 lowercase letters and digits."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]{5,50}$", var.name))
    error_message = "The registry name must be 5 to 50 lowercase letters and digits."
  }
}

variable "resource_group_name" {
  description = "Resource group that holds the registry."
  type        = string
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "sku" {
  description = "Registry SKU."
  type        = string
  default     = "Basic"
}

variable "tags" {
  description = "Tags for every resource."
  type        = map(string)
  default     = {}
}

# The admin user stays off: the Web Apps pull with their managed identity (AcrPull) and the deployment
# identity pushes with the AcrPush role.
resource "azurerm_container_registry" "this" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = var.sku
  admin_enabled       = false
  tags                = var.tags
}

output "id" {
  description = "Resource id of the registry (scope of the AcrPull and AcrPush roles)."
  value       = azurerm_container_registry.this.id
}

output "name" {
  description = "Name of the registry."
  value       = azurerm_container_registry.this.name
}

output "login_server" {
  description = "Host name of the registry, for example acralivia1a2b3.azurecr.io."
  value       = azurerm_container_registry.this.login_server
}
