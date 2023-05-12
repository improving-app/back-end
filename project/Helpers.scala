import C.{dockerSettings, Compilation, Packaging, Testing}
import Dependencies._
import akka.grpc.sbt.AkkaGrpcPlugin
import akka.grpc.sbt.AkkaGrpcPlugin.autoImport.akkaGrpcCodeGeneratorSettings
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin
import sbt.Keys.{libraryDependencies, _}
import sbt.{Def, Project, Test, Tests, _}
import sbtdynver.DynVerPlugin.autoImport.dynverSeparator
import sbtprotoc.ProtocPlugin.autoImport.PB
import scalapb.GeneratorOption.{FlatPackage, RetainSourceCodeInfo, SingleLineToProtoString}

/**
 * Contains the versions needed.
 */
object V {
  lazy val scala = "2.13.10"
  lazy val akka = "2.8.0"
  lazy val akkaHttp = "10.5.0"
  lazy val akkaManagement = "1.2.0"
  lazy val akkaProjection = "1.3.1"
  lazy val akkaPersistenceCassandra = "1.1.0"
  lazy val cors = "1.1.3"
  lazy val catsCore = "2.9.0"
  lazy val logback = "1.4.5"
  lazy val scalalogging = "3.9.5"
  lazy val scalatest = "3.2.15"
  lazy val protobufJava = "3.22.0"
  lazy val testcontainersScalaVersion = "0.40.12"
  lazy val airframeUlidVersion = "23.3.0"
}

// C for Configuration functions
object C {

  val scala3Options: Seq[String] = Seq(
    "-release:17",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlog-reflective-calls",
    "-Xlint:-byname-implicit",
    "-Wconf:cat=unused:s,any:e"
  )

  val javaOptions: Seq[String] = Seq(
    "-Xlint:unchecked",
    "-Xlint:deprecation",
    "-parameters" // for Jackson
  )

  object Compilation {

    def service(componentName: String, port: Int = 8080)(
        project: Project
    ): Project = {
      project
        .enablePlugins(AkkaGrpcPlugin, JavaAppPackaging, DockerPlugin)
        .configs(IntegrationTest.extend(Test))
        .configure(Compilation.scala)
        .configure(Testing.scalaTest)
        .configure(Packaging.docker)
        .settings(
          Defaults.itSettings,
          name := componentName,
          run / fork := true,
          scalaVersion := V.scala,
          scalacOptions := scala3Options,
          Compile / scalacOptions ++= scala3Options,
          IntegrationTest / fork := true,
          libraryDependencies ++=
            utilityDependencies ++ loggingDependencies ++ httpDependencies ++ akkaHttpTestingDependencies ++ jsonDependencies,
          dockerSettings(port)
        )
    }

    def scala(project: Project): Project = {
      project.settings(
        run / fork := true,
        Compile / scalacOptions ++= Seq(
          "-release:11",
          "-deprecation",
          "-feature",
          "-unchecked",
          "-Xlog-reflective-calls",
          "-Xlint"
        ),
        Compile / javacOptions ++= Seq(
          "-Xlint:unchecked",
          "-Xlint:deprecation",
          "-parameters" // for Jackson
        )
      )
    }

