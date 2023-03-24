import akka.actor.ActorSystem
import com.improving.app.common.domain.{CaPostalCodeImpl, Contact, OrganizationId, PostalCodeMessageImpl}
import com.improving.app.tenant.domain.{ActivateTenant, AddOrganizations, EstablishTenant, RemoveOrganizations, SuspendTenant, UpdateAddress, UpdatePrimaryContact, UpdateTenantName}
import akka.grpc.GrpcClientSettings
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.improving.app.common.domain.{Address, MemberId, TenantId}
import com.improving.app.tenant.api.{TenantService, TenantServiceClient}
import org.scalatest.Retries

import java.io.File
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec._
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable
import org.scalatest.time.{Millis, Minutes, Span}
import org.testcontainers.containers.wait.strategy.Wait

import scala.util.Random

class TenantServerSpec extends AnyFlatSpec with TestContainerForAll with Matchers with ScalaFutures with Retries {
  override def withFixture(test: NoArgTest) = {
    if (isRetryable(test))
      withRetry {
        super.withFixture(test)
      }
    else
      super.withFixture(test)
  }

  // Implicits for running and testing functions of the gRPC server
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(3, Minutes), interval = Span(10, Millis))
  implicit protected val system: ActorSystem = ActorSystem("testActor")

  // Definition of the container to use. This assumes the sbt command
  // 'sbt docker:publishLocal' has been run such that
  // 'docker image ls' shows "improving-app-tenant:latest" as a record
  // Since the tenant-service uses cassandra for persistence, a docker compose file is used to also run the scylla db
  // container
  val exposedPort = 8080 // This exposed port should match the port on Helpers.scala
  val serviceName = "tenant-service"
  override val containerDef =
    DockerComposeContainer.Def(
      new File("../docker-compose.yml"),
      tailChildContainers = true,
      exposedServices = Seq(
        ExposedService(serviceName, exposedPort, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:$exposedPort*.", 1))
      )
    )

  private def getClient(containers: Containers): TenantService = {
    val host = containers.container.getServiceHost(serviceName, exposedPort)
    val port = containers.container.getServicePort(serviceName, exposedPort)

    val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)
    TenantServiceClient(clientSettings)
  }

  behavior of "TestServer in a test container"

  it should "expose a port for tenant-service" in {
    withContainers { a =>
      assert(a.container.getServicePort(serviceName, exposedPort) > 0)
    }
  }

  it should "properly process UpdateTenantName" taggedAs(Retryable) in {
    withContainers {containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.updateTenantName(UpdateTenantName(
        tenantId = Some(TenantId(tenantId)),
        newName = "newName",
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.oldName shouldBe ""
      response.newName shouldBe "newName"
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process UpdatePrimaryContact" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.updatePrimaryContact(UpdatePrimaryContact(
        tenantId = Some(TenantId(tenantId)),
        newContact = Some(
          Contact(
            firstName = "firstName1",
            lastName = "lastName1",
            emailAddress = Some("test1@test.com"),
            phone = Some("111-111-1112"),
            userName = "contactUsername1"
          )
        ),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.oldContact shouldBe None
      response.newContact shouldBe Some(
        Contact(
          firstName = "firstName1",
          lastName = "lastName1",
          emailAddress = Some("test1@test.com"),
          phone = Some("111-111-1112"),
          userName = "contactUsername1"
        )
      )
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process UpdateAddress" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.updateAddress(UpdateAddress(
        tenantId = Some(TenantId(tenantId)),
        newAddress = Some(
          Address(
            line1 = "line3",
            line2 = "line4",
            city = "city1",
            stateProvince = "stateProvince1",
            country = "country1",
            postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
          )
        ),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.oldAddress shouldBe None
      response.newAddress shouldBe Some(
        Address(
          line1 = "line3",
          line2 = "line4",
          city = "city1",
          stateProvince = "stateProvince1",
          country = "country1",
          postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
        )
      )
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process AddOrganizations" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.addOrganizations(AddOrganizations(
        tenantId = Some(TenantId(tenantId)),
        orgId = Seq(OrganizationId("org1")),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.newOrgsList shouldBe Seq(OrganizationId("org1"))
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process RemoveOrganizations" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.addOrganizations(AddOrganizations(
        tenantId = Some(TenantId(tenantId)),
        orgId = Seq(OrganizationId("org1")),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      response.newOrgsList shouldBe Seq(OrganizationId("org1"))

      val removeResponse = client.removeOrganizations(RemoveOrganizations(
        tenantId = Some(TenantId(tenantId)),
        orgId = Seq(OrganizationId("org1")),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      removeResponse.tenantId shouldBe Some(TenantId(tenantId))
      removeResponse.newOrgsList shouldBe Seq.empty
      removeResponse.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process ActivateTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val updateTenantNameResponse = client.updateTenantName(UpdateTenantName(
        tenantId = Some(TenantId(tenantId)),
        newName = "newName",
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      updateTenantNameResponse.tenantId shouldBe Some(TenantId(tenantId))
      updateTenantNameResponse.newName shouldBe "newName"

      val updatePrimaryContactResponse = client.updatePrimaryContact(UpdatePrimaryContact(
        tenantId = Some(TenantId(tenantId)),
        newContact = Some(
          Contact(
            firstName = "firstName1",
            lastName = "lastName1",
            emailAddress = Some("test1@test.com"),
            phone = Some("111-111-1112"),
            userName = "contactUsername1"
          )
        ),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      updatePrimaryContactResponse.tenantId shouldBe Some(TenantId(tenantId))
      updatePrimaryContactResponse.newContact shouldBe Some(
        Contact(
          firstName = "firstName1",
          lastName = "lastName1",
          emailAddress = Some("test1@test.com"),
          phone = Some("111-111-1112"),
          userName = "contactUsername1"
        )
      )

      val updateAddressResponse = client.updateAddress(UpdateAddress(
        tenantId = Some(TenantId(tenantId)),
        newAddress = Some(
          Address(
            line1 = "line3",
            line2 = "line4",
            city = "city1",
            stateProvince = "stateProvince1",
            country = "country1",
            postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
          )
        ),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      updateAddressResponse.tenantId shouldBe Some(TenantId(tenantId))
      updateAddressResponse.newAddress shouldBe Some(
        Address(
          line1 = "line3",
          line2 = "line4",
          city = "city1",
          stateProvince = "stateProvince1",
          country = "country1",
          postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
        )
      )

      val response = client.activateTenant(ActivateTenant(
        tenantId = Some(TenantId(tenantId)),
        activatingUser = Some(MemberId("activatingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("activatingUser"))
    }
  }

  it should "properly process SuspendTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.suspendTenant(SuspendTenant(
        tenantId = Some(TenantId(tenantId)),
        suspensionReason = "reason",
        suspendingUser = Some(MemberId("suspendingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("suspendingUser"))
    }
  }
}