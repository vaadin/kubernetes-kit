
resource "azurerm_public_ip" "nginx_static_ip" {
  count               = var.nginx_create_static ? 1 : 0
  name                = "pip-nginx-ingress-static-ip"
  resource_group_name = var.resource_group_name
  location            = var.location
  allocation_method   = "Static"
  sku                 = "Standard"
  tags = {
    environment = var.environment
  }
}

resource "helm_release" "nginx_ingress" {
  name       = "ingress-nginx"
  repository = "https://kubernetes.github.io/ingress-nginx"
  chart      = "ingress-nginx"
  namespace  = "kube-system"
  version    = var.nginx_version

  set {
    name  = "controller.replicaCount"
    value = var.replicaCount
  }
  set {
    name = "controller.service.annotations.\"service\\.beta\\.kubernetes\\.io/azure-load-balancer-health-probe-request-path\""
    value = "/healthz"
  }
  dynamic "set" {
    for_each = var.nginx_create_static ? [1] : []
    content {
      name = "controller.service.loadBalancerIP"
      value = azurerm_public_ip.nginx_static_ip[0].ip_address
    }
  }
}