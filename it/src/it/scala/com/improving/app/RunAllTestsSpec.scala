package com.improving.app

import akka.actor.ActorSystem
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.{Container, DockerComposeContainer, ExposedService, ForAllTestContainer}
import com.improving.app.gateway.MemberGatewayServerSpec
import com.improving.app.member.MemberServerSpec
import com.improving.app.organization.OrganizationServerSpec
import com.improving.app.tenant.TenantServerSpec
import org.scalatest.{ParallelTestExecution, Suite}
import org.scalatest.flatspec.AnyFlatSpec
import org.testcontainers.containers.wait.strategy.Wait

import java.io.File

class RunAllTestsSpec extends AnyFlatSpec with ParallelTestExecution { self =>

  val containerDef: DockerComposeContainer.Def = DockerComposeContainer.Def(
    new File("../docker-compose.yml"),
    tailChildContainers = true,
    exposedServices = Seq(
      ExposedService("tenant-service", 8080, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8080*.", 1)),
      ExposedService("member-service", 8081, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8081*.", 1)),
      ExposedService("organization-service", 8082, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8082*.", 1))
    )
  )

  containerDef.start()

  override def nestedSuites: collection.immutable.IndexedSeq[Suite] = Vector(
    new TenantServerSpec(containerDef),
    new MemberServerSpec(containerDef),
    new OrganizationServerSpec(containerDef),
    new MemberGatewayServerSpec {
      override val containerDef: DockerComposeContainer.Def = self.containerDef
    }
  )
}
