import sbt._

object Dependencies {
  object Versions {
    val akka = "2.8.0"
    val akkaHttp = "10.5.0"
    val akkaKafka = "4.0.0"
    val akkaHttpCirce = "1.39.2"
    val alpakka = "5.0.0"
    val chimney = "0.6.2"
    val commonsCodec = "1.15"
    val jsoniterScala = "2.21.3"
    val logback = "1.4.6"
    val monocle = "3.1.0"
    val pureconfig = "0.17.2"
    val scapegoat = "2.1.0"
    val scalalogging = "3.9.5"
    val scalamock = "5.2.0"
    val scalapbCompiler = "0.11.13"
    val scalatest = "3.2.15"
    val scalacheck = "1.17.0"
    val scalaxml = "2.1.0"
    val sttp = "3.8.13"
    val testcontainers = "1.17.6"
    val cats = "2.9.0"
    val circe = "0.14.5"
    val gatling = "3.9.5"
  }

  import Versions._

  val scalaPbCompilerPlugin: ModuleID =
    "com.thesamet.scalapb" %% "compilerplugin" % scalapbCompiler

  val basicTestingDependencies: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % scalatest % "it, test",
    "org.scalamock" %% "scalamock" % scalamock % Test,
    "org.scalacheck" %% "scalacheck" % scalacheck % "test",
  )

  val akkaHttpTestingDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-stream-testkit" % akka % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akka % IntegrationTest,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttp % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttp % IntegrationTest,
    "com.typesafe.akka" %% "akka-discovery" % akka % Test,
    "com.typesafe.akka" %% "akka-discovery" % akka % IntegrationTest
  )

  val loggingDependencies: Seq[ModuleID] = Seq(
    "ch.qos.logback" % "logback-classic" % logback,
    "com.typesafe.scala-logging" %% "scala-logging" % scalalogging
  )

  val utilityDependencies: Seq[ModuleID] = Seq(
    "org.scala-lang.modules" %% "scala-xml" % scalaxml,
    // "io.scalaland" %% "chimney" % chimney,
    "com.github.pureconfig" %% "pureconfig" % pureconfig,
    "org.scalacheck" %% "scalacheck" % scalacheck,
    "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
    // "dev.optics" %% "monocle-core" % monocle,
    // "dev.optics" %% "monocle-macro" % monocle,
    // noinspection SbtDependencyVersionInspection
    // "commons-codec" % "commons-codec" % commonsCodec,
    // "org.typelevel" %% "cats-core" % cats
  )

  val httpDependencies: Seq[ModuleID] = Seq(
    "com.softwaremill.sttp.client3" %% "core" % sttp,
    "com.softwaremill.sttp.client3" %% "jsoniter" % sttp,
    "com.softwaremill.sttp.client3" %% "okhttp-backend" % sttp,
    "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirce
  )

  val jsonDependencies: Seq[ModuleID] = Seq(
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % jsoniterScala,
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterScala,
    "io.circe" %% "circe-core" % circe,
    "io.circe" %% "circe-generic" % circe,
    "io.circe" %% "circe-parser" % circe,
    "com.thesamet.scalapb" %% "scalapb-json4s" % "0.12.0"
  )

  val integrationTestDependencies: Seq[ModuleID] = Seq(
    "org.testcontainers" % "testcontainers" % testcontainers % Test,
    "com.typesafe.akka" %% "akka-stream" % akka % Test,
    "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub-grpc" % alpakka % Test,
    "com.typesafe.akka" %% "akka-stream-kafka" % akkaKafka % Test,
    "com.typesafe.akka" %% "akka-http" % akkaHttp % Test,
    "com.typesafe.akka" %% "akka-http2-support" % akkaHttp % Test
  )

  val akkaTypedDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % V.akka,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % V.akka % Test
  )

  val loadTestDependencies: Seq[ModuleID] = Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % gatling % "test,it",
    "io.gatling" % "gatling-test-framework" % gatling % "test,it",
    "io.circe" %% "circe-core" % circe % "test,it",
    "io.circe" %% "circe-generic" % circe % "test,it",
    "io.circe" %% "circe-parser" % circe % "test,it",
    "com.thesamet.scalapb" %% "compilerplugin" % "0.11.13" % "test,it"
  )

  val scalaPbDependencies: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
    "com.google.protobuf" % "protobuf-java" % "3.22.2" % "protobuf",
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0",
  )

  val scalaPbValidationDependencies: Seq[ModuleID] = Seq(
    "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
    "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf",
    "com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.4" % "protobuf",
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
  )

  val gatlingDependencies: Seq[ModuleID] = Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % Versions.gatling % "test",
    "io.gatling" % "gatling-test-framework" % Versions.gatling % "test",
    "com.github.phisgr" % "gatling-grpc" % "0.16.0" % "test"
  )
}

