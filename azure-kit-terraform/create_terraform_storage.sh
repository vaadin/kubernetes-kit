#!/bin/bash
# https://learn.microsoft.com/en-us/azure/developer/terraform/store-state-in-azure-storage?tabs=azure-cli

set -e

RESOURCE_GROUP_NAME=Terraform-ResourceGroup
STORAGE_ACCOUNT_NAME=myterraformtfstate$RANDOM
CONTAINER_NAME=tfstate
LOCATION=northeurope


# Create resource group
az group create --name $RESOURCE_GROUP_NAME --location $LOCATION --output none

# Create storage account
az storage account create --resource-group $RESOURCE_GROUP_NAME --name $STORAGE_ACCOUNT_NAME --sku Standard_LRS --encryption-services blob --output none 

# Create blob container
az storage container create --name $CONTAINER_NAME --account-name $STORAGE_ACCOUNT_NAME --output none --auth-mode login

echo "Created storageaccount $STORAGE_ACCOUNT_NAME"
echo "Give this storageaccount name when running terraform init the first time."
