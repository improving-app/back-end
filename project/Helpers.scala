import akka.grpc.sbt.AkkaGrpcPlugin
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin
import sbt.{Project, Test, Tests, _}
import sbt.Keys._
import kalix.sbt.KalixPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB
import scalapb.GeneratorOption.{FlatPackage, RetainSourceCodeInfo, SingleLineToProtoString}

/**
 * Contains the versions needed.
 */
object V {
  lazy val scala = "2.13.10"
  lazy val akka = "2.7.0"
  lazy val akkaHttp = "10.5.0"
  lazy val akkaManagement = "1.2.0"
  lazy val akkaProjection = "1.3.1"
  lazy val akkaPersistenceCassandra = "1.1.0"
  lazy val cors = "1.1.3"
  lazy val logback = "1.4.5"
  lazy val scalatest = "3.2.15"
  lazy val protobufJava = "3.22.0"
}

// C for Configuration functions
object C {

  val scala3Options = Seq(
    "-release:17",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlog-reflective-calls",
    "-Xlint",
    "-Werror"
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
    project
      .configs(IntegrationTest)
      .enablePlugins(AkkaGrpcPlugin, JavaAppPackaging, DockerPlugin)
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
        IntegrationTest / fork := true,
        run / fork := true,
        Global / cancelable := false, // ctrl-c
        Defaults.itSettings,
        libraryDependencies ++= Seq(
          "com.typesafe.akka" %% "akka-actor-typed" % V.akka,
          "com.typesafe.akka" %% "akka-actor-testkit-typed" % V.akka % Test,
          "com.typesafe.akka" %% "akka-cluster-tools" % V.akka,
          "com.typesafe.akka" %% "akka-cluster-sharding-typed" % V.akka,
//          "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % V.akkaManagement, // not yet necessary
//          "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % V.akkaManagement, // not yet necessary
          "com.typesafe.akka" %% "akka-persistence" % V.akka,
          "com.typesafe.akka" %% "akka-persistence-cassandra" % V.akkaPersistenceCassandra,
          "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
          "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0",
          "com.typesafe.akka" %% "akka-persistence-query" % V.akka,
          "com.typesafe.akka" %% "akka-persistence-typed" % V.akka,
          "com.lightbend.akka" %% "akka-projection-core" % "1.3.1",
          "com.lightbend.akka" %% "akka-projection-eventsourced" % V.akkaProjection,
          "com.typesafe.akka" %% "akka-serialization-jackson" % V.akka,
          "com.typesafe.akka" %% "akka-slf4j" % V.akka,
          "com.typesafe.akka" %% "akka-stream-testkit" % V.akka % Test,
          "com.typesafe.akka" %% "akka-testkit" % V.akka % Test,
          "ch.qos.logback" % "logback-classic" % V.logback,
          "org.scalatest" %% "scalatest" % V.scalatest % "it, test",
          "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
          "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.12" % "it",
          "com.typesafe.akka" %% "akka-serialization-jackson" % "2.7.0" % "it, test"
    ),
        dockerBaseImage := "docker.io/library/eclipse-temurin:17.0.6_10-jre",
        dockerUsername := sys.props.get("docker.username"),
        dockerRepository := sys.props.get("docker.registry"),
        dockerUpdateLatest := true,
        dockerExposedPorts ++= Seq(8080),
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
      .configure(scalapbCodeGen)
  }

  def scalapbCodeGen(project: Project): Project = {
    project.settings(
      libraryDependencies ++= Seq(
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
        "com.google.protobuf" % "protobuf-java" % V.protobufJava % "protobuf",
        "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
        "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0",
        "com.typesafe.akka" %% "akka-serialization-jackson" % "2.7.0"
      ),
      Compile / PB.targets := Seq(
        scalapb.gen(
          FlatPackage,
          SingleLineToProtoString,
          RetainSourceCodeInfo
        ) -> (Compile / sourceManaged).value / "scalapb",
        scalapb.validate.gen(
          FlatPackage,
          SingleLineToProtoString,
          RetainSourceCodeInfo
        ) -> (Compile / sourceManaged).value / "scalapb"
      ),
    )
  }
}