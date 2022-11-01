resource "azurerm_container_registry" "acr" {
  count                    = var.acr_count ? 1 : 0
  name                     = var.acr_name
  resource_group_name      = var.resource_group_name
  location                 = var.location
  sku                      = var.acr_sku
  admin_enabled            = false
}