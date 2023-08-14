# improving-app/back-end

The backend for a demonstration  of Yoppworks technology 

# To build, publish dockerize and deploy to GKE

`sbt docker:stage` to generate the Dockerfile

`sbt clean docker:publishLocal` to publish docker image locally or...

`sbt clean docker:publish` to publish to gcp artifact registry, ensure authenticated with gcloud. (This will only work if you are on an Intel processor.  If on ARM64 (apple silicon) see build.sbt for instructions)

## Locally running the server:

For running the services that are event sourced, it needs a database for persistence. In our case, we use ScyllaDB, so run the command `docker run --name test-scylla --publish 9042:9042 --hostname test-scylla -d scylladb/scylla --smp 1` and this should allow you to have the image. You can terminate this running container because the Docker Compose file should run it for you.

`docker compose up` to run all services. This is assuming you have all the dockerfiles generated after `sbt docker:stage` and `sbt clean docker: publishLocal`

## Locally running on microk8s:

### IMPORTANT NOTE
For now, local scylla-db services can only be connected to in microk8s by changing a service's `application.conf` to use the *internal* `ClusterIP` of the `scylla-db` service
For this, `applyForInternalIP.yaml`, will be used in place of `scyllaApply.yaml` in below steps.

## Installation Instructions
1. Install microk8s if not already installed (: see https://microk8s.io/docs/install-alternatives for instructions)
   - If on mac, just run `brew install ubuntu/microk8s/microk8s` but make sure you read 
   [these notes](https://microk8s.io/docs/install-macos)
2. Run `mickrok8s status` to see which addons are enabled
3. Run `microk8s enable <name>` to ensure these addons are enabled:
   `dashboard, dns, metrics-server, observability, registry`
4. Copy the `microk8s/microk8s-config.yaml` file to the standard config location
   per [these instructions](https://microk8s.io/docs/add-launch-config). You will need to repeat this every time
   the YAML file changes. 
5. Install scylla
   1. `docker run --name some-scylla -d scylladb/scylla` to download scylla-db image locally as a container
   2. `docker save scylladb/scylla > scylla.tar` to save container for uploading
   3. `multipass transfer scylla.tar microk8s-vm:/tmp/scylla.tar` to transfer image into multipass directory
   4. `microk8s ctr image import /tmp/scylla.tar` to import from multipass image to microk8s
   5. `microk8s kubectl apply -f scyllaApplyForInternalIP.yaml`
6. Upload or push directories 
   - Option 1: `./importAllFromDockerLocally.sh` or `./importToMicro.sh improving-app-[serviceName]` for individual services
   - Option 2: `./tagAllForDocker.sh`
7. Run command `microk8s kubectl apply -f microApplyLocal.yaml` for Option 1 or `microk8s kubectl apply -f microApply.yaml` for Option 2 in previous step
6. Check status with `microk8s kubectl get pods -o wide`
7. `microk8s kubectl apply -f applyForInternalIP.yaml`
6. Run `microk8s kubectl get all --all-namespaces` to view all services in k8s
   - expected results should look like below. Use `NAME` column to fill in `[pod-name]` in steps to expose
   ```
   NAME                             READY   STATUS    RESTARTS       AGE     IP             NODE          NOMINATED NODE   READINESS GATES
   improving-app-7c98699b4b-ptzmj   7/7     Running   0              6d23h   192.168.64.3   microk8s-vm   <none>           <none>
   scylla-db-7bdb45445b-8skfx       1/1     Running   0              179m    192.168.64.3   microk8s-vm   <none>           <none>
    ```

## Exposing Services Instructions
1. Expose deployment internally using `microk8s kubectl expose deployment improving-app --type=NodePort --port=9000`
2. Inspect services using `microk8s kubectl get service -o wide`
   - expected results should look like below.
   ```
   NAME            TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE    SELECTOR
   kubernetes      ClusterIP   10.152.183.1     <none>        443/TCP          23d    <none>
   improving-app   NodePort    10.152.183.68    <none>        9000:30668/TCP   7d1h   app=improving-app
   scylla-db       NodePort    10.152.183.5     <none>        9042:30900/TCP   7d1h   app=scylla-db
   ```
3. Expose node externally using port forwarding `microk8s kubectl port-forward services/improving-app 9000:9000`

NOTE: You can inspect a pod's configuration using `microk8s kubectl describe pod pod-name`


## Testing on locally running server:

Present in each subproject is a `sample-requests.txt`. One example is on `./tenant/src/main/resources/sample-requests.txt`.
This shows working examples of grpcurl requests on a running server. Simply run the request on a command line and it should respond appropriately.

### NOTE: all below is very old and should possibly be removed

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
