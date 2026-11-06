variable "plan_name" {
  description = "Name of the App Service plan."
  type        = string
}

variable "app_name" {
  description = "Name of the Web App. Globally unique: it is the first part of its host name."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group that holds the plan and the Web App."
  type        = string
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "sku_name" {
  description = "App Service plan SKU. The free tier (F1) cannot run custom containers, so the minimum is B1."
  type        = string
  default     = "B1"
}

variable "acr_id" {
  description = "Resource id of the registry the Web App pulls from."
  type        = string
}

variable "acr_login_server" {
  description = "Host name of that registry."
  type        = string
}

variable "initial_image" {
  description = <<-EOT
    Image (name:tag) the Web App is created with. It does not need to exist yet: deploy.sh sets the real one on every
    deployment, and Terraform ignores later changes to it.
  EOT
  type        = string
  default     = "alivia-api:initial"
}

variable "health_check_path" {
  description = "Path App Service probes to decide whether an instance is healthy."
  type        = string
  default     = "/actuator/health"
}

variable "tags" {
  description = "Tags for every resource."
  type        = map(string)
  default     = {}
}

resource "azurerm_service_plan" "this" {
  name                = var.plan_name
  resource_group_name = var.resource_group_name
  location            = var.location
  os_type             = "Linux"
  sku_name            = var.sku_name
  tags                = var.tags
}

resource "azurerm_linux_web_app" "api" {
  name                = var.app_name
  resource_group_name = var.resource_group_name
  location            = var.location
  service_plan_id     = azurerm_service_plan.this.id

  https_only              = true
  client_affinity_enabled = false

  # FTP deployment is never used. The SCM site keeps its basic authentication because the log stream needs it.
  ftp_publish_basic_authentication_enabled = false

  identity {
    type = "SystemAssigned"
  }

  site_config {
    always_on           = true
    ftps_state          = "Disabled"
    http2_enabled       = true
    minimum_tls_version = "1.2"

    health_check_path                 = var.health_check_path
    health_check_eviction_time_in_min = 5

    # The Web App pulls from the registry with its own identity (see the AcrPull assignment below): no password.
    container_registry_use_managed_identity = true

    application_stack {
      docker_registry_url = "https://${var.acr_login_server}"
      docker_image_name   = var.initial_image
    }
  }

  # App Service keeps these on its own file system; there is no other logging service (see the deployment document).
  logs {
    application_logs {
      file_system_level = "Information"
    }

    http_logs {
      file_system {
        retention_in_days = 7
        retention_in_mb   = 35
      }
    }
  }

  tags = var.tags

  lifecycle {
    ignore_changes = [
      # deploy.sh owns the configuration of the application and the image that runs: applying Terraform again must not
      # undo what the last deployment set.
      app_settings,
      site_config[0].application_stack,
    ]
  }
}

resource "azurerm_role_assignment" "acr_pull" {
  scope                = var.acr_id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_linux_web_app.api.identity[0].principal_id
  principal_type       = "ServicePrincipal"
}

output "app_name" {
  description = "Name of the Web App."
  value       = azurerm_linux_web_app.api.name
}

output "app_id" {
  description = "Resource id of the Web App (scope of the role of the deployment identity)."
  value       = azurerm_linux_web_app.api.id
}

output "default_hostname" {
  description = "Host name of the Web App."
  value       = azurerm_linux_web_app.api.default_hostname
}

output "outbound_ips" {
  description = "Every address the Web App may call from. The databases accept only these."
  value       = azurerm_linux_web_app.api.possible_outbound_ip_address_list
}
