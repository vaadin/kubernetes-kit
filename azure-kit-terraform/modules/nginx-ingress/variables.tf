variable "replicaCount" {
  description = "Nginx ingress replicacount"
}

variable "nginx_version" {
  description = "Nginx ingress helm chart version"
}

variable "nginx_create_static" {
  description = "Static ip to use with nginx"
}

variable "application" {
  type        = string
  description = "Application name"
}
variable "environment" {
  type        = string
  description = "Environment name"
}
variable "location" {
  type        = string
  description = "Target location"
}
variable "resource_group_name" {
  type        = string
  description = "Target resource group name"
}
