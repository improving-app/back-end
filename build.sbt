ThisBuild / dynverSeparator := "-"

lazy val backEnd = project
  .in(file("."))
  .aggregate(commonTypes, tenant)

// This is for protobuf types defined at 'domain' scope and having cross-service applicability only.
lazy val commonTypes = project
  .in(file("common-types"))
  .configure(C.protobufsLib("improving-app-common-types"))

lazy val tenant = project
  .in(file("tenant"))
  .configure(C.akkaPersistentEntity("improving-app-tenant"))
  .dependsOn(commonTypes)

(Compile / runMain) := (tenant / Compile / runMain).evaluated
onLoad in Global := (onLoad in Global).value.andThen(Command.process("project tenant", _))

lazy val organization = project
  .in(file("organization"))
  .configure(C.akkaPersistentEntity("improving-app-organization"))
  .dependsOn(commonTypes)

lazy val member = project
  .in(file("member"))
  .configure(C.akkaPersistentEntity("improving-app-member"))
  .dependsOn(commonTypes)
