package com.improving.app.store.domain

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import com.improving.app.common.domain.{MemberId, OrganizationId, StoreId}
import com.improving.app.store.domain.Store.StoreRequestEnvelope
import com.improving.app.store.domain.TestData.baseStoreInfo
import com.typesafe.config.{Config, ConfigFactory, Optional}
import org.scalatest.{stats, BeforeAndAfterAll}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

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

    val response: StatusReply[StoreEvent] = createStore(storeId, p, probe, storeInfo = None)
  }

  trait ReadiedSpec {
    val (storeId, p, probe) = createTestVariables()

    createStore(storeId, p, probe)

    val response: StatusReply[StoreEvent] = readyStore(storeId, p, probe)
  }

  trait NewInfoForEditSpec {
    val newName: Option[String] = Some("xxxx")
    val newDesc: Option[String] = Some("xxxx")
    val newSponsoringOrg: Option[OrganizationId] = Some(OrganizationId("otherOrg"))
  }

  def createStore(
      storeId: StoreId,
      p: ActorRef[StoreRequestEnvelope],
      probe: TestProbe[StatusReply[StoreEvent]],
      storeInfo: Option[EditableStoreInfo] = Some(
        EditableStoreInfo(
          Some(baseStoreInfo.getInfo.name),
          Some(baseStoreInfo.getInfo.description),
          Some(baseStoreInfo.getInfo.sponsoringOrg)
        )
      ),
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      CreateStore(
        storeId = storeId,
        onBehalfOf = MemberId("creatingUser"),
        info = storeInfo
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
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      MakeStoreReady(
        storeId = storeId,
        onBehalfOf = MemberId("readyingUser"),
        info = None
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
        storeId = storeId,
        onBehalfOf = MemberId("openingUser")
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
        storeId = storeId,
        onBehalfOf = MemberId("closingUser")
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
        storeId = storeId,
        onBehalfOf = MemberId("deletingUser")
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
        storeId = storeId,
        onBehalfOf = MemberId("terminatingUser")
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
      info: EditableStoreInfo = EditableStoreInfo(
        Some(baseStoreInfo.getInfo.name),
        Some(baseStoreInfo.getInfo.description),
        Some(baseStoreInfo.getInfo.sponsoringOrg)
      ),
      checkSuccess: Boolean = true
  ): StatusReply[StoreEvent] = {
    p ! Store.StoreRequestEnvelope(
      EditStoreInfo(
        storeId = storeId,
        onBehalfOf = MemberId("editingUser"),
        newInfo = info
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
              storeId,
              MemberId("unauthorizedUser"),
              EditableStoreInfo(),
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
          successVal.asMessage.sealedValue.storeCreated.get.storeId shouldEqual storeId
          successVal.asMessage.sealedValue.storeCreated.get.metaInfo.createdBy shouldEqual MemberId("creatingUser")
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
            MakeStoreReady(storeId, MemberId("user")),
            OpenStore(storeId, MemberId("user")),
            CloseStore(storeId, MemberId("user")),
            DeleteStore(storeId, MemberId("user")),
            TerminateStore(storeId, MemberId("user")),
            EditStoreInfo(
              storeId,
              MemberId("user"),
              EditableStoreInfo()
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
      "executing EditStoreInfo command" should {
        "error for an unauthorized updating user" ignore new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            EditStoreInfo(
              storeId,
              MemberId("unauthorizedUser"),
              EditableStoreInfo(),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()

          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Store"
        }

        "succeed for an empty edit and return the proper response" in new CreatedSpec {
          val state: EditableStoreInfo = EditableStoreInfo(
            Some(baseStoreInfo.getInfo.name),
            Some(baseStoreInfo.getInfo.description),
            Some(baseStoreInfo.getInfo.sponsoringOrg)
          )

          val response2: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo())

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.storeId shouldEqual storeId
          successVal.metaInfo.lastUpdatedBy shouldEqual MemberId("editingUser")
          successVal.info.getEditableInfo shouldEqual state
        }

        "succeed for an edit of all fields and return the proper response" in new CreatedSpec with NewInfoForEditSpec {
          val state: EditableStoreInfo = EditableStoreInfo(
            newName,
            newDesc,
            newSponsoringOrg,
          )
          val response2: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo(newName, newDesc, newSponsoringOrg))
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.storeId shouldEqual storeId
          successVal.info.getEditableInfo shouldEqual state
          successVal.metaInfo.lastUpdatedBy shouldEqual MemberId("editingUser")
        }

        "succeed for a partial edit and return the proper response" in new CreatedSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo(name = newName))

          val successVal2: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal2.storeId shouldEqual storeId
          successVal2.metaInfo.lastUpdatedBy shouldEqual MemberId("editingUser")
          successVal2.info.infoOrEditable.editableInfo.flatMap(_.name) shouldEqual newName

          val response3: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo(description = newDesc))

          val successVal3: StoreInfoEdited = response3.getValue.asInstanceOf[StoreInfoEdited]

          successVal3.storeId shouldEqual storeId
          successVal3.metaInfo.lastUpdatedBy shouldEqual MemberId("editingUser")
          successVal3.info.infoOrEditable.editableInfo.flatMap(_.description) shouldEqual newDesc

          val response4: StatusReply[StoreEvent] =
            editStoreInfo(storeId, p, probe, EditableStoreInfo(sponsoringOrg = newSponsoringOrg))

          val successVal4: StoreInfoEdited = response4.getValue.asInstanceOf[StoreInfoEdited]

          successVal4.storeId shouldEqual storeId
          successVal4.metaInfo.lastUpdatedBy shouldEqual MemberId("editingUser")
          successVal4.info.infoOrEditable.editableInfo.get.sponsoringOrg shouldEqual newSponsoringOrg
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
              storeId = storeId,
              onBehalfOf = MemberId("unauthorized"),
            ),
            probe.ref
          )

          val response2: StatusReply[StoreEvent] = probe.receiveMessage()
          assert(response2.isError)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to make Store ready"
        }

        "error on incomplete info" in new CreatedNoInfoSpec with NewInfoForEditSpec {
          val response2: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "No associated name"

          editStoreInfo(storeId, p, probe, EditableStoreInfo(name = Some(baseStoreInfo.getInfo.name)))

          val response3: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError2: Throwable = response3.getError
          responseError2.getMessage shouldEqual "No associated description"

          editStoreInfo(
            storeId,
            p,
            probe,
            EditableStoreInfo(name = None, description = Some(baseStoreInfo.getInfo.description))
          )

          val response4: StatusReply[StoreEvent] = readyStore(storeId, p, probe, checkSuccess = false)

          val responseError3: Throwable = response4.getError
          responseError3.getMessage shouldEqual "No associated sponsoring org"
        }

        "succeed and return the proper response" in new ReadiedSpec {
          val successVal: StoreEvent = response.getValue
          assert(successVal.asMessage.sealedValue.isStoreIsReady)

          val storeOpened: StoreIsReady = successVal.asMessage.sealedValue.storeIsReady.get

          storeOpened.storeId shouldEqual storeId

          val storeOpenedMeta: StoreMetaInfo = storeOpened.metaInfo

          storeOpenedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeOpenedMeta.lastUpdatedBy shouldEqual MemberId("readyingUser")
        }
      }

      "executing EditStoreInfo command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          // Test command in question
          p ! Store.StoreRequestEnvelope(
            EditStoreInfo(
              storeId = storeId,
              onBehalfOf = MemberId("unauthorized"),
              newInfo = EditableStoreInfo(
                Some(baseStoreInfo.getInfo.name),
                Some(baseStoreInfo.getInfo.description),
                Some(baseStoreInfo.getInfo.sponsoringOrg)
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

          successVal.info.getInfo shouldEqual baseStoreInfo.getInfo
        }

        "succeed for an edit of all fields and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updateInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName,
            description = newDesc,
            sponsoringOrg = newSponsoringOrg
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updateInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.info.getInfo shouldEqual StoreInfo(
            updateInfo.getName,
            updateInfo.getDescription,
            updateInfo.getSponsoringOrg
          )
        }

        "succeed for a partial edit and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.info.getInfo shouldEqual baseStoreInfo.getInfo.copy(name = updatedInfo.getName)
        }
      }
    }

    "in the Ready state" when {
      "executing MakeStoreReady command" should {
        "error for an unauthorized updating user" ignore new CreatedSpec {
          p ! Store.StoreRequestEnvelope(
            MakeStoreReady(
              storeId,
              MemberId("unauthorizedUser"),
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

          storeReady.storeId shouldEqual storeId
          val storeReadyMeta: StoreMetaInfo = storeReady.metaInfo

          storeReadyMeta.createdBy shouldEqual MemberId("creatingUser")
          storeReadyMeta.lastUpdatedBy shouldEqual MemberId("readyingUser")
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
              storeId,
              MemberId("unauthorizedUser"),
              EditableStoreInfo(
                Some(baseStoreInfo.getInfo.name),
                Some(baseStoreInfo.getInfo.description),
                Some(baseStoreInfo.getInfo.sponsoringOrg)
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

          successVal.info shouldEqual baseStoreInfo
        }

        "succeed for an edit of all fields and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updateInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName,
            description = newDesc,
            sponsoringOrg = newSponsoringOrg
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updateInfo)
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.info.getInfo shouldEqual StoreInfo(
            updateInfo.getName,
            updateInfo.getDescription,
            updateInfo.getSponsoringOrg
          )
        }

        "succeed for a partial edit and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.info.getInfo shouldEqual baseStoreInfo.getInfo.copy(name = updatedInfo.name.get)
        }
      }
    }

    "in the Open state" when {
      "executing OpenStore command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          p ! Store.StoreRequestEnvelope(
            OpenStore(
              storeId,
              MemberId("unauthorizedUser"),
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

          storeOpened.storeId shouldEqual storeId

          val storeOpenedMeta: StoreMetaInfo = storeOpened.metaInfo

          storeOpenedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeOpenedMeta.lastUpdatedBy shouldEqual MemberId("openingUser")
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
              storeId,
              MemberId("unauthorizedUser"),
              EditableStoreInfo()
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

          successVal.info.getInfo shouldEqual baseStoreInfo.getInfo
        }

        "succeed for an edit of all fields and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updateInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName,
            description = newDesc,
            sponsoringOrg = newSponsoringOrg
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updateInfo)
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.info shouldEqual StoreOrEditableInfo(
            StoreOrEditableInfo.InfoOrEditable.Info(
              StoreInfo(
                updateInfo.getName,
                updateInfo.getDescription,
                updateInfo.getSponsoringOrg
              )
            )
          )
        }

        "succeed for a partial edit and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.info.getInfo shouldEqual baseStoreInfo.getInfo.copy(name = updatedInfo.getName)
        }
      }
    }

    "in the Closed state" when {
      "executing CloseStore command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          p ! Store.StoreRequestEnvelope(
            CloseStore(
              storeId,
              MemberId("unauthorizedUser")
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

          storeClosed.storeId shouldEqual storeId

          val storeClosedMeta: StoreMetaInfo = storeClosed.metaInfo

          storeClosedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeClosedMeta.lastUpdatedBy shouldEqual MemberId("closingUser")
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

          storeClosed.storeId shouldEqual storeId

          val storeClosedMeta: StoreMetaInfo = storeClosed.metaInfo

          storeClosedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeClosedMeta.lastUpdatedBy shouldEqual MemberId("closingUser")
        }
      }

      "executing OpenStore command" should {
        "succeed in opening store when never previously opened" in new ReadiedSpec {
          closeStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = openStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreOpened)

          val storeOpened: StoreOpened = successVal.asMessage.sealedValue.storeOpened.get

          storeOpened.storeId shouldEqual storeId

          val storeOpenedMeta: StoreMetaInfo = storeOpened.metaInfo

          storeOpenedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeOpenedMeta.lastUpdatedBy shouldEqual MemberId("openingUser")
        }

        "succeed in opening store when initially opened" in new ReadiedSpec {
          openStore(storeId, p, probe)
          closeStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] = openStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreOpened)

          val storeOpened: StoreOpened = successVal.asMessage.sealedValue.storeOpened.get

          storeOpened.storeId shouldEqual storeId
          val storeOpenedMeta: StoreMetaInfo = storeOpened.metaInfo

          storeOpenedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeOpenedMeta.lastUpdatedBy shouldEqual MemberId("openingUser")
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
              storeId,
              MemberId("unauthorizedUser"),
              EditableStoreInfo()
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

          successVal.info.getInfo shouldEqual baseStoreInfo.getInfo
        }

        "succeed for an edit of all fields and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updateInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName,
            description = newDesc,
            sponsoringOrg = newSponsoringOrg
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updateInfo)
          assert(response2.isSuccess)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.info.getInfo shouldEqual StoreInfo(
            updateInfo.getName,
            updateInfo.getDescription,
            updateInfo.getSponsoringOrg
          )
        }

        "succeed for a partial edit and return the proper response" in new ReadiedSpec with NewInfoForEditSpec {
          val updatedInfo: EditableStoreInfo = EditableStoreInfo(
            name = newName
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo)

          val successVal: StoreInfoEdited = response2.getValue.asInstanceOf[StoreInfoEdited]

          successVal.info.getInfo shouldEqual baseStoreInfo.getInfo.copy(name = updatedInfo.getName)
        }
      }
    }

    "in the Deleted state" when {
      "executing DeleteStore command" should {
        "error for an unauthorized updating user" ignore new ReadiedSpec {
          closeStore(storeId, p, probe)
          p ! Store.StoreRequestEnvelope(
            DeleteStore(
              storeId,
              MemberId("unauthorizedUser")
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

          storeDeleted.storeId shouldEqual storeId
          val storeDeletedMeta: StoreMetaInfo = storeDeleted.metaInfo

          storeDeletedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeDeletedMeta.lastUpdatedBy shouldEqual MemberId("deletingUser")
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
              storeId,
              MemberId("unauthorizedUser")
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

          storeTerminated.storeId shouldEqual storeId
          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.metaInfo

          storeTerminatedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.lastUpdatedBy shouldEqual MemberId("terminatingUser")
        }

        "succeed and return the proper response after store is terminated from Ready state" in new ReadiedSpec {
          val response2: StatusReply[StoreEvent] =
            terminateStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.storeId shouldEqual storeId

          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.metaInfo

          storeTerminatedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.lastUpdatedBy shouldEqual MemberId("terminatingUser")
        }

        "succeed and return the proper response after store is terminated from Open state" in new ReadiedSpec {
          openStore(storeId, p, probe)

          val response2: StatusReply[StoreEvent] =
            terminateStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.storeId shouldEqual storeId

          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.metaInfo

          storeTerminatedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.lastUpdatedBy shouldEqual MemberId("terminatingUser")
        }

        "succeed and return the proper response after store is terminated from Closed state" in new ReadiedSpec {
          closeStore(storeId, p, probe)
          val response2: StatusReply[StoreEvent] = terminateStore(storeId, p, probe)
          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.storeId shouldEqual storeId

          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.metaInfo

          storeTerminatedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.lastUpdatedBy shouldEqual MemberId("terminatingUser")
        }

        "succeed and return the proper response after store is terminated from Deleted state" in new ReadiedSpec {
          closeStore(storeId, p, probe)
          deleteStore(storeId, p, probe)
          val response2: StatusReply[StoreEvent] = terminateStore(storeId, p, probe)

          val successVal: StoreEvent = response2.getValue
          assert(successVal.asMessage.sealedValue.isStoreTerminated)

          val storeTerminated: StoreTerminated = successVal.asMessage.sealedValue.storeTerminated.get

          storeTerminated.storeId shouldEqual storeId
          val storeTerminatedMeta: StoreMetaInfo = storeTerminated.metaInfo

          storeTerminatedMeta.createdBy shouldEqual MemberId("creatingUser")
          storeTerminatedMeta.lastUpdatedBy shouldEqual MemberId("terminatingUser")
          storeTerminatedMeta.lastUpdatedBy shouldEqual MemberId("terminatingUser")
        }
      }

      "executing commands" should {
        "error on all commands" in new ReadiedSpec {
          terminateStore(storeId, p, probe)
          val commands = Seq(
            MakeStoreReady(storeId, MemberId("user")),
            OpenStore(storeId, MemberId("user")),
            CloseStore(storeId, MemberId("user")),
            DeleteStore(storeId, MemberId("user")),
            TerminateStore(storeId, MemberId("user")),
            EditStoreInfo(
              storeId,
              MemberId("user"),
              EditableStoreInfo()
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
            name = Some(baseStoreInfo.getInfo.name)
          )

          val response2: StatusReply[StoreEvent] = editStoreInfo(storeId, p, probe, updatedInfo, checkSuccess = false)

          val responseError: Throwable = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in empty state"
        }
      }
    }
  }
}
