name := "nike-inventory"

version := "0.1.0"

scalaVersion := "2.13.4"
lazy val AkkaVersion = "2.6.17"
lazy val AkkaHttpVersion = "10.2.7"
lazy val AkkaGrpcVersion = "2.1.1"
lazy val AkkaManagementVersion = "1.1.1"
lazy val ScalatestVersion = "3.1.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-actor-typed"                  % AkkaVersion,
  "com.typesafe.akka"             %% "akka-persistence-typed"            % AkkaVersion,
  "com.typesafe.akka"             %% "akka-cluster-sharding-typed"       % AkkaVersion,
  "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion,
  "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
  //"com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
  "com.typesafe.akka"             %% "akka-pki"                          % AkkaVersion,
  "com.typesafe.akka"             %% "akka-serialization-jackson"        % AkkaVersion,
  "com.typesafe.akka"             %% "akka-slf4j"                        % AkkaVersion,

  // The Akka HTTP overwrites are required because Akka-gRPC depends on 10.1.x
  "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
  "com.typesafe.akka"             %% "akka-http-spray-json"              % AkkaHttpVersion,

  "ch.qos.logback"                 % "logback-classic" % "1.2.3",
  "com.typesafe.akka"             %% "akka-actor-testkit-typed"          % AkkaVersion      % Test,
  "com.typesafe.akka"             %% "akka-stream-testkit"               % AkkaVersion      % Test,
  "org.scalatest"                 %% "scalatest"                         % ScalatestVersion % Test
)

enablePlugins(AkkaGrpcPlugin)

enablePlugins(DockerPlugin, AshScriptPlugin)
dockerBaseImage := "adoptopenjdk/openjdk13:alpine-slim"
dockerExposedPorts ++= Seq(2551, 8080)
dockerUpdateLatest := true
