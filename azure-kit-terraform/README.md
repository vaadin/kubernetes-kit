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

## Autoscaling
Theres different types of autoscaling possible:
- The horizontal pod autoscaler.
- Vertical Pod Autoscaling
- The cluster autoscaler.

### Horizoncal pod autoscaler
The [Horizontal Pod Autoscaler](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/) controls the scale of a Deployment and its ReplicaSet.
It is implemented as a Kubernetes API resource and a controller and can not be deployed with this terraform.
It is usually deployed together with the application as scaling is dependent on the load requirements on the application.

There is a [walkthrough example](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/) of using horizontal pod autoscaling.

### Vertical Pod Autoscaling
It tries to automatically set resource requests and limits on running containers based on past usage.
Currently its in preview: https://learn.microsoft.com/en-us/azure/aks/vertical-pod-autoscaler

### Cluster autoscaler
The [Azure cluster autoscaler](https://learn.microsoft.com/en-us/azure/aks/cluster-autoscaler) component can watch for pods in your cluster that can't be scheduled because of resource constraints.
When issues are detected, the number of nodes in a node pool is increased to meet the application demand.

To enable autoscaling with terraform you need to set up variables in variables.tf file:
- enable_auto_scaling: true
- min_count: Minimum number of nodes in cluster
- max_count: Maximum number of nodes in cluster

NOTE: There is by default quotas in azure on how many specific type resources you can have.
For example vCPU amounts can be limited to as low as 10 per subscription.
This will limit the cluster size unless you increase the quota.
These limits can usually be increased in the portal "Quotas" section.
https://learn.microsoft.com/en-us/azure/quotas/per-vm-quota-requests

More info regarding Microsoft Azure subscription limits: https://learn.microsoft.com/en-us/azure/azure-resource-manager/management/azure-subscription-service-limits
### Set Resource Requests and Limits
This different for Vertical Pod Autoscaling.

For scaling to work better you should use resource requests and limits in your application deployments.
There are lots of best practices guides out there, and it really depends on your application what is the best.
https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/

For example:

    containers:
    - name: prodcontainer1
      image: ubuntu
      resources:
        requests:
          memory: “64Mi”
          cpu: “300m”
        limits:                              
          memory: “128Mi”
Links:
- https://www.containiq.com/post/setting-and-rightsizing-kubernetes-resource-limits
- https://home.robusta.dev/blog/kubernetes-memory-limit
- https://home.robusta.dev/blog/stop-using-cpu-limits

## Multiple Kubernetes environments (Staging/Prod)
The best approach when deploying multiple kubernetes environments is to use separate directories for each environment.
Then you can modify the variables.tf file for each environment.

Each combination of variables: application, environment and location would result in a different environment.

It's also possible to have multiple subscriptions and keep most of the terraform variables identical.
With multiple subscriptions the storageaccounts cannot have the same name.