ThisBuild / dynverSeparator := "-"

lazy val allServices = project
  .in(file("."))
  .aggregate(gateway, tenant, organization, member)

// This is for protobuf types defined at 'domain' scope and having cross-service applicability only.
lazy val commonTypes = project
  .in(file("common-types"))
  .configure(C.protobufsLib("improving-app-common-types"))

lazy val tenant = project
  .in(file("tenant"))
  .configure(C.akkaPersistentEntity("improving-app-tenant", 8080))
  .dependsOn(commonTypes)

lazy val organization = project
  .in(file("organization"))
  .configure(C.akkaPersistentEntity("improving-app-organization", 8082))
  .dependsOn(commonTypes)

lazy val member = project
  .in(file("member"))
  .configure(C.akkaPersistentEntity("improving-app-member", 8081))
  .dependsOn(commonTypes)

lazy val gateway = project
  .in(file("gateway"))
  .configure(C.service("improving-app-gateway", 8090))
  .dependsOn(commonTypes, member % "compile->compile;test->test;it->test")

lazy val it = project
  .in(file("it"))
  .configure(C.itService("improving-app-integration-tests"))
  .dependsOn(
    organization % "it->it",
    tenant % "it->it",
    member % "it->it",
    gateway % "it->it",
    commonTypes % "it->compile"
  )
