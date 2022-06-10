import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin
import sbt.{Project, Test, Tests, _}
import sbt.Keys._
import kalix.sbt.KalixPlugin

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
        scalaVersion := "2.13.8",
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

}