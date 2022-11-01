resource "azurerm_key_vault" "kv" {
    name                        = "kv-${var.application}-${var.environment}"
    location                    = var.location
    resource_group_name         = var.resource_group_name
    tenant_id                   = var.tenantid
    soft_delete_retention_days  = 60
    sku_name                    = "standard"
}

resource "azurerm_key_vault_access_policy" "kubernetes_secrets" {
    key_vault_id = azurerm_key_vault.kv.id
    tenant_id = var.tenantid
    object_id = var.kube_secrets_identity

    key_permissions = [
        "Get",
    ]

    secret_permissions = [
        "Get",
    ]

    certificate_permissions = [
        "Get",
    ]
}
