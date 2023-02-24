ThisBuild / dynverSeparator := "-"

lazy val backEnd = project.in(file("."))
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

//might need to put common types (like memberId...
lazy val commonTypes = project.in(file("commonTypes"))
  .configure(C.akkaPersistentEntity("improving-app-common-types"))

lazy val tenant = project.in(file("tenant"))
  .configure(C.protobufsLib("improving-app-tenant"))