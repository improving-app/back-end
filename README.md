# To build, publish dockerize and deploy to GKE

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

    `kubectl config set-context --current --namespace=inventory-domain`

    or

    `kubectl config set-context --current --namespace=inventory-query`

`kubectl apply -f akka-cluster-role.yaml`

`kubectl apply -f domain.yaml`

`kubectl expose deployment inventory-domain --type=LoadBalancer --port=80 --target-port=8080 --name=inventory-domain-service`

`kubectl apply -f query.yaml`

`kubectl expose deployment inventory-query --type=LoadBalancer --port=80 --target-port=8080 --name=inventory-query-service`

### To Redeploy to Kubernetes from scratch

`kubectl delete deploy inventory-command`

`kubectl apply -f inventory-command`

`kubectl delete deploy inventory-query`

`kubectl apply -f inventory-query`

### To perform a rolling update (for command, same process for query)

Bump the app version in build.sbt, application.conf and command.yaml. Akka will see the version increase and recognize
it and start routing to the pods containing that version and shut down the others.

`kubectl apply -f inventory-domain`

## to test:

Run `kubectl describe services inventory-service` and take note of LoadBalancer External IP.

`grpcurl -plaintext -d '{"size": "1", "color":"2", "style":"3"}' <<EXTERNALIP>>:8080 com.inventory.api.v1.ProductAvailabilityService/GetAvailability`


# Configuration Customization

This kubernetes file setup supports setting separate jdbc urls for the projection and persistence database and setting the user/password secrets in kubernetes files instead of hardcoding them in the application.confs.

The projection url (which is used by both query and domain) is located here
`kubernetes/overlays/test/shared/projection-jdbc.yaml`

The persistence url (used only by domain) is located here
`kubernetes/overlays/test/domain/query-deployment-patches.yaml`

The projection db user/password are stored in
`kubernetes/overlays/test/shared/projection-secret.yaml`

The persistence db user/password are stored in 
`kubernetes/overlays/test/domain/persistence-secret.yaml`

The user/password values are stored in base64 encoding.
use `echo -n user | base64` to encode the value, then copy it to the file. 




# Minikube

## To run this on minikube

First you need to install minikube see here [minikube get started](https://minikube.sigs.k8s.io/docs/start/)


You will also need access to a postgres cluster (eg instance of postgres) (#other-databases)

You will also need to run the ddl per `scripts/ddl/domain.sql` (or see the slick documentation)

Update the yamls as per (#configuration-customization)

Once the yaml's have been updated (as per above) we proceed to the building phase

First we use kubectl kustomize to merge the files into a single yaml

Change directory to `kubernetes/overlays/test/domain` and the run 

`kubectl kustomize > ../../../domain-test-deploymen.yaml`

Then switch to `kubernetes/overlays/test/query` and run

`kubectl kustomize > ../../../query-test-deployment.yaml`


Go to the project root directory and run `sbt clean docker:publishLocal`

For the following applies to work minikube needs to be running.

`minikube start` to start it.  Or `minikube status` to check its state.

Then we need to load the images into minikube (this may take some minute(s)):

`minikube image load us-east4-docker.pkg.dev/nike-pov/nike-inventory/inventory-domain:1.12-SNAPSHOT`

`minikube image load us-east4-docker.pkg.dev/nike-pov/nike-inventory/inventory-query:1.12-SNAPSHOT`

Now we are ready to start applying the yamls.

Go to the `kubernetes` directory and run

`kubectl apply -f akka-cluster-role.yaml`

`kubectl apply -f domain-test-deployment.yaml`

`kubectl apply -f query-test-deployment.yaml`

-- now part of the main yaml `kubectl expose -n inventory-domain deployment inventory-domain --type=LoadBalancer --port=80 --target-port=8080 --name=inventory-domain-service`

-- now part of the main yaml `kubectl expose -n inventory-query deployment inventory-query --type=LoadBalancer --port=80 --target-port=8080 --name=inventory-query-service`


To test this setup, you will need [grpcurl](https://github.com/fullstorydev/grpcurl)


In a separate terminal window run the following:

`kubectl port-forward -n inventory-query service/inventory-query-service 8080:80`

This will not exit -and once it does you will no longer have access to the interface.

Back in the original window, switch to the project root and run:

`grpcurl -plaintext -proto domain/src/main/protobuf/product-availability.proto -d '{"style":"1","color":"2","size":"3"}' localhost:8080 com.inventory.api.v1.ProductAvailabilityService/GetAvailability`

You should get back a non error response like 
`{

}`


This process creates services, secrets, deployments - to remove this setup completely, you'll need to delete them all.


# Other Databases

If you need to use a database other than postgresql then you will need to modify the slick sections in both query/domain application conf files, as well as the necessary dependencies in the build.sbt and possibly add settings to the yaml depending on the database requirements. - Definitely simplest to get a local version of postgres (local, docker, or even in minikube)