# demo-be
The backend for a demonstration  of Yoppworks technology 

# To build, publish dockerize and deploy to GKE

`sbt clean docker:publishLocal` to test locally or...

`sbt clean docker:publish` to publish to gcp artifact registry, ensure authenticated with gcloud. (This will only work if you are on an Intel processor.  If on ARM64 (apple silicon) see build.sbt for instructions)

## To deploy to kubernetes:

Be sure to create a postgres database and update the application.conf accordingly in the Slick section. You'll also need
to create the schemas found here: https://github.com/akka/akka-persistence-jdbc/blob/v5.0.4/core/src/main/resources/schema/postgres/postgres-create-schema.sql

In a cloud console session:
If you haven't already, run `gcloud container clusters get-credentials cluster-1 --zone us-east4-c`

Transfer  the domain-gcp-deployment.yaml, query-gcp-deployment.yaml, akka-cluster-role.yaml files in /kubernetes to your cloud console VM. Note: command will be deployed in a separate namespace
than query, which makes things much more clear during rolling updates, etc.

`kubectl apply -f domain-gcp-deployment.yaml`

`kubectl apply -f query-gcp-deployment.yaml`


Choose which namespace you'll be working with:

    `kubectl config set-context --current --namespace=inventory-domain`

    or

    `kubectl config set-context --current --namespace=inventory-query`

    alternatively you'll need to add -n inventory-domain or -n inventory-query to each call (or add an alias like `kid='kubectl -n inventory-domain`)


### To Redeploy to Kubernetes from scratch

`kubectl delete deploy inventory-domain`

`kubectl apply -f inventory-gcp-domain`

`kubectl delete deploy inventory-query`

`kubectl apply -f inventory-gcp-query`

### To perform a rolling update (for domain, same process for query)

Bump the app version in build.sbt, application.conf. Rebuild the images with either sbt Docker/publish if on Intel or follow instructions in build.sbt to build Intel image manually while on mac.  Akka will see the version increase and recognize
it and start routing to the pods containing that version and shut down the others.


`kubectl apply -f domain-gcp-inventory`

## to test:

Run `kubectl describe services inventory-domain-services` and take note of LoadBalancer External IP. (LoadBalancer Ingress)

`grpcurl -plaintext -proto domain/src/main/protobuf/product-availability.proto -d '{"size": "1", "color":"2", "style":"3"}' <<EXTERNALIP>>:80 com.inventory.api.v1.ProductAvailabilityService/GetAvailability`


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

once the yaml files are updated run the regen_test_yaml.sh script in the scripts directory.  This builds the domain-test-deployment.yaml and the query-test-deployment.yaml in the kubernetes  directory.




# Minikube

## To run this on minikube

First you need to install minikube see here [minikube get started](https://minikube.sigs.k8s.io/docs/start/)


You will also need access to a postgres cluster (eg instance of postgres) (#other-databases)

You will also need to run the ddl per `scripts/ddl/domain.sql` (or see the slick documentation)

Update the yamls as per (#configuration-customization)

Once the yaml's have been updated (as per above) we proceed to the building phase

Go to the project root directory and run `sbt clean docker:publishLocal`

For the following applies to work minikube needs to be running.

`minikube start` to start it.  Or `minikube status` to check its state.

Then we need to load the images into minikube (this may take some minute(s)):

`minikube image load us-east4-docker.pkg.dev/reference-applications/inventory-demo/inventory-domain:1.13-SNAPSHOT`

`minikube image load us-east4-docker.pkg.dev/reference-applications/inventory-demo/inventory-query:1.13-SNAPSHOT`

Now we are ready to start applying the yamls.

Go to the `kubernetes` directory and run one of either

`kubectl apply -f domain-test-deployment.yaml`

## or

`kubectl apply -f query-test-deployment.yaml`

Running both causes kubectl to stop responding ( on a mac M1 pro).  ymmv



To test this setup, you will need [grpcurl](https://github.com/fullstorydev/grpcurl)


In a separate terminal window run the following:

`kubectl port-forward -n inventory-domain service/inventory-domain-service 8080:80`

This will not exit -and once it does you will no longer have access to the interface.

Back in the original window, switch to the project root and run:

`grpcurl -plaintext -proto domain/src/main/protobuf/product-availability.proto -d '{"style":"1","color":"2","size":"3"}' localhost:8080 com.inventory.api.v1.ProductAvailabilityService/GetAvailability`

You should get back a non error response like 
`{

}`



### Note 
This process creates services, secrets, deployments - to remove this setup completely, you'll need to delete them all.  there is a flush_test.sh script in the scripts directory that will accomplish this.


# Other Databases

If you need to use a database other than postgresql then you will need to modify the slick sections in both query/domain application conf files, as well as the necessary dependencies in the build.sbt and possibly add settings to the yaml depending on the database requirements. - Definitely simplest to get a local version of postgres (local, docker, or even in minikube)
