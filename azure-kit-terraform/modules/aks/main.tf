# https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs/resources/kubernetes_cluster
resource "azurerm_kubernetes_cluster" "aks" {
  name                                = var.cluster_name
  location                            = var.location
  resource_group_name                 = var.resource_group_name
  dns_prefix                          = "dns-${var.application}-${var.environment}-${var.location}"
  kubernetes_version                  = var.kubernetes_version
  automatic_channel_upgrade           = var.kubernetes_upgrade_channel
  node_resource_group                 = "AKS_${var.resource_group_name}"
  private_cluster_enabled             = var.enable_private_cluster
  azure_policy_enabled                = true
  api_server_authorized_ip_ranges     = var.master_api_authorized_ip_ranges

  dynamic "default_node_pool" {
    iterator = pool
    for_each = var.default_node_pool

    content {
      name                = pool.value.name
      node_count          = pool.value.node_count
      vm_size             = pool.value.vm_size
      os_disk_size_gb     = pool.value.os_disk_size_gb
      min_count           = lookup(pool.value, "min_count", null)
      max_count           = lookup(pool.value, "max_count", null)
      max_pods            = lookup(pool.value, "max_pods", null)
      type                = lookup(pool.value, "type", null)
      enable_auto_scaling = lookup(pool.value, "enable_auto_scaling", null)
      node_taints         = lookup(pool.value, "node_taints", null)
      vnet_subnet_id      = var.aks_subnet_id
    }
  }

  dynamic "network_profile" {
    iterator = net
    for_each = [var.network_profile]

    content {
      network_plugin     = lookup(net.value, "network_plugin", "azure")
      dns_service_ip     = lookup(net.value, "dns_service_ip", null)
      docker_bridge_cidr = lookup(net.value, "docker_bridge_cidr", null)
      service_cidr       = lookup(net.value, "service_cidr", null)
      load_balancer_sku  = lookup(net.value, "load_balancer_sku", null)
      network_policy     = lookup(net.value, "network_policy", null)
    }
  }

  # Managed Identity
  identity {
    type = "SystemAssigned"
  }

  key_vault_secrets_provider {
    secret_rotation_enabled = true
  }

}