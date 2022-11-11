# Terraform Backend State
terraform {
    required_providers {
        azurerm = {
            source = "hashicorp/azurerm"
            version = ">=3.29.0, <4.0.0"
        }
    }

  # The backend resources needs to be created in advance.
  # Use ./create_terraform_storage.sh script
  # Note that storage_account_name is globally unique for all Azure users and thus usually contains a random string part.
  backend "azurerm" {
    resource_group_name  = "Terraform-ResourceGroup"
#    storage_account_name = "<FIXME>"
    container_name       = "tfstate"
    key                  = "terraform.tfstate"
  }
}

# Providers start here
# =======================
provider "azurerm" {
  features {}
}

provider "helm" {
  kubernetes {
    host                   = module.aks.cluster_host
    client_key             = base64decode(module.aks.client_key)
    client_certificate     = base64decode(module.aks.client_certificate)
    cluster_ca_certificate = base64decode(module.aks.cluster_ca_certificate)
  }
}

data "azurerm_subscription" "current" {}
data "azurerm_client_config" "current" {}

# Resouces start here
# ======================

# Resource Group
resource "azurerm_resource_group" "resource_group" {
  name     = "rg-${var.application}-${var.environment}-${var.location}"
  location = var.location
  tags = {
    "Application" = var.application
    "Environment" = var.environment
  }
}

# Network
module "network" {
  source              = "./modules/network"
  application         = var.application
  environment         = var.environment
  location            = var.location
  resource_group_name = azurerm_resource_group.resource_group.name
  address_space       = local.network["address-space"]
  address_prefix_aks  = local.network["aks"]
}

# Acr
module "acr" {
  source              = "./modules/acr"
  acr_count           = var.acr_count
  acr_name            = "acr${var.application}${var.environment}${var.location}"
  acr_sku             = var.acr_sku
  location            = var.location
  resource_group_name = azurerm_resource_group.resource_group.name
}

# Azure Kubernetes Services
module "aks" {
  source                      = "./modules/aks"
  application                 = var.application
  cluster_name                = "aks-${var.application}-${var.environment}-${var.location}"
  environment                 = var.environment
  kubernetes_version          = var.kubernetes_version
  kubernetes_upgrade_channel  = var.kubernetes_upgrade_channel
  resource_group_name         = azurerm_resource_group.resource_group.name
  location                    = var.location
  enable_private_cluster      = var.enable_private_cluster
  aks_subnet_id               = module.network.aks_subnet_id


  default_node_pool = [
    {
      name                = "nodepool"
      node_count          = var.node_count
      vm_size             = var.vm_size
      os_disk_size_gb     = var.os_disk_size_gb
      max_pods            = var.max_pods
      type                = "VirtualMachineScaleSets"
      enable_auto_scaling = var.enable_auto_scaling
      min_count           = var.min_count
      max_count           = var.max_count
      max_pods            = var.max_pods
      availability_zones  = ["1", "2", "3"]
    }
  ]

  network_profile = {
    network_plugin     = var.network_plugin
    dns_service_ip     = cidrhost(local.network["aks_service"], 10)
    docker_bridge_cidr = "172.17.0.1/16"
    service_cidr       = local.network["aks_service"]
    load_balancer_sku  = "standard"
    network_policy     = "calico"
  }
}

# Keyvault
module "keyvault" {
  source = "./modules/keyvault"
  application         = var.application
  environment         = var.environment
  location            = var.location
  resource_group_name = azurerm_resource_group.resource_group.name
  tenantid            = data.azurerm_client_config.current.tenant_id
  kube_secrets_identity  = module.aks.azure_keyvault_secrets_provider_id
}

# Nginx ingress
module "nginx" {
  source          = "./modules/nginx-ingress"
  depends_on      = [module.aks]
  replicaCount    = var.nginx_replicas
  nginx_version   = var.nginx_version
  nginx_create_static = var.nginx_create_static
  application         = var.application
  environment         = var.environment
  location            = var.location
  resource_group_name = module.aks.node_resource_group
}

# cert-manager
module "cert-manager" {
  source = "./modules/cert-manager"
  depends_on      = [module.aks]
  cert-manager-version = var.cert-manager-version
  deploy-cert-manager = var.deploy-cert-manager
}