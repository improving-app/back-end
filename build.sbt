name := "inventory"

scalaVersion := "2.13.4"
lazy val AkkaVersion = "2.6.18"
lazy val AkkaHttpVersion = "10.2.7"
lazy val AkkaGrpcVersion = "2.1.3"
lazy val AkkaManagementVersion = "1.1.3"
lazy val AkkaProjectionVersion = "1.2.3"
lazy val AkkaPersistenceJdbcVersion = "5.0.4"
lazy val PostgresVersion = "42.3.1"
lazy val PostgresSocketFactoryVersion = "1.4.1"
lazy val PubsubVersion = "1.115.1"
lazy val SlickVersion = "3.3.3"
lazy val CorsVersion = "1.1.3"
lazy val LogbackVersion = "1.2.10"
lazy val ScalatestVersion = "3.2.10"

version in ThisBuild := "1.12-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(domain, query)
  .settings(
    skip in publish := true
  )

lazy val domain = (project in file("domain"))
  .enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
    dockerExposedPorts := Seq(8080, 8558, 25520), // http, management and artery remoting
    dockerBaseImage := "adoptopenjdk/openjdk13:alpine-slim",
    dockerRepository := Some("us-east4-docker.pkg.dev"),
    dockerUpdateLatest := true,
    packageName in Docker := "nike-pov/nike-inventory/inventory-domain",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
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
    dockerBaseImage := "adoptopenjdk/openjdk13:alpine-slim",
    dockerRepository := Some("us-east4-docker.pkg.dev"),
    dockerUpdateLatest := false,
    packageName in Docker := "nike-pov/nike-inventory/inventory-query",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
      "ch.megard"                     %% "akka-http-cors"                    % CorsVersion,
      "com.typesafe.akka"             %% "akka-actor-typed"                  % AkkaVersion,
      "com.typesafe.akka"             %% "akka-cluster-typed"                % AkkaVersion,
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
