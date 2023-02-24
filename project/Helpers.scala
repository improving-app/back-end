import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin
import sbt.{Project, Test, Tests, _}
import sbt.Keys._
import kalix.sbt.KalixPlugin

/**
 * Contains the versions needed.
 */
object V {
  lazy val scala = "2.13.8"
  lazy val akka = "2.7.0"
  lazy val akkaHttp = "10.5.0"
  lazy val akkaGrpc = "2.1.3"
  lazy val akkaManagement = "1.2.0"
  lazy val akkaProjection = "1.3.1"
  lazy val akkaPersistenceJdbc = "5.2.1"
  lazy val postgres = "42.5.4"
  lazy val postgresSocketFactory = "1.10.0"
  lazy val pubsub = "1.123.2"
  lazy val slick = "3.4.1"
  lazy val cors = "1.1.3"
  lazy val logback = "1.4.5"
  lazy val scalatest = "3.2.15"
}

// C for Configuration functions
object C {

  val scala3Options = Seq(
    "-target:11",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlog-reflective-calls",
    "-Xlint"
  )

  val javaOptions = Seq(
    "-Xlint:unchecked", "-Xlint:deprecation", "-parameters" // for Jackson
  )
  def kalix(artifactName: String)(project: Project): Project = {
    project
      .enablePlugins(KalixPlugin, JavaAppPackaging, DockerPlugin)
      .settings(
        name := artifactName,
        organization := "com.improving",
        organizationHomepage := Some(url("https://improving.app")),
        licenses := Seq(("Apache 2", url("https://www.apache.org/licenses/LICENSE-2.0"))),
        scalaVersion := V.scala,
        scalacOptions := scala3Options,
        Compile / scalacOptions ++= scala3Options,
        Compile / javacOptions ++= javaOptions,
        Test / parallelExecution := false,
        Test / testOptions += Tests.Argument("-oDF"),
        Test / logBuffered := false,
        Compile / run := {
          // needed for the proxy to access the user function on all platforms
          sys.props += "kalix.user-function-interface" -> "0.0.0.0"
          (Compile / run).evaluated
        },
        run / fork := false,
        Global / cancelable := false, // ctrl-c
        libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.2.12" % Test),
        dockerBaseImage := "docker.io/library/adoptopenjdk:11-jre-hotspot",
        dockerUsername := sys.props.get("docker.username"),
        dockerRepository := sys.props.get("docker.registry"),
        dockerUpdateLatest := true,
        dockerBuildCommand := {
          if (sys.props("os.arch") != "amd64") {
            // use buildx with platform to build supported amd64 images on other CPU architectures
            // this may require that you have first run 'docker buildx create' to set docker buildx up
            dockerExecCommand.value ++ Seq("buildx", "build", "--platform=linux/amd64", "--load") ++ dockerBuildOptions.value :+ "."
          } else dockerBuildCommand.value
        }
      )
  }

  def akkaPersistentEntity(artifactName: String)(project: Project): Project = {
    project.enablePlugins(JavaAppPackaging, DockerPlugin)
      .settings(
        name := artifactName,
        organization := "com.improving",
        organizationHomepage := Some(url("https://improving.app")),
        licenses := Seq(("Apache 2", url("https://www.apache.org/licenses/LICENSE-2.0"))),
        scalaVersion := V.scala,
        scalacOptions := scala3Options,
        Compile / scalacOptions ++= scala3Options,
        Compile / javacOptions ++= javaOptions,
        Test / parallelExecution := false,
        Test / testOptions += Tests.Argument("-oDF"),
        Test / logBuffered := false,
        run / fork := false,
        Global / cancelable := false, // ctrl-c
        libraryDependencies ++= Seq(
          "com.typesafe.akka" %% "akka-http" % V.akkaHttp,
          "com.typesafe.akka" %% "akka-http2-support" % V.akkaHttp ,
          "com.typesafe.akka" %% "akka-http-spray-json" % V.akkaHttp,
          "com.typesafe.akka" %% "akka-actor-typed" % V.akka,
          "com.typesafe.akka" %% "akka-persistence-typed" % V.akka,
          "com.lightbend.akka" %% "akka-persistence-jdbc" % V.akkaPersistenceJdbc,
          "org.postgresql" % "postgresql" % V.postgres,
          "com.google.cloud.sql" % "postgres-socket-factory" % V.postgresSocketFactory,
          "com.google.cloud" % "google-cloud-pubsub" % V.pubsub,
          "com.typesafe.akka" %% "akka-cluster-sharding-typed" % V.akka,
          "com.typesafe.akka" %% "akka-persistence-query" % V.akka,
          "com.lightbend.akka" %% "akka-projection-eventsourced" % V.akkaProjection,
          "com.lightbend.akka" %% "akka-projection-slick" % V.akkaProjection,
          "com.lightbend.akka" %% "akka-projection-jdbc" % V.akkaProjection,
          "com.typesafe.akka" %% "akka-stream" % V.akka,
          "com.typesafe.akka" %% "akka-discovery" % V.akka,
          "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % V.akkaManagement,
          "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % V.akkaManagement,
          "com.typesafe.akka" %% "akka-serialization-jackson" % V.akka,
          "com.typesafe.akka" %% "akka-slf4j" % V.akka,
          "com.typesafe.slick" %% "slick" % V.slick,
          "com.typesafe.slick" %% "slick-hikaricp" % V.slick,
          "ch.qos.logback" % "logback-classic" % V.logback,
          "com.typesafe.akka" %% "akka-actor-testkit-typed" % V.akka % Test,
          "com.typesafe.akka" %% "akka-stream-testkit" % V.akka % Test,
          "org.scalatest" %% "scalatest" % V.scalatest % Test),
        dockerBaseImage := "docker.io/library/adoptopenjdk:11-jre-hotspot",
        dockerUsername := sys.props.get("docker.username"),
        dockerRepository := sys.props.get("docker.registry"),
        dockerUpdateLatest := true,
        dockerBuildCommand := {
          if (sys.props("os.arch") != "amd64") {
            // use buildx with platform to build supported amd64 images on other CPU architectures
            // this may require that you have first run 'docker buildx create' to set docker buildx up
            dockerExecCommand.value ++ Seq("buildx", "build", "--platform=linux/amd64", "--load") ++ dockerBuildOptions.value :+ "."
          } else dockerBuildCommand.value
        }
      )
  }

  def protobufsLib(artifactName: String)(project: Project): Project = {
    project.enablePlugins(JavaAppPackaging)
      .settings(
        name := artifactName,
        organization := "com.improving",
        organizationHomepage := Some(url("https://improving.app")),
        licenses := Seq(("Apache 2", url("https://www.apache.org/licenses/LICENSE-2.0"))),
        scalaVersion := V.scala,
        scalacOptions := scala3Options,
        Compile / scalacOptions ++= scala3Options,
        Test / logBuffered := false,
        run / fork := false,
        Global / cancelable := false, // ctrl-c
        libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % V.scalatest % Test),
      )
  }
}