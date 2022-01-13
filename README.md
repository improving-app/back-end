NOTE: query side will be pushed very soon!
      we'll need to create a new GCP project to match example.inventory and unwind all the Nike stuff.

# To build, publish dockerize and deploy to GKE (using Nike project for example)

`sbt clean docker:publishLocal` to test locally or...

`sbt clean docker:publish` to publish to gcp artifact registry, ensure authenticated with gcloud.

## To deploy to kubernetes:

Be sure to create a postgres database and update the application.conf accordingly in the Slick section. You'll also need
to create the schemas found here: https://github.com/akka/akka-persistence-jdbc/blob/v5.0.4/core/src/main/resources/schema/postgres/postgres-create-schema.sql

In a cloud console session:
If you haven't already, run `gcloud container clusters get-credentials cluster-1 --zone us-east4-c`

Transfer all files in /kubernetes to your cloud console VM. Note: command will be deployed in a separate namespace
than query, which makes things much more clear during rolling updates, etc.

`kubectl apply -f domain-namespace.json`

`kubectl apply -f query-namespace.json`

Choose which namespace you'll be working with:

    `kubectl config set-context --current --namespace=nike-inventory-command`

    or

    `kubectl config set-context --current --namespace=nike-inventory-query`


`kubectl apply -f akka-cluster-role.yaml`

`kubectl apply -f command.yaml`

`kubectl expose deployment nike-inventory-domain --type=LoadBalancer --name=nike-inventory-domain-service`

`kubectl apply -f command.yaml`

`kubectl expose deployment nike-inventory-query --type=LoadBalancer --name=nike-inventory-query-service`

### FUTURE (todo):
    `gcloud secrets create nike-sql-secret \
    --replication-policy="automatic"`

    `gcloud secrets update nike-sql-secret \
    --update-labels=username=postgres`

    `gcloud secrets update nike-sql-secret \
    --update-labels=password=postgres`

### To Redeploy to Kubernetes from scratch

`kubectl delete deploy nike-inventory-command`

`kubectl apply -f nike-inventory-command`

`kubectl delete deploy nike-inventory-query`

`kubectl apply -f nike-inventory-query`

### To perform a rolling update (for command, same process for query)

Bump the app version in build.sbt, application.conf and command.yaml. Akka will see the version increase and recognize
it and start routing to the pods containing that version and shut down the others.

`kubectl apply -f nike-inventory-command`

## to test:

Run `kubectl describe services nike-inventory-service` and take note of LoadBalancer External IP.

`grpcurl -plaintext -d '{"sku": "1234"}' <<EXTERNALIP>>:8080 com.nike.inventory.api.ProductAvailabilityService/GetAvailability`