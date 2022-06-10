ThisBuild / dynverSeparator := "-"

lazy val backEnd = project.in(file("."))
  .aggregate(organization)

lazy val organization = project.in(file("organization"))
  .configure(C.kalix("improving-app-organization"))
