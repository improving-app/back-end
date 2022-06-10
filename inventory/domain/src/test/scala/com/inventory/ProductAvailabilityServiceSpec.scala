package com.inventory

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import com.google.protobuf.empty.Empty
import com.inventory.commands.v1.{Ack, AddItemCommand, GetProductAvailabilityCommand, ProductAvailabilityReply, RemoveItemCommand}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.inventory.api.v1._

import scala.concurrent.Future

class ProductAvailabilityServiceSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  val testKit = ActorTestKit()

  implicit val system: ActorSystem[_] = testKit.system
  import system.executionContext

  var items: Int = 0
  val interface = new ProductAvailabilityInterface {
    override def sendGetProductAvailabilityCommand(command: GetProductAvailabilityCommand): Future[ProductAvailabilityReply] =
      command match {
        case GetProductAvailabilityCommand(_, style, _, _, _) if style.equals("") =>
          Future(ProductAvailabilityReply("", "", "", 0))
        case GetProductAvailabilityCommand(_, style, color, size, _) if !style.equals("") =>
          Future(ProductAvailabilityReply(style, color, size, items))
      }

    override def sendAddItemCommand(command: AddItemCommand): Future[Ack] = {
      items = items + 1
      Future(Ack(command.entityId))
    }

    override def sendRemoveItemCommand(command: RemoveItemCommand): Future[Ack] = {
      items = items -1
      Future(Ack(command.entityId))
    }
  }

  val service = new ProductAvailabilityServiceImpl(interface, system, "v1")

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "ProductAvailabilityService" should {
    val Style = "loads"
    val Color = "pink"
    val Size = "10"

    val add: AddItemRequest = AddItemRequest.defaultInstance
      .withStyle(Style)
      .withColor(Color)
      .withSize(Size)

    val remove: RemoveItemRequest = RemoveItemRequest.defaultInstance
      .withStyle(Style)
      .withColor(Color)
      .withSize(Size)

    "return empty reply with non existing item" in {
      val reply = service.getAvailability(GetAvailabilityRequest("", "", ""))
      reply.futureValue should ===(ProductAvailabilityResponse("", "", "", 0))
    }

    "add an item" in {
      items = 0
      val reply = service.addItem(add)
      reply.futureValue should ===(Empty.defaultInstance)
    }

    "return data when existing" in {
      items = 0
      service.addItem(add)
      val reply = service.getAvailability(GetAvailabilityRequest(Style, Color, Size))
      reply.futureValue should ===(ProductAvailabilityResponse(Style, Color, Size, 1))
    }

    "remove an item" in {
      items = 0
      service.addItem(add)
      service.addItem(add)
      val reply = service.removeItem(remove)
      reply.futureValue should ===(Empty.defaultInstance)
    }
  }
}
