# Kubernetes Kit Demo

A demo application for [Vaadin Kubernetes Kit](https://vaadin.com/docs/latest/tools/kubernetes).

## Prerequisites

- A local Kubernetes cluster (e.g., [minikube](https://minikube.sigs.k8s.io/docs/start/), [kind](https://kind.sigs.k8s.io/docs/user/quick-start/), or [Docker Desktop](https://docs.docker.com/desktop/kubernetes/))
- [Helm](https://helm.sh/docs/intro/install/) for installing Envoy Gateway

## Run the demo

### 1. Install Envoy Gateway

Install [Envoy Gateway](https://gateway.envoyproxy.io/) as the Gateway API implementation:

```
helm install eg oci://docker.io/envoyproxy/gateway-helm --version v1.6.0 -n envoy-gateway-system --create-namespace
```

Then deploy the gateway configuration:

```
kubectl apply -f deployment/gateway.yaml
```

### 2. Build the application image

Build the production JAR and create a container image using the Spring Boot Maven plugin:

```
mvn clean package -pl :kubernetes-kit-demo -Pproduction,redis spring-boot:build-image -pl :kubernetes-kit-demo -Pproduction,redis
```

To publish the image to a local registry (e.g., for kind or minikube):

```
docker tag kubernetes-kit-demo:3.0-SNAPSHOT localhost:5001/kubernetes-kit-demo:1.0.0
docker push localhost:5001/kubernetes-kit-demo:1.0.0
```

### 3. Deploy to the cluster

Deploy Redis, the application, and the HTTP route:

```
kubectl apply -f deployment/redis.yaml -f deployment/app-v1.yaml -f deployment/route-v1.yaml
```

### 4. Access the application

Find the Envoy proxy service and forward a local port:

```
export ENVOY_SERVICE=$(kubectl get svc -l gateway.envoyproxy.io/owning-gateway-name=public-gateway -o jsonpath='{.items[0].metadata.name}')
kubectl port-forward service/${ENVOY_SERVICE} 8080:80
```

The demo should be reachable at http://localhost:8080/

You should see 4 application pods and 1 Redis pod running:

```
kubectl get pods
NAME                                        READY   STATUS    RESTARTS      AGE
kubernetes-kit-demo-v1-f87bfcbb4-5qjml      1/1     Running   0             22s
kubernetes-kit-demo-v1-f87bfcbb4-czkzr      1/1     Running   0             22s
kubernetes-kit-demo-v1-f87bfcbb4-gjqw6      1/1     Running   0             22s
kubernetes-kit-demo-v1-f87bfcbb4-rxvjb      1/1     Running   0             22s
kubernetes-kit-redis-788d56c66-8b259        1/1     Running   0             22s
```

**Note:** You can use Hazelcast instead of Redis by replacing `redis` with `hazelcast` in the Maven profiles and deploying `deployment/hazelcast.yaml` instead of `deployment/redis.yaml`.

## Scale the deployment

At http://localhost:8080/counter you find a counter whose value is held in the UI. Pushing the "Increment" button increments the counter and keeps a log of the operation, tracking the hostname and address of the node currently serving the request.

Simulate the unavailability of a pod (e.g., `kubernetes-kit-demo-v1-f87bfcbb4-5qjml`):

```
kubectl delete pod kubernetes-kit-demo-v1-f87bfcbb4-5qjml
```

Wait until the pod is terminated and a new one is running:

```
kubectl get pods
NAME                                        READY   STATUS    RESTARTS      AGE
kubernetes-kit-demo-v1-f87bfcbb4-jj5c4      1/1     Running   0             12s
kubernetes-kit-demo-v1-f87bfcbb4-czkzr      1/1     Running   0             51s
kubernetes-kit-demo-v1-f87bfcbb4-gjqw6      1/1     Running   0             51s
kubernetes-kit-demo-v1-f87bfcbb4-rxvjb      1/1     Running   0             51s
kubernetes-kit-redis-788d56c66-8b259        1/1     Running   0             51s
```

Try incrementing the counter again. The request will be redirected to another pod but the session should be restored and the counter will keep incrementing.

## Update the application to a new version

### 1. Build and deploy the new version

```
mvn clean package -pl :kubernetes-kit-demo -Pproduction,redis spring-boot:build-image -pl :kubernetes-kit-demo -Pproduction,redis
kubectl apply -f deployment/app-v2.yaml
```

### 2. Route new sessions to the new version

This routes new sessions to v2 while keeping existing sessions on v1 through cookie-based session persistence:

```
kubectl apply -f deployment/route-v1-v2.yaml
```

### 3. Notify existing users

Inject the `X-AppUpdate` header to trigger the version update notification for users on v1:

```
kubectl apply -f deployment/route-v1-v2-notify.yaml
```

### 4. Switch to the new version

Once the new version is verified, route all traffic to v2:

```
kubectl apply -f deployment/route-v2.yaml
```

Then remove the old version:

```
kubectl delete -f deployment/app-v1.yaml
```
