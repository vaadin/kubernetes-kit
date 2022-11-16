# Kubernetes Kit Demo

## Run the demo

To run the demo you first need to have a local Kubernetes cluster up and running.
You can find recommended tools here: https://kubernetes.io/docs/tasks/tools/

To deploy the demo application into your cluster, follow these steps:

1. Build the application JAR:
```
# mvn clean package -Pproduction,redis
```
2. Create or update the Docker image:
```
# docker build -t kubernetes-kit-demo:latest .
```
3. Deploy the image into the cluster:
```
# kubectl apply -f deployment/redis.yaml -f deployment/app.yaml
```

You should now see 4 pods running, e.g.

```
# kubectl get pods
NAME                                      READY   STATUS    RESTARTS      AGE
kubernetes-kit-demo-f87bfcbb4-5qjml       1/1     Running   0             22s
kubernetes-kit-demo-f87bfcbb4-czkzr       1/1     Running   0             22s
kubernetes-kit-demo-f87bfcbb4-gjqw6       1/1     Running   0             22s
kubernetes-kit-demo-f87bfcbb4-rxvjb       1/1     Running   0             22s
kubernetes-kit-redis-788d56c66-8b259      1/1     Running   0             22s
```

At this point the demo should be reachable at http://localhost:8000/

*NOTE* You can run the demo with Hazelcast instead of Redis by replacing `redis` with `hazelcast` in steps 1 and 3.

## Scale the deployment

At http://localhost:8000/counter you find a counter which value is held in the UI.
Pushing the "Increment" button increments the counter and keeps a log of the operation, tracking the hostname and address of the node currently serving the request.

Now let's simulate the unavailability of the pod handling the requests (assume it's the `kubernetes-kit-demo-f87bfcbb4-5qjml` pod):

```
# kubectl delete pod kubernetes-kit-demo-f87bfcbb4-5qjml
```

And wait until the pod is terminated and there's a new one running:

```
# kubectl get pods
NAME                                      READY   STATUS    RESTARTS      AGE
kubernetes-kit-demo-f87bfcbb4-jj5c4       1/1     Running   0             12s
kubernetes-kit-demo-f87bfcbb4-czkzr       1/1     Running   0             51s
kubernetes-kit-demo-f87bfcbb4-gjqw6       1/1     Running   0             51s
kubernetes-kit-demo-f87bfcbb4-rxvjb       1/1     Running   0             51s
kubernetes-kit-redis-788d56c66-8b259      1/1     Running   0             51s
```

If you try to increment the counter again, your request will be redirected to another pod but the session should be restored and the counter will keep incrementing.
