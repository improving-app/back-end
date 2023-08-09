ThisBuild / dynverSeparator := "-"
Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val `back-end` = project
  .in(file("."))
  .aggregate(commonUtils, gateway, tenant, organization, member, store, event, product, gatling)

// This is for protobuf types defined at 'domain' scope and having cross-service applicability only.
lazy val commonTypes = project
  .in(file("common-types"))
  .configure(C.protobufsLib("improving-app-common-types"))

lazy val commonUtils = project
  .in(file("common-utils"))
  .configure(C.scalaCompilation("improving-app-common-utils"))
  .configure(C.Compilation.scala, C.Testing.scalaTest)
  .configure(C.openTelemetry)

lazy val tenant = project
  .in(file("tenant"))
  .configure(C.akkaPersistentEntity("improving-app-tenant", 8080))
  .dependsOn(commonTypes, commonUtils)

lazy val organization = project
  .in(file("organization"))
  .configure(C.akkaPersistentEntity("improving-app-organization", 8082))
  .dependsOn(commonTypes, commonUtils)

lazy val member = project
  .in(file("member"))
  .configure(C.akkaPersistentEntity("improving-app-member", 8081))
  .dependsOn(commonTypes, commonUtils)

lazy val store = project
  .in(file("store"))
  .configure(C.akkaPersistentEntity("improving-app-store", 8083))
  .dependsOn(commonTypes, commonUtils)

lazy val event = project
  .in(file("event"))
  .configure(C.akkaPersistentEntity("improving-app-event", 8084))
  .dependsOn(commonTypes, commonUtils)

lazy val product = project
  .in(file("product"))
  .configure(C.akkaPersistentEntity("improving-app-product", 8085))
  .dependsOn(commonTypes, commonUtils)

lazy val gateway = project
  .in(file("gateway"))
  .configure(C.Compilation.service("improving-app-gateway", 8090))
  .dependsOn(
    commonTypes, commonUtils, tenant, organization, member, store, event
  )

lazy val gatling = project
  .in(file("gatling"))
  .configure(
    C.Compilation
      .service("improving-app-gatling", 8900, Dependencies.gatlingDependencies)
  )
  .enablePlugins(GatlingPlugin)
  .dependsOn(commonTypes, gateway)
