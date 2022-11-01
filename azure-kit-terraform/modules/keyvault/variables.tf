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
variable "tenantid" {
  description = "the azure AD tenant ID"
}
variable "kube_secrets_identity" {
  type        = string
  description = "The kube secrets provider principal object ID"
}
