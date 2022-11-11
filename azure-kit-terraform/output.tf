output "aks_cluster" {
  value       = module.aks.aks_name
  description = "The name of the Azure Kubernetes Cluster"
}

resource "local_file" "kubeconfig" {
  depends_on      = [module.aks]
  filename        = "./kubeconfig"
  content         = module.aks.kube_config
  file_permission = 0600
}
