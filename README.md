# To build, publish dockerize and deploy to GKE

`sbt clean test`

Run `docker images` and take note of your newly created image id.

`docker tag <<YOURIMAGEID>> us-east4-docker.pkg.dev/nike-pov/nike-inventory/nike-inventory:latest`

`docker push us-east4-docker.pkg.dev/nike-pov/nike-inventory/nike-inventory:latest`

## To deploy to kubernetes:

In a cloud console session:
If you haven't already, run `gcloud container clusters get-credentials cluster-1 --zone us-east4-c`

Transfer all files in /kubernetes to your cloud console VM.

`kubectl apply -f namespace.json`

`kubectl config set-context --current --namespace=nike-inventory`

`kubectl apply -f akka-cluster-role.yaml`

`kubectl apply -f service-account.yaml`

`gcloud secrets create nike-sql-secret \
--replication-policy="automatic"`

`gcloud secrets update nike-sql-secret \
--update-labels=username=postgres`

`gcloud secrets update nike-sql-secret \
--update-labels=password=postgres`

`kubectl apply -f deployment.yaml`

`kubectl expose deployment nike-inventory --type=LoadBalancer --name=nike-inventory-service`

## to test:

Run `kubectl describe services nike-inventory-service` and take note of LoadBalancer External IP.

`grpcurl -plaintext -d '{"sku": "1234"}' <<EXTERNALIP>>:8080 com.nike.inventory.api.ProductAvailabilityService/GetAvailability`