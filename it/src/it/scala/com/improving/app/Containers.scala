package com.improving.app

import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import org.testcontainers.containers.wait.strategy.Wait

import java.io.File

object Containers {
  val containerDefForAll: DockerComposeContainer.Def = DockerComposeContainer.Def(
    new File("../docker-compose.yml"),
    tailChildContainers = true,
    exposedServices = Seq(
      ExposedService("tenant-service", 8080, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8080*.", 1)),
      ExposedService("member-service", 8081, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8081*.", 1)),
      ExposedService("organization-service", 8082, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8082*.", 1))
    )
  )
}
