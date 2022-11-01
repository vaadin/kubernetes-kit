variable "address_space" {
  description = "Vnet CIDR"
}

variable "address_prefix_aks" {
  description = "AKS subnet CIDR"
}

variable "application" {
  type        = string
  description = "Application name"
}

variable "environment" {
  type        = string
  description = "Prefix used for the environment"
}

variable "location" {
  type        = string
  description = "Target location"
}

variable "resource_group_name" {
  type        = string
  description = "Target resource group name"
}


