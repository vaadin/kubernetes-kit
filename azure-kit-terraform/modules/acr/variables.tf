variable "acr_count" {
  type        = string
  description = "A flag to create or destroy ACR"
}

variable "acr_name" {
  type        = string
  description = "ACR name"
}

variable "acr_sku" {
  type        = string
  description = "ACR SKU"
}

variable "location" {
  type        = string
  description = "Location"
}

variable "resource_group_name" {
  type        = string
  description = "Resource group name"
}