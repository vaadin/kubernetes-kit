output "aks_name" {
  value = azurerm_kubernetes_cluster.aks.name
}

output "kubelet_identity" {
  value = azurerm_kubernetes_cluster.aks.kubelet_identity[0].object_id
}

output "cluster_host" {
  value = azurerm_kubernetes_cluster.aks.kube_config.0.host
}

output "client_key" {
  value = azurerm_kubernetes_cluster.aks.kube_config.0.client_key
}

output "client_certificate" {
  value = azurerm_kubernetes_cluster.aks.kube_config.0.client_certificate
}

output "cluster_ca_certificate" {
  value = azurerm_kubernetes_cluster.aks.kube_config.0.cluster_ca_certificate
}

output "kube_config" {
  value = azurerm_kubernetes_cluster.aks.kube_config_raw
}

output "azure_keyvault_secrets_provider_id" {
  value = azurerm_kubernetes_cluster.aks.key_vault_secrets_provider[0].secret_identity.0.object_id
}

output "node_resource_group" {
  value = azurerm_kubernetes_cluster.aks.node_resource_group
}