# Observability Kit

## Rebuild demo app

To collect metrics, we need to rebuild the docker image with the O11y Kit agent.

1. Update the Docker image:
```
# docker build -t azure-kit-demo:latest .
```
2. Re-apply the manifest:
```
# kubectl apply -f kubernetes.yaml
```

## Install the observability images

Now we need to install the necessary observability tools into the cluster.

1. Place your server offlineKey file into the `observability` directory. You can get this through your Vaadin.com account.
2. Create the observability namespace.
```
# kubectl apply -f ./observability/kube-observability-ns.yaml
```
3. Install the OpenTelemetry collector.
```
# kubectl apply -f ./observability/kube-collector.yaml
```
4. Install Prometheus.
```
# kubectl apply -f ./observability/kube-prometheus.yaml
```
5. Install Grafana.
```
# kubectl apply -f ./observability/kube-grafana.yaml
```

## View Metrics

The Grafana homepage should now be available at http://localhost:3000. You may need to provide an HTTP tunnel to access this. If so, use port-forward:
```
# kubectl -n observability port-forward svc/grafana 3000:3000
```

If port 3000 is already in use, change the first occurrence of 3000 in the command above to an open port.

You will need to set up a Prometheus datasource pointing to http://prometheus:9090.

You will also need to set up a dashboard.
