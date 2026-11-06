variable "server_name" {
  description = "Name of the server. Globally unique: it is the first part of its host name."
  type        = string
}

variable "database_name" {
  description = "Database the application uses. Flyway builds its schema on the first start."
  type        = string
  default     = "alivia"
}

variable "resource_group_name" {
  description = "Resource group that holds the server."
  type        = string
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "postgres_version" {
  description = <<-EOT
    PostgreSQL major version. The migrations were verified on 17 and 18, so 17 is the floor. Use the highest one the
    region offers (az postgres flexible-server list-skus -l <region>).
  EOT
  type        = string
  default     = "17"
}

variable "sku_name" {
  description = "Server SKU. B_Standard_B1ms is the smallest Burstable one."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "storage_mb" {
  description = "Storage in MB."
  type        = number
  default     = 32768
}

variable "backup_retention_days" {
  description = "Days the automatic backups are kept (point-in-time restore)."
  type        = number
  default     = 7
}

variable "administrator_login" {
  description = "Administrator of the server. The application connects with it: Flyway has to create and alter tables."
  type        = string
  default     = "alivia_admin"
}

variable "allowed_ips" {
  description = "Addresses the server accepts: the Web App's outbound ones, plus the operator's while checking the connection."
  type        = list(string)
}

variable "tags" {
  description = "Tags for every resource."
  type        = map(string)
  default     = {}
}

# Letters and digits only: the password goes into a JDBC URL and an environment variable, and a special character
# would need escaping in both.
resource "random_password" "administrator" {
  length  = 32
  special = false
}

resource "azurerm_postgresql_flexible_server" "this" {
  name                = var.server_name
  resource_group_name = var.resource_group_name
  location            = var.location
  version             = var.postgres_version

  administrator_login    = var.administrator_login
  administrator_password = random_password.administrator.result

  sku_name              = var.sku_name
  storage_mb            = var.storage_mb
  auto_grow_enabled     = false
  backup_retention_days = var.backup_retention_days

  geo_redundant_backup_enabled  = false
  public_network_access_enabled = true

  tags = var.tags

  lifecycle {
    ignore_changes = [
      # Azure picks the availability zone when none is asked for and reports it back.
      zone,
    ]
  }
}

resource "azurerm_postgresql_flexible_server_database" "this" {
  name      = var.database_name
  server_id = azurerm_postgresql_flexible_server.this.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# One rule per address. The addresses come from the Web App, so the first apply has to create the Web App before the
# rest (see the README).
resource "azurerm_postgresql_flexible_server_firewall_rule" "allowed" {
  for_each = toset(var.allowed_ips)

  name             = "allow-${replace(each.value, ".", "-")}"
  server_id        = azurerm_postgresql_flexible_server.this.id
  start_ip_address = each.value
  end_ip_address   = each.value
}

output "firewall_rule_names" {
  description = "Names of the firewall rules, one per allowed address."
  value       = sort([for rule in azurerm_postgresql_flexible_server_firewall_rule.allowed : rule.name])
}

output "fqdn" {
  description = "Host name of the server."
  value       = azurerm_postgresql_flexible_server.this.fqdn
}

output "database_name" {
  description = "Database the application uses."
  value       = azurerm_postgresql_flexible_server_database.this.name
}

output "administrator_login" {
  description = "Administrator of the server (DB_USERNAME)."
  value       = azurerm_postgresql_flexible_server.this.administrator_login
}

output "administrator_password" {
  description = "Password of the administrator (DB_PASSWORD)."
  value       = random_password.administrator.result
  sensitive   = true
}

output "jdbc_url" {
  description = "Connection URL for the application (DB_URL). SSL is required by the server."
  value       = "jdbc:postgresql://${azurerm_postgresql_flexible_server.this.fqdn}:5432/${azurerm_postgresql_flexible_server_database.this.name}?sslmode=require"
}
