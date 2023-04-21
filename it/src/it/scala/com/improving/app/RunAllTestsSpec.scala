package com.improving.app

import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import com.improving.app.gateway.MemberGatewayServerSpec
import com.improving.app.member.MemberServerSpec
import com.improving.app.organization.OrganizationServerSpec
import com.improving.app.tenant.TenantServerSpec
import org.scalatest.{BeforeAndAfterAll, ParallelTestExecution, Suite}
import org.scalatest.flatspec.AnyFlatSpec
import org.testcontainers.containers.wait.strategy.Wait

import java.io.File

class RunAllTestsSpec extends AnyFlatSpec with ParallelTestExecution with BeforeAndAfterAll { self =>

  val containerDef: DockerComposeContainer.Def = DockerComposeContainer.Def(
    new File("../docker-compose.yml"),
    tailChildContainers = true,
    exposedServices = Seq(
      ExposedService("tenant-service", 8080, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8080*.", 1)),
      ExposedService("member-service", 8081, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8081*.", 1)),
      ExposedService("organization-service", 8082, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8082*.", 1))
    )
  )

  val container: DockerComposeContainer = containerDef.createContainer()

  override def afterAll(): Unit = {
    super.afterAll()
    container.stop()
  }

  override def nestedSuites: collection.immutable.IndexedSeq[Suite] = Vector(
    new TenantServerSpec {
      override val containerDef: DockerComposeContainer.Def = self.containerDef
    },
    new MemberServerSpec {
      override val containerDef: DockerComposeContainer.Def = self.containerDef
    },
    new OrganizationServerSpec {
      override val containerDef: DockerComposeContainer.Def = self.containerDef
    },
    new MemberGatewayServerSpec {
      override val containerDef: DockerComposeContainer.Def = self.containerDef
    }
  )
}
