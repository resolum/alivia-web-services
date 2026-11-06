# Runs without credentials: every provider is simulated. It checks how the configuration behaves, not Azure itself.
#
# The databases accept only the outbound addresses of the Web App, and Azure reports them once the Web App exists. A plan
# of everything at once fails with "Invalid for_each argument" (the firewall rules cannot be known yet), so the first
# apply creates the Web App before the rest, exactly as the two runs below do.

mock_provider "azurerm" {
  mock_data "azurerm_resource_group" {
    defaults = {
      id       = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-alivia"
      name     = "rg-alivia"
      location = "brazilsouth"
    }
  }

  mock_data "azurerm_container_registry" {
    defaults = {
      id           = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-alivia/providers/Microsoft.ContainerRegistry/registries/acralivia12345"
      name         = "acralivia12345"
      login_server = "acralivia12345.azurecr.io"
    }
  }

  # The provider checks the format of the ids it receives, so the simulated ones have to be valid.
  mock_resource "azurerm_service_plan" {
    defaults = {
      id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-alivia/providers/Microsoft.Web/serverFarms/asp-alivia-production"
    }
  }

  mock_resource "azurerm_linux_web_app" {
    defaults = {
      id                                = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-alivia/providers/Microsoft.Web/sites/app-alivia-production"
      default_hostname                  = "app-alivia-production.azurewebsites.net"
      possible_outbound_ip_address_list = ["20.0.0.1", "20.0.0.2", "20.0.0.2"]
    }
  }

  mock_resource "azurerm_postgresql_flexible_server" {
    defaults = {
      id   = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg-alivia/providers/Microsoft.DBforPostgreSQL/flexibleServers/psql-alivia-production"
      fqdn = "psql-alivia-production.postgres.database.azure.com"
    }
  }
}

mock_provider "mongodbatlas" {
  mock_resource "mongodbatlas_advanced_cluster" {
    defaults = {
      connection_strings = {
        standard_srv = "mongodb+srv://alivia-production.abc12.mongodb.net"
      }
    }
  }
}

mock_provider "random" {}

variables {
  acr_name              = "acralivia12345"
  atlas_organization_id = "000000000000000000000000"
}

run "first_apply_creates_the_web_app" {
  command = apply

  plan_options {
    target = [module.platform]
  }

  assert {
    condition     = startswith(module.platform.app_name, "app-alivia-production-")
    error_message = "The Web App name carries the environment and a unique suffix."
  }
}

run "second_apply_creates_the_databases_with_one_rule_per_distinct_address" {
  command = apply

  assert {
    condition     = module.postgres.firewall_rule_names == tolist(["allow-20-0-0-1", "allow-20-0-0-2"])
    error_message = "One firewall rule per distinct address of the Web App, and nothing else (got ${jsonencode(module.postgres.firewall_rule_names)})."
  }

  assert {
    condition     = endswith(module.postgres.jdbc_url, "/alivia?sslmode=require")
    error_message = "The JDBC URL names the database and requires SSL."
  }

  assert {
    condition     = strcontains(module.mongodb_atlas.connection_string, "@alivia-production.abc12.mongodb.net/alivia_db?retryWrites=true&w=majority")
    error_message = "The MongoDB connection string points at the cluster and the application database."
  }

  assert {
    condition     = startswith(module.mongodb_atlas.connection_string, "mongodb+srv://alivia_app:")
    error_message = "The application connects with its own user, not an administrator."
  }
}
