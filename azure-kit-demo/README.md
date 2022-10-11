# Azure Kit Demo

## Run the demo

To run the demo you first need to have a local Kubernetes cluster up and running.
You can find recommended tools here: https://kubernetes.io/docs/tasks/tools/

To deploy the demo application into your cluster, follow these steps:

1. Build the application JAR:
```
# mvn clean package -Pproduction
```
2. Create or update the Docker image:
```
# docker build -t azure-kit-demo:latest .
```
3. Deploy the image into the cluster:
```
# kubectl apply -f kubernetes.yaml
```

You should now see 4 pods running, e.g.

```
# kubectl get pods
NAME                                 READY   STATUS    RESTARTS      AGE
azure-kit-demo-f87bfcbb4-5qjml       1/1     Running   0             22s
azure-kit-demo-f87bfcbb4-czkzr       1/1     Running   0             22s
azure-kit-demo-f87bfcbb4-gjqw6       1/1     Running   0             22s
azure-kit-demo-f87bfcbb4-rxvjb       1/1     Running   0             22s
```

At this point the demo should be reachable at http://localhost:8000/

## Scale the deployment

At http://localhost:8000/counter you find a counter which value is held in the UI.
Pushing the "Increment" button increments the counter and keeps a log of the operation, tracking the hostname and address of the node currently serving the request.

Now try to scale down your deployment to a single pod:

```
# kubectl scale deployments/azure-kit-demo --replicas=1
```

And verify there's now a single one running:

```
# kubectl get pods
NAME                                 READY   STATUS    RESTARTS      AGE
azure-kit-demo-f87bfcbb4-5qjml       1/1     Running   0             51s
```

If you try to increment the counter again, there's a chance you get redirected to another pod and see the hostname and address change.
