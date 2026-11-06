variable "organization_id" {
  description = "Id of the Atlas organization the project is created in."
  type        = string
}

variable "project_name" {
  description = "Name of the Atlas project. A project holds one free (M0) cluster at most, so every environment has its own."
  type        = string
}

variable "cluster_name" {
  description = "Name of the cluster."
  type        = string
}

variable "instance_size" {
  description = "Cluster tier. M0 is the free one; any other value creates a paid dedicated cluster."
  type        = string
  default     = "M0"
}

variable "region_name" {
  description = <<-EOT
    Atlas name of the Azure region (for example US_EAST for East US). M0 is only offered in some regions: the Atlas
    interface lists them when a free cluster is created on Azure.
  EOT
  type        = string
  default     = "US_EAST"
}

variable "database_name" {
  description = "Database the application uses; the application user only gets access to it."
  type        = string
  default     = "alivia_db"
}

variable "username" {
  description = "Database user of the application."
  type        = string
  default     = "alivia_app"
}

variable "allowed_ips" {
  description = "Addresses Atlas accepts: the Web App's outbound ones, plus the operator's while checking the connection."
  type        = list(string)
}

variable "tags" {
  description = "Tags for the project and the cluster."
  type        = map(string)
  default     = {}
}

locals {
  free = var.instance_size == "M0"
}

resource "mongodbatlas_project" "this" {
  name   = var.project_name
  org_id = var.organization_id
  tags   = var.tags
}

resource "mongodbatlas_advanced_cluster" "this" {
  project_id   = mongodbatlas_project.this.id
  name         = var.cluster_name
  cluster_type = "REPLICASET"

  # M0 has no backups and no termination protection; a paid cluster gets backups from Atlas.
  backup_enabled = !local.free
  tags           = var.tags

  replication_specs = [
    {
      region_configs = [
        {
          # A free cluster is a TENANT cluster hosted on the cloud it backs; a paid one is hosted on Azure directly.
          provider_name         = local.free ? "TENANT" : "AZURE"
          backing_provider_name = local.free ? "AZURE" : null
          region_name           = var.region_name
          priority              = 7
          electable_specs = {
            instance_size = var.instance_size
            node_count    = local.free ? null : 3
          }
        }
      ]
    }
  ]
}

# Letters and digits only: the password goes into a connection string and an environment variable.
resource "random_password" "application" {
  length  = 32
  special = false
}

resource "mongodbatlas_database_user" "application" {
  project_id         = mongodbatlas_project.this.id
  username           = var.username
  password           = random_password.application.result
  auth_database_name = "admin"

  roles {
    role_name     = "readWrite"
    database_name = var.database_name
  }

  scopes {
    name = mongodbatlas_advanced_cluster.this.name
    type = "CLUSTER"
  }
}

# One entry per address. The addresses come from the Web App, so the first apply has to create the Web App before the
# rest (see the README).
resource "mongodbatlas_project_ip_access_list" "allowed" {
  for_each = toset(var.allowed_ips)

  project_id = mongodbatlas_project.this.id
  ip_address = each.value
  comment    = "Web App outbound address"
}

output "database_name" {
  description = "Database the application uses (MONGODB_DATABASE)."
  value       = var.database_name
}

output "connection_string" {
  description = "Connection string for the application (MONGODB_URI)."
  value = format(
    "mongodb+srv://%s:%s@%s/%s?retryWrites=true&w=majority",
    mongodbatlas_database_user.application.username,
    random_password.application.result,
    trimprefix(mongodbatlas_advanced_cluster.this.connection_strings.standard_srv, "mongodb+srv://"),
    var.database_name,
  )
  sensitive = true
}