    def itService(componentName: String)(
        project: Project
    ): Project = {
      project
        .enablePlugins(AkkaGrpcPlugin, JavaAppPackaging, DockerPlugin)
        .configs(IntegrationTest)
        .configure(Compilation.scala)
        .configure(Testing.scalaTest)
        .settings(
          Defaults.itSettings,
          name := componentName,
          run / fork := true,
          scalaVersion := V.scala,
          scalacOptions := scala3Options,
          IntegrationTest / fork := true,
          Compile / scalacOptions ++= scala3Options,
          libraryDependencies ++=
            utilityDependencies ++ loggingDependencies ++ httpDependencies ++ akkaHttpTestingDependencies ++ jsonDependencies,
          libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-actor-typed" % V.akka,
            "com.dimafeng" %% "testcontainers-scala-scalatest" % V.testcontainersScalaVersion % "it, test"
          )
        )
    }

  }

  object Testing {
    def scalaTest(project: Project): Project = {
      project.settings(
        Test / parallelExecution := false,
        Test / testOptions += Tests.Argument("-oDF"),
        Test / logBuffered := false,
        libraryDependencies ++= basicTestingDependencies ++ jsonDependencies
      )
    }
  }

  object Packaging {

    def docker(project: Project): Project = {
      project.settings(
        dockerBaseImage := "docker.io/library/adoptopenjdk:11-jre-hotspot",
        dockerUsername := sys.props.get("docker.username"),
        dockerRepository := sys.props.get("docker.registry"),
        dockerUpdateLatest := true,
        dockerExposedPorts ++= Seq(8080),
        dockerBuildCommand := {
          if (sys.props("os.arch") != "amd64") {
            // use buildx with platform to build supported amd64 images on other CPU architectures
            // this may require that you have first run 'docker buildx create' to set docker buildx up
            dockerExecCommand.value ++ Seq(
              "buildx",
              "build",
              "--platform=linux/amd64",
              "--load"
            ) ++ dockerBuildOptions.value :+ "."
          } else dockerBuildCommand.value
        }
      )
    }
  }

  def akkaPersistentEntity(artifactName: String, port: Integer)(project: Project): Project = {
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
        Compile / publishLocal := true,
        Test / parallelExecution := false,
        Test / testOptions += Tests.Argument("-oDF"),
        Test / logBuffered := false,
        IntegrationTest / fork := true,
        run / fork := true,
        Global / cancelable := false, // ctrl-c
        Defaults.itSettings,
        Compile / PB.targets += scalapb.validate
          .gen(FlatPackage) -> (Compile / akkaGrpcCodeGeneratorSettings / target).value,
        libraryDependencies ++= Seq(
          "com.typesafe.akka" %% "akka-actor-typed" % V.akka,
          "com.typesafe.akka" %% "akka-actor-testkit-typed" % V.akka % Test,
          "com.typesafe.akka" %% "akka-cluster-tools" % V.akka,
          "com.typesafe.akka" %% "akka-cluster-sharding-typed" % V.akka,
          "com.typesafe.akka" %% "akka-discovery" % V.akka,
          //          "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % V.akkaManagement, // not yet necessary
          //          "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % V.akkaManagement, // not yet necessary
          "com.typesafe.akka" %% "akka-persistence" % V.akka,
          "com.typesafe.akka" %% "akka-persistence-cassandra" % V.akkaPersistenceCassandra,
          "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
          "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0",
          "com.typesafe.akka" %% "akka-persistence-query" % V.akka,
          "com.typesafe.akka" %% "akka-persistence-typed" % V.akka,
          "com.typesafe.akka" %% "akka-persistence-testkit" % V.akka % Test,
          "com.lightbend.akka" %% "akka-projection-core" % "1.3.1",
          "com.lightbend.akka" %% "akka-projection-eventsourced" % V.akkaProjection,
          "com.typesafe.akka" %% "akka-serialization-jackson" % V.akka,
          "com.typesafe.akka" %% "akka-slf4j" % V.akka,
          "com.typesafe.akka" %% "akka-http-spray-json" % V.akkaHttp,
          "com.typesafe.akka" %% "akka-stream-testkit" % V.akka % Test,
          "com.typesafe.akka" %% "akka-testkit" % V.akka % Test,
          "ch.qos.logback" % "logback-classic" % V.logback,
          "com.typesafe.scala-logging" %% "scala-logging" % V.scalalogging,
          "org.typelevel" %% "cats-core" % V.catsCore,
          "org.scalatest" %% "scalatest" % V.scalatest % "it, test",
          "com.dimafeng" %% "testcontainers-scala-scalatest" % V.testcontainersScalaVersion % "it, test",
          "com.dimafeng" %% "testcontainers-scala-cassandra" % V.testcontainersScalaVersion % "it, test",
          "org.wvlet.airframe" %% "airframe-ulid" % V.airframeUlidVersion,
        ) ++ akkaHttpTestingDependencies ++ scalaPbDependencies ++ scalaPbValidationDependencies ++ Seq(
          scalaPbCompilerPlugin
        ),
        dockerSettings(port),
      )
  }

  def dockerSettings(
      port: Int
  ): Seq[Def.Setting[_ >: Seq[Int] with Option[String] with Seq[String] with String with Boolean]] = Seq(
    dockerBaseImage := "docker.io/library/eclipse-temurin:17.0.6_10-jre",
    dockerUsername := sys.props.get("docker.username"),
    dockerRepository := sys.props.get("docker.registry"),
    dockerUpdateLatest := true,
    dockerExposedPorts ++= Seq(port),
    dockerBuildCommand := {
      if (sys.props("os.arch") != "amd64") {
        // use buildx with platform to build supported amd64 images on other CPU architectures
        // this may require that you have first run 'docker buildx create' to set docker buildx up
        dockerExecCommand.value ++ Seq(
          "buildx",
          "build",
          "--platform=linux/amd64",
          "--load"
        ) ++ dockerBuildOptions.value :+ "."
      } else dockerBuildCommand.value
    }
  )

  def protobufsLib(artifactName: String)(project: Project): Project = {
    project
      .enablePlugins(JavaAppPackaging)
      .settings(
        name := artifactName,
        organization := "com.improving",
        organizationHomepage := Some(url("https://improving.app")),
        licenses := Seq(("Apache 2", url("https://www.apache.org/licenses/LICENSE-2.0"))),
        scalaVersion := V.scala,
        scalacOptions := scala3Options,
        exportJars := true,
        Compile / scalacOptions ++= scala3Options,
        Test / logBuffered := false,
        run / fork := false,
        Global / cancelable := false, // ctrl-c
        libraryDependencies ++= Seq(
          "org.scalatest" %% "scalatest" % V.scalatest,
          "com.dimafeng" %% "testcontainers-scala-scalatest" % V.testcontainersScalaVersion,
          "com.dimafeng" %% "testcontainers-scala-cassandra" % V.testcontainersScalaVersion,
          "com.typesafe.akka" %% "akka-actor-typed" % V.akka,
          "com.typesafe.scala-logging" %% "scala-logging" % V.scalalogging,
          "com.typesafe.akka" %% "akka-http-core" % V.akkaHttp
        ),
      )
      .configure(scalapbCodeGen)
  }

  def scalapbCodeGen(project: Project): Project = {
    project.settings(
      libraryDependencies ++= scalaPbDependencies ++ scalaPbValidationDependencies,
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
