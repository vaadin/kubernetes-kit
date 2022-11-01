output "aks_subnet_id" {
  value = azurerm_subnet.aks.id
}

output "vnet_id" {
  value = azurerm_virtual_network.aks.id
}

output "vnet_name" {
  value = azurerm_virtual_network.aks.name
}