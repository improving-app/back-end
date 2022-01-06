name := "nike-inventory"

version := "0.1.2" // Must update app-version in conf as well.

scalaVersion := "2.13.4"
lazy val AkkaVersion = "2.6.18"
lazy val AkkaHttpVersion = "10.2.7"
lazy val AkkaGrpcVersion = "2.1.1"
lazy val AkkaManagementVersion = "1.1.1"
lazy val AkkaProjectionVersion = "1.2.2"
lazy val AkkaPersistenceJdbcVersion = "5.0.4"
lazy val PostgresVersion = "42.3.1"
lazy val PostgresSocketFactoryVersion = "1.4.1"
lazy val SlickVersion = "3.3.3"
lazy val ScalatestVersion = "3.2.9"

libraryDependencies ++= Seq(
  "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-actor-typed"                  % AkkaVersion,
  "com.typesafe.akka"             %% "akka-persistence-typed"            % AkkaVersion,
  "com.lightbend.akka"            %% "akka-persistence-jdbc"             % AkkaPersistenceJdbcVersion,
  "org.postgresql"                 % "postgresql"                        % PostgresVersion,
  "com.google.cloud.sql"           % "postgres-socket-factory"           % PostgresSocketFactoryVersion,
  "com.typesafe.akka"             %% "akka-persistence-query"            % AkkaVersion,
  "com.lightbend.akka"            %% "akka-projection-eventsourced"      % AkkaProjectionVersion,
  "com.lightbend.akka"            %% "akka-projection-slick"             % AkkaProjectionVersion,
  "com.lightbend.akka"            %% "akka-projection-jdbc"              % AkkaProjectionVersion,
  "com.typesafe.akka"             %% "akka-cluster-sharding-typed"       % AkkaVersion,
  "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion,
  "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
  "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http"      % AkkaManagementVersion,
  "com.typesafe.akka"             %% "akka-pki"                          % AkkaVersion,
  "com.typesafe.akka"             %% "akka-serialization-jackson"        % AkkaVersion,
  "com.typesafe.akka"             %% "akka-slf4j"                        % AkkaVersion,
  "com.typesafe.slick"            %% "slick"                             % SlickVersion,
  "com.typesafe.slick"            %% "slick-hikaricp"                    % SlickVersion,

  // The Akka HTTP overwrites are required because Akka-gRPC depends on 10.1.x
  "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-http-spray-json"              % AkkaHttpVersion,

  "ch.qos.logback"                 % "logback-classic" % "1.2.3",
  "com.typesafe.akka"             %% "akka-actor-testkit-typed"          % AkkaVersion      % Test,
  "com.typesafe.akka"             %% "akka-stream-testkit"               % AkkaVersion      % Test,
  "org.scalatest"                 %% "scalatest"                         % ScalatestVersion % Test
)

enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)

dockerBaseImage := "adoptopenjdk/openjdk13:alpine-slim"
dockerExposedPorts := Seq(8080, 8558, 25520) // http, management and artery remoting
packageName in Docker := "nike-pov/nike-inventory/nike-inventory"
dockerRepository := Some("us-east4-docker.pkg.dev")
dockerUpdateLatest := true
