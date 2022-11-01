# Network settings for kubernetes internal networks
# if you are using azure network type you need to to plan this networking.
# These IP addresses must be unique across your network space, and must be planned in advance.
# https://learn.microsoft.com/en-us/azure/aks/configure-azure-cni#plan-ip-addressing-for-your-cluster
locals {
  network = {
    address-space = "10.224.0.0/16" # vnet address-space 65,536 IP's in range 10.224.0.0 - 10.224.255.255
    aks           = "10.224.0.0/19" # aks subnet 8,192 IP's in range 10.224.0.1 - 10.224.31.254
    aks_service   = "10.240.0.0/16" # Service address range should not be in previous defined networks.
  }
}

