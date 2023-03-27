package com.improving.app.common.test

import akka.actor.ActorSystem
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.{Outcome, Retries}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Minutes, Span}
import org.testcontainers.containers.wait.strategy.Wait

import java.io.File

/**
 * ServiceTestContainerSpec class is the typical pattern for testing services that are gRPC services in a
 * Docker container. The service in question should be present in the docker-compose.yml.
 *
 * When running the test cases that extends this class, it is assumed that the user already has a docker container for
 * persistence (Scylla-DB) and has already run the command 'sbt clean compile && sbt docker:stage && sbt docker:publishLocal'
 *
 * To ensure completing CI tests, the first test that contains a service endpoint call must have 'taggedAs(Retryable)'.
 * Without this, the first test may say the service is unavailable while all other tests pass. Local testing should be
 * unaffected.
 * @param exposedPort The exposedPort is an Integer that should match the port exposed in docker-compose.yml
 * @param serviceName The serviceName is a String that should match the port exposed in docker-compose.yml
 */
class ServiceTestContainerSpec(exposedPort: Integer, serviceName: String)
  extends AnyFlatSpec
    with TestContainerForAll
    with Matchers
    with ScalaFutures
    with Retries{

  /**
   * The function that allows the test to be retryable. The method's functionality becomes apparent in CI testing when the first test
   * that uses a service endpoint fails do to an "unavailability". In this case, the solution is to add 'taggedAs(Retryable)'
   * to the first failing test.
   *
   * @param test
   * @return
   */
  override def withFixture(test: NoArgTest): Outcome = {
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

  // The definition of the container to use. The docker-compose file is used to start the service and waits for the appropriate message
  override val containerDef = DockerComposeContainer.Def(
    new File("../docker-compose.yml"),
    tailChildContainers = true,
    exposedServices = Seq(
      ExposedService(serviceName, exposedPort, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:$exposedPort*.", 1))
    )
  )

  /**
   * Gets the appropriate host and port of the container. A feature of TestContainer is that the actual host and port used may be
   * different from what you defined in the docker-compose file. This means that the host and port has to be resolved,
   * and only then can you use the client.
   * @param containers
   * @return
   */
  protected def getContainerHostPort(containers: Containers): (String, Integer) = {
    val host = containers.container.getServiceHost(serviceName, exposedPort)
    val port = containers.container.getServicePort(serviceName, exposedPort)

    (host, port)
  }

  /**
   * Trivial test case to see if the service is running.
   * @param a
   */
  protected def validateExposedPort(a: Containers): Unit = {
    assert(a.container.getServicePort(serviceName, exposedPort) > 0)
  }
}