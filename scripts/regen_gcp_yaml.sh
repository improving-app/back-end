cd ../kubernetes/overlays/gcp/domain
kubectl kustomize > ../../../domain-gcp-deployment.yaml
cd ../query
kubectl kustomize > ../../../query-gcp-deployment.yaml