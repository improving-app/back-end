cd ../kubernetes/overlays/test/domain
kubectl kustomize > ../../../domain-test-deployment.yaml
cd ../query
kubectl kustomize > ../../../query-test-deployment.yaml