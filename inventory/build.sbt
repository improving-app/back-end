name := "inventory"

scalaVersion := "2.13.8"
lazy val AkkaVersion = "2.6.19"
lazy val AkkaHttpVersion = "10.2.9"
lazy val AkkaGrpcVersion = "2.1.3"
lazy val AkkaManagementVersion = "1.1.3"
lazy val AkkaProjectionVersion = "1.2.3"
lazy val AkkaPersistenceJdbcVersion = "5.0.4"
lazy val PostgresVersion = "42.3.3"
lazy val PostgresSocketFactoryVersion = "1.5.0"
lazy val PubsubVersion = "1.116.3"
lazy val SlickVersion = "3.3.3"
lazy val CorsVersion = "1.1.3"
lazy val LogbackVersion = "1.2.11"
lazy val ScalatestVersion = "3.2.11"

ThisBuild / version := "1.13-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(domain, query)
  .settings(
    publish / skip:= true
  )

// if on M1 architecture (ARM64) and you want to push to intel/amd 64 you need to build the docker image manually as per https://www.acervera.com/blog/2020/04/sbt-docker-buildx-multi-arch/
// ended up doing:
// sbt docker:stage
// then for query and domain
// cd query/target/docker/stage
// docker buildx build --platform=linux/amd64 -t us-east4-docker.pkg.dev/reference-applications/inventory-demo/inventory-query:1.13-SNAPSHOT . 
//  -note this is not needed if the above is spelled correctly....  docker image tag reference-applications/inventory-demo/inventory/query:latest us-east4-docker.pkg.dev/reference-applications/inventory-demo/inventory-query:1.13-SNAPSHOT
// docker image push us-east4-docker.pkg.dev/reference-applications/inventory-demo/inventory-query:1.13-SNAPSHOT
// then into the gcloud shell


lazy val domain = (project in file("domain"))
  .enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
    dockerExposedPorts := Seq(8080, 8558, 25520), // http, management and artery remoting
    //dockerBaseImage := "adoptopenjdk/openjdk13:alpine-slim",
    dockerBaseImage := "adoptopenjdk/openjdk11:centos-slim",
    dockerRepository := Some("us-east4-docker.pkg.dev"),
    dockerUpdateLatest := true,
    Docker / packageName := "reference-applications/inventory-demo/inventory-domain",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http-spray-json"              % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-actor-typed"                  % AkkaVersion,
      "com.typesafe.akka"             %% "akka-persistence-typed"            % AkkaVersion,
      "com.lightbend.akka"            %% "akka-persistence-jdbc"             % AkkaPersistenceJdbcVersion,
      "org.postgresql"                 % "postgresql"                        % PostgresVersion,
      "com.google.cloud.sql"           % "postgres-socket-factory"           % PostgresSocketFactoryVersion,
      "com.google.cloud"               % "google-cloud-pubsub"               % PubsubVersion,
      "com.typesafe.akka"             %% "akka-cluster-sharding-typed"       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-persistence-query"            % AkkaVersion,
      "com.lightbend.akka"            %% "akka-projection-eventsourced"      % AkkaProjectionVersion,
      "com.lightbend.akka"            %% "akka-projection-slick"             % AkkaProjectionVersion,
      "com.lightbend.akka"            %% "akka-projection-jdbc"              % AkkaProjectionVersion,
      "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
      "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
      "com.typesafe.akka"             %% "akka-serialization-jackson"        % AkkaVersion,
      "com.typesafe.akka"             %% "akka-slf4j"                        % AkkaVersion,
      "com.typesafe.slick"            %% "slick"                             % SlickVersion,
      "com.typesafe.slick"            %% "slick-hikaricp"                    % SlickVersion,
      "ch.qos.logback"                 % "logback-classic"                   % LogbackVersion,
      "com.typesafe.akka"             %% "akka-actor-testkit-typed"          % AkkaVersion      % Test,
      "com.typesafe.akka"             %% "akka-stream-testkit"               % AkkaVersion      % Test,
      "org.scalatest"                 %% "scalatest"                         % ScalatestVersion % Test
    )
  )

lazy val query = (project in file("query"))
  .enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
    dockerExposedPorts := Seq(8080, 8558, 25520), // http, management and artery remoting
   // dockerBaseImage := "adoptopenjdk/openjdk13:alpine-slim",
    dockerBaseImage := "adoptopenjdk/openjdk11:centos-slim",
    dockerRepository := Some("us-east4-docker.pkg.dev"),
    dockerUpdateLatest := false,
    Docker / packageName := "reference-applications/inventory-demo/inventory-query",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http-spray-json"              % AkkaHttpVersion,
      "ch.megard"                     %% "akka-http-cors"                    % CorsVersion,
      "com.typesafe.akka"             %% "akka-actor-typed"                  % AkkaVersion,
      "com.typesafe.akka"             %% "akka-cluster-typed"                % AkkaVersion,
      "com.typesafe.akka"             %% "akka-persistence-query"            % AkkaVersion,
      "com.typesafe.akka"             %% "akka-persistence"                  % AkkaVersion,
      "org.postgresql"                 % "postgresql"                        % PostgresVersion,
      "com.google.cloud.sql"           % "postgres-socket-factory"           % PostgresSocketFactoryVersion,
      "com.google.cloud"               % "google-cloud-pubsub"               % PubsubVersion,
      "com.lightbend.akka"            %% "akka-projection-slick"             % AkkaProjectionVersion,
      "com.lightbend.akka"            %% "akka-projection-jdbc"              % AkkaProjectionVersion,
      "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
      "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
      "com.typesafe.akka"             %% "akka-serialization-jackson"        % AkkaVersion,
      "com.typesafe.akka"             %% "akka-slf4j"                        % AkkaVersion,
      "com.typesafe.slick"            %% "slick"                             % SlickVersion,
      "com.typesafe.slick"            %% "slick-hikaricp"                    % SlickVersion,
      "ch.qos.logback"                 % "logback-classic"                   % LogbackVersion,
      "com.typesafe.akka"             %% "akka-actor-testkit-typed"          % AkkaVersion      % Test,
      "com.typesafe.akka"             %% "akka-stream-testkit"               % AkkaVersion      % Test,
      "com.typesafe.akka"             %% "akka-http-testkit"                 % AkkaHttpVersion  % Test,
      "org.scalatest"                 %% "scalatest"                         % ScalatestVersion % Test
    )
  )
