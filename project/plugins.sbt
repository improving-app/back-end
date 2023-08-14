addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.3.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.3")
addSbtPlugin("io.gatling" % "gatling-sbt" % "4.3.3")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.6")
libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin"           % "0.11.13",
  "com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.4"
)
