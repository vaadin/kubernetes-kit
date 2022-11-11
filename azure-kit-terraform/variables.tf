
# Do we create Azure Container Registry?
variable "acr_count" {
  type        = string
  default     = 1
  description = "A flag to create or destroy ACR"
}

# SKU used for ACR
variable "acr_sku" {
  type        = string
  default     = "Standard"
  description = "ACR sku"
}

# Name of your application.
# Used in naming items in Azure
variable "application" {
  type        = string
  default     = "Vaadin"
  description = "Application name"
}

# Name of your environment.
# Free text name if you got multiple environments of the application
variable "environment" {
  type        = string
  default     = "azurekit"
  description = "Environment"
}

# Azure location to use
variable "location" {
  type        = string
  default     = "northeurope"
  description = "Location"
}

# Should this Kubernetes Cluster have its API server only exposed on internal IP addresses?
# if enabled you need to operate kubernetes from your azure setups internal addresses. (or VPN)
variable "enable_private_cluster" {
  type        = bool
  default     = false
  description = "flag to deploy a private cluster"
}

# https://learn.microsoft.com/en-us/azure/aks/supported-kubernetes-versions?tabs=azure-cli#aks-kubernetes-release-calendar
# $ az aks get-versions --location <location> --output table
# this version is not mandatory.
# If null, the latest recommended version will be used at provisioning time.
# Also automatic upgrades setting can affect this.
variable "kubernetes_version" {
  default = null
}

# Automatic channel for upgrades
# Possible values are patch, rapid, node-image, stable and none.
# https://learn.microsoft.com/en-us/azure/aks/auto-upgrade-cluster
variable "kubernetes_upgrade_channel" {
  default = "patch"
}


# If you need more advanced networking use "azure" network plugin
# https://learn.microsoft.com/en-us/azure/aks/concepts-network#azure-virtual-networks
variable "network_plugin" {
  type        = string
  description = "Network plugin to use in AKS: azure or kubenet."
  default     = "kubenet"
}


# How many nodes in kubernetes nodepool
# AKS Node Pool Reference
variable "node_count" {
  description = "The initial number of nodes which should exist in this Node Pool."
  default     = 2
}

# https://learn.microsoft.com/en-us/azure/aks/cluster-autoscaler
variable "enable_auto_scaling" {
  description = "Should the Kubernetes Auto Scaler be enabled for this Node Pool?"
  type    = bool
  default = false
}

# Used for autoscaling:
# Else must be null
variable "min_count" {
  description = "The minimum number of nodes which should exist in this Node Pool."
  default     = null
}
variable "max_count" {
  description = "The maximum number of nodes which should exist in this Node Pool."
  default = null
}

# https://learn.microsoft.com/fi-fi/azure/aks/configure-azure-cni#configure-maximum---new-clusters
variable "max_pods" {
  type    = number
  default = 110
}

# size of kubernetes node OS disk.
variable "os_disk_size_gb" {
  type    = number
  default = 128
}

# https://github.com/Azure/k8s-best-practices/blob/master/Cost_Optimization.md#node---vm-sizes
# https://learn.microsoft.com/en-us/azure/virtual-machines/sizes
# Standard_D2s_v5 = 2 cpu 8G RAM
# D – General purpose compute
# 2 – VM Size
# s – Premium Storage capable
# v5 – version
variable "vm_size" {
  default = "Standard_D2s_v5"
}


variable "nginx_replicas" {
  type    = number
  default = 2
}

# https://github.com/kubernetes/ingress-nginx/blob/main/charts/ingress-nginx/CHANGELOG.md
variable "nginx_version" {
  type    = string
  default = "4.3.0"
}

# Allocate static ip for nginx ingress?
variable "nginx_create_static" {
  description = "Should we allocate static ip for nginx ingress?"
  type    = bool
  default = true
}

# For LetsEncrypt SSL
# https://cert-manager.io/docs/configuration/acme/
# if you want to use cert-manager for letsencrypt this deploys it for you.
variable "deploy-cert-manager" {
  description = "Should we deploy cert-manager to kubernetes cluster?"
  type = bool
  default = true
}

# https://cert-manager.io/docs/release-notes/
variable "cert-manager-version" {
  description = "Helm chart version to use for cert-manager"
  default = "1.10.0"
}