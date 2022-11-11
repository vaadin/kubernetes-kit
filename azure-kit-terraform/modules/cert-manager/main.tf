resource "helm_release" "cert-manager" {
  count               = var.deploy-cert-manager ? 1 : 0
  name              = "cert-manager"
  repository        = "https://charts.jetstack.io"
  chart             = "cert-manager"
  version           = var.cert-manager-version

  namespace         = "cert-manager"
  create_namespace  = "true"

  set {
    name  = "installCRDs"
    value = "true"
  }
}