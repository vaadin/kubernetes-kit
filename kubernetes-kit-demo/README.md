# Kubernetes Kit Demo

## Run the demo

To run the demo you first need to have a local Kubernetes cluster up and running.
You can find recommended tools here: https://kubernetes.io/docs/tasks/tools/

You also need to setup the ingress-ngnix before:
```
# kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.4.0/deploy/static/provider/cloud/deploy.yaml
```

To deploy the demo application into your cluster, follow these steps:
1. Build the application JAR:
```
# mvn clean package -Pproduction,redis
```
2. Create or update the Docker image with version tag:
```
# docker build -t kubernetes-kit-demo:1.0.0 .
```
Optionally, if you run a local docker registry, add the *localhost:5001* registry address prefix as well and push it to registry. Refer to the image in the config files with *the localhost:5001/kubernetes-kit-demo:1.0.0* name:
```
# docker build -t localhost:5001/kubernetes-kit-demo:1.0.0 .
# docker push localhost:5001/kubernetes-kit-demo:1.0.0
```
3. Deploy the image, redis service and ingress controller into the cluster:
```
# kubectl apply -f deployment/redis.yaml -f deployment/app-v1.yaml -f deployment/ingress-v1.yaml
```
4. Open port forward for ingress controller (in other window, and keep it open):
```
# kubectl port-forward -n ingress-nginx service/ingress-nginx-controller 8080:80
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

At this point the demo should be reachable at http://localhost:8080/

*NOTE:* You can run the demo with Hazelcast instead of Redis by replacing `redis` with `hazelcast` in steps 2 and 4.

## Scale the deployment

At http://localhost:8080/counter you find a counter which value is held in the UI.
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

## Update the application to a new version

1. Build a new application version:
```
# docker build -t kubernetes-kit-demo:2.0.0 .
```
Optionally, if you run a local docker registry, add the *localhost:5001* registry address prefix as well and push it to registry. Refer to the image in the config files with *the localhost:5001/kubernetes-kit-demo:1.0.0* name:
```
# docker build -t localhost:5001/kubernetes-kit-demo:2.0.0 .
# docker push localhost:5001/kubernetes-kit-demo:2.0.0
```
2. Deploy the new version:
```
# kubectl apply -f deployment/app-v2.yaml
```
3. Deploy the canary ingress config for it:
```
# kubectl apply -f deployment/ingress-v2-use-canary.yaml
```
4. Set the X-AppUpdate header for the old version to show the version update notification:
```
# kubectl apply -f deployment/ingress-v1-add-header.yaml
```

Test and verify if the new version is working properly.

5. Make the new version as a default:
```
# kubectl apply -f deployment/ingress-v2.yaml
```
6. Delete the old version:
```
# kubectl delete -f deployment/app-v1.yaml
```
7. Delete the canary ingress config:
```
# kubectl delete -f deployment/ingress-v2-use-canary.yaml
```
