# Azure Kit Terraform
## Requirements
* Azure subscription
* Terraform: https://www.terraform.io/downloads
* Azure cli: https://learn.microsoft.com/en-us/cli/azure/install-azure-cli
## Prepare environment for terraform state

Az CLI login:

    $ az login # Login to azure
    $ az account set --subscription <id>
    $ az account show # Verify you are connected to correct subscription.

Create backing storage for terraform state files using provided script:

    $ ./create_terraform_storage.sh
Check for the output about created storageaccount name.
    
## Initialize terraform    
    $ terraform init

    Initializing modules...
    - acr in modules/acr
    - aks in modules/aks
    - keyvault in modules/keyvault
    - network in modules/network

    Initializing the backend...
    storage_account_name
    The name of the storage account.
    
    Enter a value: <enter storageaccount name>

    Successfully configured the backend "azurerm"! Terraform will automatically
    use this backend unless the backend configuration changes.

    Initializing provider plugins...
    ...
## Configure terraform for your setup
* Edit /settings.tf
* Edit /variables.tf

## Run terraform plan
    $ terraform plan -out=plan.out
Verify the output on screen and proceed if proposed setup is good.

## Run terraform apply
    $ terraform apply "plan.out"
    azurerm_resource_group.resource_group: Creating...
    ...
    Apply complete! Resources: 8 added, 0 changed, 0 destroyed.


## Use kubectl to connect to created kubernetes cluster
    $ export KUBECONFIG="./kubeconfig"
    $ kubectl get nodes
    NAME                               STATUS   ROLES   AGE     VERSION
    aks-nodepool-31060480-vmss000000   Ready    agent   3m   v1.23.12
    aks-nodepool-31060480-vmss000001   Ready    agent   3m   v1.23.12


Get the ingress ip

    $ kubectl -n kube-system get service ingress-nginx-controller 
    NAME                                 TYPE           CLUSTER-IP      EXTERNAL-IP    PORT(S)                      AGE
    ingress-nginx-controller             LoadBalancer   10.240.97.40    a.b.c.d   80:30799/TCP,443:31928/TCP   106m

## Cleanup terraform deployed items
    $ terraform destroy
You need to remove the storageccount and resourcegroup "Terraform-ResourceGroup" created by sh script manually.

## TLS
To help using letsencrypt there is an option in terraform variables to enable letsencrypt certmanager in cluster.
After certmanager is installed you still need to create the cluster issuer separately: [https://learn.microsoft.com/en-us/azure/aks/ingress-tls?tabs=azure-cli#create-a-ca-cluster-issuer](https://learn.microsoft.com/en-us/azure/aks/ingress-tls?tabs=azure-cli#create-a-ca-cluster-issuer).
After this the next steps are to configure your ingress to use these certificates. You can follow the same microsoft [guide](https://learn.microsoft.com/en-us/azure/aks/ingress-tls?tabs=azure-cli#update-your-ingress-routes) for this. 

If you plan to use normal certificates it's best to follow guide by Microsoft: [learn.microsoft.com/en-us/azure/aks/csi-secrets-store-nginx-tls](learn.microsoft.com/en-us/azure/aks/csi-secrets-store-nginx-tls)