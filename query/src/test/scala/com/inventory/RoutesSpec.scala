package com.inventory

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{ExecutionContext, Future}

class RoutesSpec extends AnyWordSpec with ScalatestRouteTest with Matchers with JsonFormats {

  override def testConfigSource: String =
    s"""
    akka {
      loglevel = DEBUG
      serializers {
        jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
      }
    }
    """.stripMargin

  final val TestData: Seq[LowInventory] = Seq(
    LowInventory("test-style1_blue_12", "test-style1", "blue", "12", 1),
    LowInventory("test-style2_blue_12", "test-style2", "blue", "12", 2),
    LowInventory("test-style3_blue_12", "test-style3", "blue", "12", 3)
  )

  class TestRepository() extends LowInventoryRepository {
    override def getAll(): Future[Seq[LowInventory]] = Future.successful(TestData)
    override def save(lowInventory: LowInventory)(implicit ec: ExecutionContext): Unit = ()
    override def delete(entityId: String)(implicit ec: ExecutionContext): Unit = ()
    override def createTable(): Future[Unit] = ???
    override def find(entityId: String): Future[Option[LowInventory]] = ???
  }

  val route = Routes.routes(new TestRepository())

  //TODO: why am I not getting any output or reports of success?
  "the service" should {
    "return low inventory data" in {
      Get() ~> route ~> check {
        responseAs[Seq[LowInventory]].shouldEqual(TestData)
      }
    }
  }
}
