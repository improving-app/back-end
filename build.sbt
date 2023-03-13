ThisBuild / dynverSeparator := "-"

lazy val backEnd = project
  .in(file("."))
  .aggregate(commonTypes, tenant)

// Unused projects. Completely remove once organization and member are correctly implemented.
//lazy val organization = project.in(file("organization"))
//  .configure(C.kalix("improving-app-organization"))
//
//lazy val member = project.in(file("member"))
//  .configure(C.kalix("improving-app-member"))
//  .dependsOn(organization).
//  settings(
//    libraryDependencies ++= Seq(
//      "org.typelevel" %% "cats-core" % "2.8.0"
//    )
//  )

// This is for protobuf types defined at 'domain' scope and having cross-service applicability only.
lazy val commonTypes = project
  .in(file("common-types"))
  .configure(C.protobufsLib("improving-app-common-types"))

lazy val tenant = project
  .in(file("tenant"))
  .configure(C.akkaPersistentEntity("improving-app-tenant"))
  .dependsOn(commonTypes % "compile->compile;test->test")

(Compile / runMain) := (tenant / Compile / runMain).evaluated
onLoad in Global := (onLoad in Global).value andThen (Command.process("project tenant", _))

lazy val organization = project
  .in(file("organization"))
  .configure(C.akkaPersistentEntity("improving-app-organization"))

lazy val member = project
  .in(file("member"))
  .configure(C.akkaPersistentEntity("improving-app-member"))
  .dependsOn(organization, tenant)
