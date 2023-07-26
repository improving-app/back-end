package com.improving.app.store.domain

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import com.improving.app.common.domain.{EventId, MemberId, OrganizationId, Sku, StoreId}
import com.improving.app.store.domain.Store.StoreRequestEnvelope
import com.improving.app.store.domain.TestData.baseStoreInfo
import com.improving.app.store.domain.util.{EditableStoreInfoUtil, StoreInfoUtil}
import com.typesafe.config.{Config, ConfigFactory, Optional}
import org.scalatest.{stats, BeforeAndAfterAll}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.util.Random

object StoreSpec {
  val config: Config = ConfigFactory.parseString("""
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on

    akka.actor.serialization-bindings{
      "com.improving.app.common.serialize.PBMsgSerializable" = proto
    }
  """)
}
class StoreSpec
    extends ScalaTestWithActorTestKit(StoreSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers {
  override def afterAll(): Unit = testKit.shutdownTestKit()

  def createTestVariables(): (StoreId, ActorRef[StoreRequestEnvelope], TestProbe[StatusReply[StoreEvent]]) = {
    val storeId = Random.nextString(31)
    val p = this.testKit.spawn(Store(PersistenceId.ofUniqueId(storeId)))
    val probe = this.testKit.createTestProbe[StatusReply[StoreEvent]]()
    (StoreId(storeId), p, probe)
  }

  trait CreatedSpec {
    val (storeId, p, probe) = createTestVariables()

    val response: StatusReply[StoreEvent] = createStore(storeId, p, probe)
  }

  trait CreatedNoInfoSpec {
    val (storeId, p, probe) = createTestVariables()

    val response: StatusReply[StoreEvent] =
      createStore(storeId, p, probe, storeInfo = EditableStoreInfo.defaultInstance)
  }

  trait ReadiedSpec {
    val (storeId, p, probe) = createTestVariables()

    createStore(storeId, p, probe)

    val response: StatusReply[StoreEvent] = readyStore(storeId, p, probe)
  }

  trait NewInfoForEditSpec {
    val newName: Option[String] = Some("xxxx")
    val newDesc: Option[String] = Some("xxxx")
    val newProducts: Seq[Sku] = Seq(Sku(UUID.randomUUID().toString))
    val newEvent: Option[EventId] = Some(EventId(UUID.randomUUID().toString))
    val newSponsoringOrg: Option[OrganizationId] = Some(OrganizationId("otherOrg"))

    val baseNewInfo = EditableStoreInfo(
      newName,
      newDesc,
      newProducts,
      newEvent,
      newSponsoringOrg
    )
  }

  private val baseEditableInfo: EditableStoreInfo = EditableStoreInfo(
    Some(baseStoreInfo.name),
    Some(baseStoreInfo.description),
    baseStoreInfo.products,
    baseStoreInfo.event,
    baseStoreInfo.sponsoringOrg
  )

  def createStore(
      storeId: StoreId,
      p: ActorRef[StoreRequestEnvelope],
      probe: TestProbe[StatusReply[StoreEvent]],
      storeInfo: EditableStoreInfo = baseEditableInfo,
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      CreateStore(
        storeId = Some(storeId),
        onBehalfOf = Some(MemberId("creatingUser")),
        info = Some(storeInfo)
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    if (checkSuccess) assert(response.isSuccess)
    else assert(response.isError)
    response
  }

  def readyStore(
      storeId: StoreId,
      p: ActorRef[StoreRequestEnvelope],
      probe: TestProbe[StatusReply[StoreEvent]],
      info: EditableStoreInfo = EditableStoreInfo(),
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      MakeStoreReady(
        storeId = Some(storeId),
        onBehalfOf = Some(MemberId("readyingUser")),
        info = Some(info)
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    if (checkSuccess) assert(response.isSuccess)
    else assert(response.isError)
    response
  }

  def openStore(
      storeId: StoreId,
      p: ActorRef[StoreRequestEnvelope],
      probe: TestProbe[StatusReply[StoreEvent]],
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      OpenStore(
        storeId = Some(storeId),
        onBehalfOf = Some(MemberId("openingUser")),
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    if (checkSuccess) assert(response.isSuccess)
    else assert(response.isError)
    response
  }

  def closeStore(
      storeId: StoreId,
      p: ActorRef[StoreRequestEnvelope],
      probe: TestProbe[StatusReply[StoreEvent]],
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      CloseStore(
        storeId = Some(storeId),
        onBehalfOf = Some(MemberId("closingUser")),
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    if (checkSuccess) assert(response.isSuccess)
    else assert(response.isError)
    response
  }

  def deleteStore(
      storeId: StoreId,
      p: ActorRef[StoreRequestEnvelope],
      probe: TestProbe[StatusReply[StoreEvent]],
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      DeleteStore(
        storeId = Some(storeId),
        onBehalfOf = Some(MemberId("deletingUser")),
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    if (checkSuccess) assert(response.isSuccess)
    else assert(response.isError)
    response
  }

  def terminateStore(
      storeId: StoreId,
      p: ActorRef[StoreRequestEnvelope],
      probe: TestProbe[StatusReply[StoreEvent]],
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      TerminateStore(
        storeId = Some(storeId),
        onBehalfOf = Some(MemberId("terminatingUser")),
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    if (checkSuccess) assert(response.isSuccess)
    else assert(response.isError)
    response
  }

  def editStoreInfo(
      storeId: StoreId,
      p: ActorRef[StoreRequestEnvelope],
      probe: TestProbe[StatusReply[StoreEvent]],
      info: EditableStoreInfo = baseEditableInfo,
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      EditStoreInfo(
        storeId = Some(storeId),
        onBehalfOf = Some(MemberId("editingUser")),
        newInfo = Some(info)
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    if (checkSuccess) assert(response.isSuccess)
    else assert(response.isError)
    response
  }

  "A Store Actor" when {
    // Should this also error out when other stores have the same name?
    "in the UNINITIALIZED State" when {
      "executing CreateStore command" should {
        "error for an unauthorized updating user" ignore {
          val (storeId, p, probe) = createTestVariables()

          p ! Store.StoreRequestEnvelope(
            EditStoreInfo(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Some(EditableStoreInfo()),
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }
        "succeed for the golden path and return the proper response" in {
          val (storeId, p, probe) = createTestVariables()

          val response = createStore(storeId, p, probe)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.isStoreCreated)
          successVal.asMessage.sealedValue.storeCreated.get.getStoreId shouldEqual storeId
          successVal.asMessage.sealedValue.storeCreated.get.metaInfo.map(_.getCreatedBy) shouldEqual Some(
            MemberId("creatingUser")
          )
        }

        "error for a store that is already created" in {
          val (storeId, p, probe) = createTestVariables()

          createStore(storeId, p, probe)

          val response2 = createStore(storeId, p, probe, checkSuccess = false)

          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Message type not supported in draft state"
        }
      }

      "executing any command other than create" should {
        "error as not created" in {
          val (storeId, p, probe) = createTestVariables()

          val commands = Seq(
            MakeStoreReady(Some(storeId), Some(MemberId("user"))),
            OpenStore(Some(storeId), Some(MemberId("user"))),
            CloseStore(Some(storeId), Some(MemberId("user"))),
            DeleteStore(Some(storeId), Some(MemberId("user"))),
            TerminateStore(Some(storeId), Some(MemberId("user"))),
            EditStoreInfo(
              Some(storeId),
              Some(MemberId("user")),
              Some(EditableStoreInfo())
            )
          )

          commands.foreach(command => {
            p ! Store.StoreRequestEnvelope(command, probe.ref)

            val response = probe.receiveMessage()

            assert(response.isError)

            val responseError = response.getError
            responseError.getMessage shouldEqual "Message type not supported in empty state"
          })
        }
      }
    }

    "in the DRAFT state" when {
      "executing MakeStoreReady" should {
        "error for an unauthorized readying user" ignore new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            MakeStoreReady(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Some(EditableStoreInfo()),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path with no update" in new CreatedSpec {
          val response2: StatusReply[StoreEvent] =
            readyStore(storeId, p, probe)

          val successVal: StoreIsReady = response2.getValue.asInstanceOf[StoreIsReady]

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("readyingUser"))
          successVal.info shouldEqual Some(baseEditableInfo.toInfo)
        }

        "succeed for updating all fields" in new CreatedSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] =
            readyStore(storeId, p, probe, baseNewInfo)

          val successVal: StoreIsReady = response2.getValue.asInstanceOf[StoreIsReady]

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("readyingUser"))
          successVal.info shouldEqual Some(baseNewInfo.toInfo)
        }
      }

      "executing EditStoreInfo command" should {
        "error for an unauthorized updating user" ignore new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            EditStoreInfo(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Some(EditableStoreInfo()),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for an empty edit and return the proper response" in new CreatedSpec {
          val state: EditableStoreInfo = baseEditableInfo

          val response2: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo())

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("editingUser"))
          successVal.newInfo shouldEqual Some(state)
        }

        "succeed for an edit of all fields and return the proper response" in new CreatedSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, baseNewInfo)
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.getStoreId shouldEqual storeId
          successVal.newInfo shouldEqual Some(baseNewInfo)
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("editingUser"))
        }

        "succeed for a partial edit and return the proper response" in new CreatedSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo(name = newName))

          val successVal2: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal2.getStoreId shouldEqual storeId
          successVal2.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("editingUser"))
          successVal2.newInfo.flatMap(_.name) shouldEqual newName

          val response3: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo(description = newDesc))

          val successVal3: StoreInfoEdited = response3.getValue.asInstanceOf[StoreInfoEdited]

          successVal3.getStoreId shouldEqual storeId
          successVal3.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("editingUser"))
          successVal3.newInfo.flatMap(_.description) shouldEqual newDesc

          val response4: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo(sponsoringOrg = newSponsoringOrg))

          val successVal4: StoreInfoEdited = response4.getValue.asInstanceOf[StoreInfoEdited]

          successVal4.getStoreId shouldEqual storeId
          successVal4.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("editingUser"))
          successVal4.newInfo.flatMap(_.sponsoringOrg) shouldEqual newSponsoringOrg
        }
      }

      "executing AddProductsToStore" should {
        "error for an unauthorized readying user" ignore new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            AddProductsToStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Seq(Sku(UUID.randomUUID().toString)),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path" in new CreatedSpec {
          val newSku: Sku = Sku(UUID.randomUUID().toString)

          p ! Store.StoreRequestEnvelope(
            AddProductsToStore(
              Some(storeId),
              Some(MemberId("productAddingUser")),
              Seq(newSku),
            ),
            probe.ref
          )
          val successVal: ProductsAddedToStore = probe.receiveMessage().getValue.asInstanceOf[ProductsAddedToStore]
          assert(response.isSuccess)

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("productAddingUser"))
          successVal.info.map(_.getEditableInfo) shouldEqual Some(
            baseEditableInfo.copy(products = baseEditableInfo.products :+ newSku)
          )
        }
      }

      "executing RemoveProductsFromStore" should {
        "error for an unauthorized readying user" ignore new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            RemoveProductsFromStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              baseEditableInfo.products,
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path" in new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            RemoveProductsFromStore(
              Some(storeId),
              Some(MemberId("productRemovingUser")),
              baseEditableInfo.products,
            ),
            probe.ref
          )
          val successVal: ProductsRemovedFromStore =
            probe.receiveMessage().getValue.asInstanceOf[ProductsRemovedFromStore]
          assert(response.isSuccess)

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("productRemovingUser"))
          successVal.info.map(_.getEditableInfo) shouldEqual Some(baseEditableInfo.copy(products = Seq()))
        }
      }
    }

    "in the Created state" when {
      "executing MakeStoreReady command" should {
        "error and return the proper response when Store is not yet created" in {
          val (storeId, p, probe) = createTestVariables()
          val response2: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in empty state"
        }
      }

      "executing MakeStoreReady command" should {
        "error for an unauthorized updating user" ignore new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            MakeStoreReady(
              storeId = Some(storeId),
              onBehalfOf = Some(MemberId("unauthorized")),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()
          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to make Store ready"
        }

        "errorsss on incomplete info" in new CreatedNoInfoSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "No associated name"

          editStoreInfo(storeId, p, probe, EditableStoreInfo(name = Some(baseStoreInfo.name)))

          val response3: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError2: Throwable = response3.getError
          responseError2.getMessage shouldEqual "No associated description"

          editStoreInfo(
            storeId,
            p,
            probe,
            EditableStoreInfo(name = None, description = Some(baseStoreInfo.description))
          )

          val response4: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError3: Throwable = response4.getError
          responseError3.getMessage shouldEqual "No associated sponsoring org"
        }

        "error on incomplete info" in new CreatedNoInfoSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "No associated name"

          editStoreInfo(storeId, p, probe, EditableStoreInfo(name = Some(baseStoreInfo.name)))

          val response3: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError2: Throwable = response3.getError
          responseError2.getMessage shouldEqual "No associated description"

          editStoreInfo(
            storeId,
            p,
            probe,
            EditableStoreInfo(name = None, description = Some(baseStoreInfo.description))
          )

          val response4: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError3: Throwable = response4.getError
          responseError3.getMessage shouldEqual "No associated sponsoring org"
        }

        "succeed and return the proper response" in new ReadiedSpec {
          val successVal: StoreEvent = response.getValue
          assert(successVal.asMessage.sealedValue.isStoreIsReady)

          val storeReady: StoreIsReady = successVal.asMessage.sealedValue.storeIsReady.get

          storeReady.getStoreId shouldEqual storeId

          val storeReadyMeta: StoreMetaInfo = storeReady.getMetaInfo

          storeReadyMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeReadyMeta.getLastUpdatedBy shouldEqual MemberId("readyingUser")
        }
      }

      "executing EditStoreInfo command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          // Test command in question
          p ! Store.StoreRequestEnvelope(
            EditStoreInfo(
              storeId = Some(storeId),
              onBehalfOf = Some(MemberId("unauthorized")),
              newInfo = Some(
                baseEditableInfo
              )
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for an empty edit and return the proper response" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, EditableStoreInfo())

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseStoreInfo.toEditable)
        }

        "succeed for an edit of all fields and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, baseNewInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.getNewInfo shouldEqual baseNewInfo
        }

        "succeed for a partial edit and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseStoreInfo.copy(name = updatedInfo.getName).toEditable)
        }
      }
    }

    "in the Ready state" when {
      "executing MakeStoreReady command" should {
        "error for an unauthorized updating user" ignore new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            MakeStoreReady(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()
          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to open Store"
        }

        "succeed and return the proper response" in new ReadiedSpec {
          val successVal: StoreEvent = response.getValue
          assert(successVal.asMessage.sealedValue.isStoreIsReady)

          val storeReady: StoreIsReady = successVal.asMessage.sealedValue.storeIsReady.get

          storeReady.getStoreId shouldEqual storeId
          val storeReadyMeta: StoreMetaInfo = storeReady.getMetaInfo

          storeReadyMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeReadyMeta.getLastUpdatedBy shouldEqual MemberId("readyingUser")
        }

        "error when readying a Store that is already ready" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in ready state"
        }
      }

      "executing EditStoreInfo command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          readyStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            EditStoreInfo(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Some(
                baseEditableInfo
              )
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()
          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for an empty edit and return the proper response" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, EditableStoreInfo())
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseStoreInfo.toEditable)
        }

        "succeed for an edit of all fields and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, baseNewInfo)
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.getNewInfo shouldEqual baseNewInfo
        }

        "succeed for a partial edit and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseStoreInfo.copy(name = updatedInfo.name.get).toEditable)
        }
      }

      "executing AddProductsToStore" should {
        "error for an unauthorized readying user" ignore new CreatedSpec {
          readyStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            AddProductsToStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Seq(Sku(UUID.randomUUID().toString)),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path" in new CreatedSpec {
          readyStore(storeId, p, probe)

          val newSku: Sku = Sku(UUID.randomUUID().toString)

          p ! Store.StoreRequestEnvelope(
            AddProductsToStore(
              Some(storeId),
              Some(MemberId("productAddingUser")),
              Seq(newSku),
            ),
            probe.ref
          )
          val successVal: ProductsAddedToStore = probe.receiveMessage().getValue.asInstanceOf[ProductsAddedToStore]
          assert(response.isSuccess)

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("productAddingUser"))
          successVal.info.map(_.getInfo) shouldEqual Some(
            baseEditableInfo.copy(products = baseEditableInfo.products :+ newSku).toInfo
          )
        }
      }

      "executing RemoveProductsFromStore" should {
        "error for an unauthorized readying user" ignore new CreatedSpec {
          readyStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            RemoveProductsFromStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              baseEditableInfo.products,
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path" in new CreatedSpec {
          readyStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            RemoveProductsFromStore(
              Some(storeId),
              Some(MemberId("productRemovingUser")),
              baseEditableInfo.products,
            ),
            probe.ref
          )
          val successVal: ProductsRemovedFromStore =
            probe.receiveMessage().getValue.asInstanceOf[ProductsRemovedFromStore]
          assert(response.isSuccess)

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("productRemovingUser"))
          successVal.info.map(_.getInfo) shouldEqual Some(baseEditableInfo.copy(products = Seq()).toInfo)
        }
      }
    }

    "in the Open state" when {
      "executing OpenStore command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          p ! Store.StoreRequestEnvelope(
            OpenStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to open Store"
        }

        "succeed and return the proper response" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] =
            openStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreOpened)

          val storeOpened: StoreOpened = successVal.asMessage.sealedValue.storeOpened.get

          storeOpened.getStoreId shouldEqual storeId

          val storeOpenedMeta: StoreMetaInfo = storeOpened.getMetaInfo

          storeOpenedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeOpenedMeta.getLastUpdatedBy shouldEqual MemberId("openingUser")
        }

        "error when opening a Store that is already open" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] =
            openStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreOpened)

          val response3: StatusReply[StoreEvent] = openStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response3.getError
          responseError.getMessage shouldEqual "Message type not supported in open state"
        }
      }

      "executing ReadyStore command" should {
        "error as already in Ready state" in new ReadiedSpec {
          openStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in open state"
        }
      }

      "executing EditStoreInfo command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          openStore(storeId, p, probe)

          // Test command in question
          p ! Store.StoreRequestEnvelope(
            EditStoreInfo(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Some(EditableStoreInfo())
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for an empty edit and return the proper response" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, EditableStoreInfo())
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseStoreInfo.toEditable)
        }

        "succeed for an edit of all fields and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, baseEditableInfo)
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseEditableInfo)
        }

        "succeed for a partial edit and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseStoreInfo.copy(name = updatedInfo.getName).toEditable)
        }
      }

      "executing AddProductsToStore" should {
        "error for an unauthorized readying user" ignore new ReadiedSpec {
          openStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            AddProductsToStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Seq(Sku(UUID.randomUUID().toString)),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path" in new ReadiedSpec {
          openStore(storeId, p, probe)

          val newSku: Sku = Sku(UUID.randomUUID().toString)

          p ! Store.StoreRequestEnvelope(
            AddProductsToStore(
              Some(storeId),
              Some(MemberId("productAddingUser")),
              Seq(newSku),
            ),
            probe.ref
          )
          val successVal: ProductsAddedToStore = probe.receiveMessage().getValue.asInstanceOf[ProductsAddedToStore]
          assert(response.isSuccess)

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("productAddingUser"))
          successVal.info.map(_.getInfo) shouldEqual Some(
            baseEditableInfo.copy(products = baseEditableInfo.products :+ newSku).toInfo
          )
        }
      }

      "executing RemoveProductsFromStore" should {
        "error for an unauthorized readying user" ignore new ReadiedSpec {
          openStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            RemoveProductsFromStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              baseEditableInfo.products,
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path" in new ReadiedSpec {
          openStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            RemoveProductsFromStore(
              Some(storeId),
              Some(MemberId("productRemovingUser")),
              baseEditableInfo.products,
            ),
            probe.ref
          )
          val successVal: ProductsRemovedFromStore =
            probe.receiveMessage().getValue.asInstanceOf[ProductsRemovedFromStore]
          assert(response.isSuccess)

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("productRemovingUser"))
          successVal.info.map(_.getInfo) shouldEqual Some(baseEditableInfo.copy(products = Seq()).toInfo)
        }
      }
    }

    "in the Closed state" when {
      "executing CloseStore command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          p ! Store.StoreRequestEnvelope(
            CloseStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()
          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to open Store"
        }

        "succeed and return the proper response" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] =
            closeStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreClosed)

          val storeClosed: StoreClosed = successVal.asMessage.sealedValue.storeClosed.get

          storeClosed.getStoreId shouldEqual storeId

          val storeClosedMeta: StoreMetaInfo = storeClosed.getMetaInfo

          storeClosedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeClosedMeta.getLastUpdatedBy shouldEqual MemberId("closingUser")
        }

        "succeed and return the proper response after store is opened" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] = openStore(storeId, p, probe)
          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreOpened)

          val response3: StatusReply[StoreEvent] =
            closeStore(storeId, p, probe)

          val successVal2: StoreEvent = response3.getValue
          assert(successVal2.asMessage.sealedValue.isStoreClosed)

          val storeClosed: StoreClosed = successVal2.asMessage.sealedValue.storeClosed.get

          storeClosed.getStoreId shouldEqual storeId

          val storeClosedMeta: StoreMetaInfo = storeClosed.getMetaInfo

          storeClosedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeClosedMeta.getLastUpdatedBy shouldEqual MemberId("closingUser")
        }
      }

      "executing OpenStore command" should {
        "succeed in opening store when never previously opened" in new ReadiedSpec {
          closeStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = openStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreOpened)

          val storeOpened: StoreOpened = successVal.asMessage.sealedValue.storeOpened.get

          storeOpened.getStoreId shouldEqual storeId

          val storeOpenedMeta: StoreMetaInfo = storeOpened.getMetaInfo

          storeOpenedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeOpenedMeta.getLastUpdatedBy shouldEqual MemberId("openingUser")
        }

        "succeed in opening store when initially opened" in new ReadiedSpec {
          openStore(storeId, p, probe)
          closeStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = openStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreOpened)

          val storeOpened: StoreOpened = successVal.asMessage.sealedValue.storeOpened.get

          storeOpened.getStoreId shouldEqual storeId
          val storeOpenedMeta: StoreMetaInfo = storeOpened.getMetaInfo

          storeOpenedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeOpenedMeta.getLastUpdatedBy shouldEqual MemberId("openingUser")
        }
      }

      "executing ReadyStore command" should {
        "error as already in Ready state" in new ReadiedSpec {
          closeStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in closed state"
        }
      }

      "executing EditStoreInfo command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          closeStore(storeId, p, probe)
          // Test command in question
          p ! Store.StoreRequestEnvelope(
            EditStoreInfo(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Some(EditableStoreInfo())
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for an empty edit and return the proper response" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, EditableStoreInfo())
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseEditableInfo)
        }

        "succeed for an edit of all fields and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, baseNewInfo)
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.getNewInfo shouldEqual baseNewInfo
        }

        "succeed for a partial edit and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.newInfo shouldEqual Some(baseStoreInfo.copy(name = updatedInfo.getName).toEditable)
        }
      }

      "executing AddProductsToStore" should {
        "error for an unauthorized readying user" ignore new ReadiedSpec {
          closeStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            AddProductsToStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              Seq(Sku(UUID.randomUUID().toString)),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path" in new ReadiedSpec {
          closeStore(storeId, p, probe)

          val newSku: Sku = Sku(UUID.randomUUID().toString)

          p ! Store.StoreRequestEnvelope(
            AddProductsToStore(
              Some(storeId),
              Some(MemberId("productAddingUser")),
              Seq(newSku),
            ),
            probe.ref
          )
          val successVal: ProductsAddedToStore = probe.receiveMessage().getValue.asInstanceOf[ProductsAddedToStore]
          assert(response.isSuccess)

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("productAddingUser"))
          successVal.info.map(_.getInfo) shouldEqual Some(
            baseEditableInfo.copy(products = baseEditableInfo.products :+ newSku).toInfo
          )
        }
      }

      "executing RemoveProductsFromStore" should {
        "error for an unauthorized readying user" ignore new ReadiedSpec {
          closeStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            RemoveProductsFromStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser")),
              baseEditableInfo.products,
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for golden path" in new ReadiedSpec {
          closeStore(storeId, p, probe)

          p ! Store.StoreRequestEnvelope(
            RemoveProductsFromStore(
              Some(storeId),
              Some(MemberId("productRemovingUser")),
              baseEditableInfo.products,
            ),
            probe.ref
          )
          val successVal: ProductsRemovedFromStore =
            probe.receiveMessage().getValue.asInstanceOf[ProductsRemovedFromStore]
          assert(response.isSuccess)

          successVal.getStoreId shouldEqual storeId
          successVal.metaInfo.map(_.getLastUpdatedBy) shouldEqual Some(MemberId("productRemovingUser"))
          successVal.info.map(_.getInfo) shouldEqual Some(baseEditableInfo.copy(products = Seq()).toInfo)
        }
      }
    }

    "in the Deleted state" when {
      "executing DeleteStore command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          closeStore(storeId, p, probe)
          p ! Store.StoreRequestEnvelope(
            DeleteStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()
          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to open Store"
        }

        "error when Store is Ready" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] =
            deleteStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Store must be closed before deleting"
        }

        "error when Store is Open" in new ReadiedSpec {
          openStore(storeId, p, probe)
          val response2: StatusReply[StoreEvent] =
            deleteStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Store must be closed before deleting"
        }

        "succeed and return the proper response after store is closed" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] = closeStore(storeId, p, probe)
          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreClosed)

          val response3: StatusReply[StoreEvent] =
            deleteStore(storeId, p, probe)

          val successVal2: StoreEvent = response3.getValue
          assert(successVal2.asMessage.sealedValue.isStoreDeleted)

          val storeDeleted: StoreDeleted = successVal2.asMessage.sealedValue.storeDeleted.get

          storeDeleted.getStoreId shouldEqual storeId
          val storeDeletedMeta: StoreMetaInfo = storeDeleted.getMetaInfo

          storeDeletedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeDeletedMeta.getLastUpdatedBy shouldEqual MemberId("deletingUser")
        }
      }

      "executing OpenStore command" should {
        "error as already deleted" in new ReadiedSpec {
          closeStore(storeId, p, probe)
          deleteStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = openStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in deleted state"
        }
      }

      "executing CloseStore command" should {
        "error as already deleted" in new ReadiedSpec {
          closeStore(storeId, p, probe)
          deleteStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = closeStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in deleted state"
        }
      }

      "executing ReadyStore command" should {
        "error as already in Ready state" in new ReadiedSpec {
          closeStore(storeId, p, probe)
          deleteStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in deleted state"
        }
      }

      "executing EditStoreInfo command" should {
        "error on attempted edit" in new ReadiedSpec {
          closeStore(storeId, p, probe)
          deleteStore(storeId, p, probe)
          val response2: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo(), checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in deleted state"
        }
      }
    }

    "in the Terminated state" when {
      "executing TerminateStore command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          p ! Store.StoreRequestEnvelope(
            TerminateStore(
              Some(storeId),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )
          val response2: StatusReply[StoreEvent] = probe.receiveMessage()
          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to open Store"
        }

        "succeed and return the proper response after store is terminated from Draft state" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] =
            terminateStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.getStoreId shouldEqual storeId
          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.getMetaInfo

          storeTerminatedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.getLastUpdatedBy shouldEqual MemberId("terminatingUser")
        }

        "succeed and return the proper response after store is terminated from Ready state" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] =
            terminateStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.getStoreId shouldEqual storeId

          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.getMetaInfo

          storeTerminatedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.getLastUpdatedBy shouldEqual MemberId("terminatingUser")
        }

        "succeed and return the proper response after store is terminated from Open state" in new ReadiedSpec {
          openStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] =
            terminateStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.getStoreId shouldEqual storeId

          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.getMetaInfo

          storeTerminatedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.getLastUpdatedBy shouldEqual MemberId("terminatingUser")
        }

        "succeed and return the proper response after store is terminated from Closed state" in new ReadiedSpec {
          closeStore(storeId, p, probe)
          val response2: StatusReply[StoreEvent] = terminateStore(storeId, p, probe)
          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.getStoreId shouldEqual storeId

          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.getMetaInfo

          storeTerminatedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.getLastUpdatedBy shouldEqual MemberId("terminatingUser")
        }

        "succeed and return the proper response after store is terminated from Deleted state" in new ReadiedSpec {
          closeStore(storeId, p, probe)
          deleteStore(storeId, p, probe)
          val response2: StatusReply[StoreEvent] = terminateStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.getStoreId shouldEqual storeId
          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.getMetaInfo

          storeTerminatedMeta.getCreatedBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.getLastUpdatedBy shouldEqual MemberId("terminatingUser")
        }
      }

      "executing commands" should {
        "error on all commands" in new ReadiedSpec {
          terminateStore(storeId, p, probe)
          val commands = Seq(
            MakeStoreReady(Some(storeId), Some(MemberId("user"))),
            OpenStore(Some(storeId), Some(MemberId("user"))),
            CloseStore(Some(storeId), Some(MemberId("user"))),
            DeleteStore(Some(storeId), Some(MemberId("user"))),
            TerminateStore(Some(storeId), Some(MemberId("user"))),
            EditStoreInfo(
              Some(storeId),
              Some(MemberId("user")),
              Some(EditableStoreInfo())
            )
          )

          commands.foreach(command => {
            p ! Store.StoreRequestEnvelope(command, probe.ref)

            val response = probe.receiveMessage()

            assert(response.isError)

            val responseError = response.getError
            responseError.getMessage shouldEqual "Message type not supported in empty state"
          })
        }
      }

      "executing EditStoreInfo command" should {
        "error on attempted edit" in new CreatedNoInfoSpec {
          terminateStore(storeId, p, probe)
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = Some(baseStoreInfo.name)
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in empty state"
        }
      }
    }
  }
}
