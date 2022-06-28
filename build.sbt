ThisBuild / dynverSeparator := "-"

lazy val backEnd = project.in(file("."))
  .aggregate(organization, member)

lazy val organization = project.in(file("organization"))
  .configure(C.kalix("improving-app-organization"))

lazy val member = project.in(file("member"))
  .configure(C.kalix("improving-app-member"))
  .dependsOn(organization).
  settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.8.0"
    )
  )
