# AKS Network
resource "azurerm_virtual_network" "aks" {
  name                = "vnet-${var.application}-${var.environment}-${var.location}"
  location            = var.location
  resource_group_name = var.resource_group_name
  address_space       = [var.address_space]
}

# AKS Subnet
resource "azurerm_subnet" "aks" {
  name                 = "snet-${var.application}-${var.environment}-${var.location}"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.aks.name
  address_prefixes     = [var.address_prefix_aks]
}