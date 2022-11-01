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
  description = "Default location"
}

variable "enable_private_cluster" {
  type        = bool
  description = "flag to deploy a private cluster"
}

variable "cluster_name" {
  type        = string
  description = "Default AKS cluster name"
}

variable "resource_group_name" {
  type        = string
  description = "Default resource group"
}

variable "aks_subnet_id" {
  type        = string
  description = "AKS network subnet id"
}

variable "kubernetes_version" {
  type        = string
  description = "Kubernetes version"
}

variable "kubernetes_upgrade_channel" {
  type        = string
  description = "Kubernetes upgrade channel"
}

variable "master_api_authorized_ip_ranges" {
  # default     = ["127.0.0.1/32"
  #               ]
  default = []
  description = "Limit access to kubernetes master api by network"
}

variable "network_profile" {
  type = map(string)

  default = {
    network_plugin     = "azure"
    dns_service_ip     = "10.0.0.10"
    docker_bridge_cidr = "172.17.0.1/16"
    service_cidr       = "10.0.0.0/16"
    load_balancer_sku  = "standard"
  }
}

variable "default_node_pool" {
  type = list(object({
    name                = string
    node_count          = number
    vm_size             = string
    os_disk_size_gb     = number
    max_pods            = number
    type                = string
    enable_auto_scaling = bool
    min_count           = number
    max_count           = number
    availability_zones  = list(string)
  }))
}

# The AAD RBAC Application must be created prior to this being enabled.
# https://docs.microsoft.com/en-us/azure/aks/azure-ad-integration-cli
variable "aad_rbac_enabled" {
  type        = string
  default     = 1
  description = "A flag to turn on aad rbac for AKS"
}

variable "managed_aad" {
  type        = string
  default     = 1
  description = "A flag to turn on managed aad rbac for AKS"
}

variable "admin_group_object_ids" {
  type        = list(string)
  default     = []
  description = "Used only with managed AAD integration enabled. List of AAD group object IDs to set as kubernetes admin"
}