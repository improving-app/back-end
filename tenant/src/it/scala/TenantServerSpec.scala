import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.improving.app.tenant.{TenantService, TenantServiceClient, TestInput}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec._
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Minutes, Span}
import org.testcontainers.containers.wait.strategy.Wait

class TenantServerSpec extends AnyFlatSpec with TestContainerForAll with Matchers with ScalaFutures {
  // Implicits for running and testing functions of the gRPC server
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(3, Minutes), interval = Span(10, Millis))
  implicit protected val system: ActorSystem = ActorSystem("testActor")

  // Definition of the container to use. This assumes the sbt command
  // 'sbt docker:publishLocal' has been run such that
  // 'docker image ls' shows "improving-app-tenant:latest" as a record
  val exposedPort = 8080 // This exposed port should match the port on Helpers.scala
  override val containerDef: GenericContainer.Def[GenericContainer] = GenericContainer.Def(
    "improving-app-tenant:latest",
    exposedPorts = Seq(exposedPort),
    waitStrategy = Wait.forListeningPort()
  )

  behavior of "TestServer in a test container"

  it should "start improving-app-tenant and expose 8080 port" in {
    withContainers { a =>
      assert(a.container.getExposedPorts.size() > 0)
      assert(a.container.getExposedPorts.get(0) == exposedPort)
    }
  }

  it should "properly process the testFunction" in {
    withContainers { a =>
      // Ensure there is an exposed port on the container before moving forward
      assert(a.container.getExposedPorts.size() > 0)
      assert(a.container.getExposedPorts.get(0) == exposedPort)

      //  The container is should be listening on host:firstMappedPort,
      //  which gets port forwarded to the docker container's 8080 port
      val host = a.container.getHost
      val port = a.container.getFirstMappedPort

      val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)

      val client: TenantService = TenantServiceClient(clientSettings)

      val testName = "testName"

      val response = client.testFunction(TestInput(testName)).futureValue
      assert(response.myOutput == s"hello $testName")
    }
  }
}
