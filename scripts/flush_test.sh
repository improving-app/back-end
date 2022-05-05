kubectl delete -n inventory-domain deployment inventory-domain
kubectl delete -n inventory-query deployment inventory-query
kubectl delete -n inventory-domain service inventory-domain-service
kubectl delete -n inventory-query service inventory-query-service
kubectl delete -n inventory-domain secrets persistence-db-credentials
kubectl delete -n inventory-domain secrets projection-db-credentials
kubectl delete -n inventory-query secrets projection-db-credentials