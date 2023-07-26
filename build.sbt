ThisBuild / dynverSeparator := "-"

lazy val allServices = project
  .in(file("."))
  .aggregate(gateway, tenant, organization, member, store, event, product, gatling)

// This is for protobuf types defined at 'domain' scope and having cross-service applicability only.
lazy val commonTypes = project
  .in(file("common-types"))
  .configure(C.protobufsLib("improving-app-common-types"))

lazy val commonUtils = project
  .in(file("common-utils"))
  .configure(C.Compilation.scala, C.Testing.scalaTest)
  .configure(C.openTelemetry)

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

lazy val store = project
  .in(file("store"))
  .configure(C.akkaPersistentEntity("improving-app-store", 8083))
  .dependsOn(commonTypes)

lazy val event = project
  .in(file("event"))
  .configure(C.akkaPersistentEntity("improving-app-event", 8084))
  .dependsOn(commonTypes)

lazy val product = project
  .in(file("product"))
  .configure(C.akkaPersistentEntity("improving-app-product", 8085))
  .dependsOn(commonTypes)

lazy val gateway = project
  .in(file("gateway"))
  .configure(C.Compilation.service("improving-app-gateway", 8090))
  .dependsOn(
    commonTypes,
    tenant % "compile->compile;test->test;it->test",
    organization % "compile->compile;test->test;it->test",
    member % "compile->compile;test->test;it->test",
    store % "compile->compile;test->test;it->test",
    event % "compile->compile;test->test;it->test"
  )

lazy val gatling = project
  .in(file("gatling"))
  .configure(
    C.Compilation
      .service("improving-app-gatling", 8900, Dependencies.gatlingDependencies)
  )
  .enablePlugins(GatlingPlugin)
  .dependsOn(commonTypes, gateway)
