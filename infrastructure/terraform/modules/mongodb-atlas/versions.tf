# mongodb/mongodbatlas is not in the hashicorp namespace, so the module has to say where it comes from.
terraform {
  required_providers {
    mongodbatlas = {
      source = "mongodb/mongodbatlas"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}
