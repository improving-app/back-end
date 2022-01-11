name := "nike-inventory"

scalaVersion := "2.13.4"
lazy val AkkaVersion = "2.6.18"
lazy val AkkaHttpVersion = "10.2.7"
lazy val AkkaGrpcVersion = "2.1.1"
lazy val AkkaManagementVersion = "1.1.2"
lazy val AkkaProjectionVersion = "1.2.3"
lazy val AkkaPersistenceJdbcVersion = "5.0.4"
lazy val PostgresVersion = "42.3.1"
lazy val PostgresSocketFactoryVersion = "1.4.1"
lazy val SlickVersion = "3.3.3"
lazy val ScalatestVersion = "3.2.9"

lazy val root = (project in file("."))
  .aggregate(common, domain)//, query)

lazy val common = (project in file("common"))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"             %% "akka-actor-typed"                  % AkkaVersion
    )
  )

lazy val domain = (project in file("domain"))
  .dependsOn(common)
  .enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-actor-typed"                  % AkkaVersion,
      "com.typesafe.akka"             %% "akka-persistence-typed"            % AkkaVersion,
      "com.lightbend.akka"            %% "akka-persistence-jdbc"             % AkkaPersistenceJdbcVersion,
      "org.postgresql"                 % "postgresql"                        % PostgresVersion,
      "com.google.cloud.sql"           % "postgres-socket-factory"           % PostgresSocketFactoryVersion,
      "com.typesafe.akka"             %% "akka-cluster-sharding-typed"       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
      "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
      //"com.lightbend.akka.management" %% "akka-management-cluster-http"      % AkkaManagementVersion,       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-serialization-jackson"        % AkkaVersion,
      "com.typesafe.akka"             %% "akka-slf4j"                        % AkkaVersion,
      "com.typesafe.slick"            %% "slick"                             % SlickVersion,
      "com.typesafe.slick"            %% "slick-hikaricp"                    % SlickVersion,

      // The Akka HTTP overwrites are required because Akka-gRPC depends on 10.1.x
      "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http-spray-json"              % AkkaHttpVersion,

      "ch.qos.logback"                 % "logback-classic"                   % "1.2.10",
      "com.typesafe.akka"             %% "akka-actor-testkit-typed"          % AkkaVersion      % Test,
      "com.typesafe.akka"             %% "akka-stream-testkit"               % AkkaVersion      % Test,
      "org.scalatest"                 %% "scalatest"                         % ScalatestVersion % Test
    ),
    version := "0.1.1-SNAPSHOT", // Must update app-version in conf as well.
    dockerExposedPorts := Seq(8080, 8558, 25520), // http, management and artery remoting
    dockerBaseImage := "adoptopenjdk/openjdk13:alpine-slim",
    packageName in Docker := "nike-pov/nike-inventory/nike-inventory-domain",
    dockerRepository := Some("us-east4-docker.pkg.dev"),
    dockerUpdateLatest := true
  )

lazy val query = (project in file("query"))
  .dependsOn(common)
  .enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
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
      "com.typesafe.akka"             %% "akka-serialization-jackson"        % AkkaVersion,
      "com.typesafe.akka"             %% "akka-slf4j"                        % AkkaVersion,
      "com.typesafe.slick"            %% "slick"                             % SlickVersion,
      "com.typesafe.slick"            %% "slick-hikaricp"                    % SlickVersion,

      // The Akka HTTP overwrites are required because Akka-gRPC depends on 10.1.x
      "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http-spray-json"              % AkkaHttpVersion,

      "ch.qos.logback"                 % "logback-classic"                   % "1.2.10",
      "com.typesafe.akka"             %% "akka-actor-testkit-typed"          % AkkaVersion      % Test,
      "com.typesafe.akka"             %% "akka-stream-testkit"               % AkkaVersion      % Test,
      "org.scalatest"                 %% "scalatest"                         % ScalatestVersion % Test
    ),
    version := "0.1.0-SNAPSHOT", // Must update app-version in conf as well.
    dockerExposedPorts := Seq(8080, 8558, 25520), // http, management and artery remoting
    dockerBaseImage := "adoptopenjdk/openjdk13:alpine-slim",
    packageName in Docker := "nike-pov/nike-inventory/nike-inventory-query",
    dockerRepository := Some("us-east4-docker.pkg.dev"),
    dockerUpdateLatest := true
  )
