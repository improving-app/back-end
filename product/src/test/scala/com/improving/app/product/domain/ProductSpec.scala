package com.improving.app.product.domain

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings
import com.improving.app.common.domain.{MemberId, Sku}
import com.improving.app.product.domain.Product._
import com.improving.app.product.domain.ProductState._
import com.improving.app.product.domain.TestData._
import com.improving.app.product.domain.util._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ProductSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with Matchers {
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[ProductEnvelope, ProductEvent, ProductState](
    system,
    Product("testEntityTypeHint", testSkuString),
    SerializationSettings.disabled
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The Product" when {
    "in the UninitializedDraftState" when {
      "executing CreateProduct" should {
        "error for an unauthorized creating user" ignore {
          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseCreateProduct.copy(
                onBehalfOf = Some(MemberId("unauthorizedUser"))
              ),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Product"
        }

        "succeed for golden path" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseCreateProduct,
              _
            )
          )

          val productCreated =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductCreated

          productCreated.sku shouldBe Some(Sku(testSkuString))
          productCreated.info shouldBe Some(baseProductInfo.toEditable)
          productCreated.meta.map(_.currentState) shouldBe Some(PRODUCT_STATE_DRAFT)
          productCreated.meta.flatMap(_.createdBy) shouldBe Some(MemberId("creatingMember"))

          val state = result.stateOfType[DraftProductState]

          state.editableInfo shouldEqual baseProductInfo.toEditable

        }

        "error for registering the same product" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseCreateProduct,
              _
            )
          )
          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseCreateProduct,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "CreateProduct command cannot be used on a draft Product"
        }
      }
    }
    "in the DraftProductState" when {
      "executing ActivateProduct" should {
        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseActivateProduct.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Product"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseActivateProduct,
              _
            )
          )

          val productActivated =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductActivated

          productActivated.sku shouldBe Some(Sku(testSkuString))
          productActivated.meta.map(_.currentState) shouldBe Some(PRODUCT_STATE_ACTIVE)
          productActivated.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("activatingMember")

          val state = result.stateOfType[ActiveProductState]

          state.info shouldBe baseProductInfo
        }
      }

      "executing InactivateProduct" should {
        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseInactivateProduct.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Product"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseInactivateProduct,
              _
            )
          )

          result.reply.getError.getMessage shouldEqual "InactivateProduct command cannot be used on a draft Product"
        }
      }

      "executing CreateProduct" should {
        "error for a draft event" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseCreateProduct,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "CreateProduct command cannot be used on a draft Product"
        }
      }

      "executing InactivateProduct" should {
        "error for a draft event" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseInactivateProduct,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "InactivateProduct command cannot be used on a draft Product"
        }
      }

      "executing EditProduct" should {
        "succeed for an empty edit" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseEditProductInfo.copy(info = Some(EditableProductInfo())),
              _
            )
          )

          val productEdited =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductInfoEdited

          productEdited.sku shouldEqual baseEditProductInfo.sku
          productEdited.info shouldEqual baseCreateProduct.info
          productEdited.getMeta.currentState shouldEqual PRODUCT_STATE_DRAFT
          productEdited.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          productEdited.getMeta.lastModifiedBy shouldEqual Some(MemberId("editingMember"))
        }

        "succeed for editing all fields" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseEditProductInfo,
              _
            )
          )

          val productEdited =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductInfoEdited

          productEdited.sku shouldEqual baseEditProductInfo.sku
          productEdited.info shouldEqual baseEditProductInfo.info
          productEdited.getMeta.currentState shouldEqual PRODUCT_STATE_DRAFT
          productEdited.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          productEdited.getMeta.lastModifiedBy shouldEqual Some(MemberId("editingMember"))
        }
      }

      "executing DeleteProduct" should {
        "succeed for a draft event" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseDeleteProduct,
              _
            )
          )

          val productDeleted =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductDeleted

          productDeleted.sku shouldEqual baseDeleteProduct.sku
          productDeleted.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          productDeleted.getMeta.lastModifiedBy shouldEqual Some(MemberId("deletingMember"))
          productDeleted.getMeta.currentState shouldEqual PRODUCT_STATE_DELETED
        }
      }
    }

    "in the ActivatedProductState" when {
      "executing ActivateProduct" should {
        "error due to already in ActivatedProductState" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseActivateProduct,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "ActivateProduct command cannot be used on an active Product"
        }
      }

      "executing InactivateProduct" should {
        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseInactivateProduct.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Product"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseInactivateProduct,
              _
            )
          )

          val productRescheduled =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductInactivated

          productRescheduled.sku shouldBe Some(Sku(testSkuString))
          productRescheduled.meta.map(_.currentState) shouldBe Some(PRODUCT_STATE_INACTIVE)
          productRescheduled.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("inactivatingMember")

          val state = result.stateOfType[InactiveProductState]

          state.info shouldEqual baseProductInfo
          state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("inactivatingMember")
        }
      }

      "executing DeleteProduct" should {
        "succeed for a scheduled event" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseDeleteProduct,
              _
            )
          )

          val productDeleted =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductDeleted

          productDeleted.sku shouldEqual baseDeleteProduct.sku
          productDeleted.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          productDeleted.getMeta.lastModifiedBy shouldEqual Some(MemberId("deletingMember"))
          productDeleted.getMeta.currentState shouldEqual PRODUCT_STATE_DELETED
        }
      }

      "executing EditProduct" should {
        "succeed for editing all fields" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseEditProductInfo,
              _
            )
          )

          val productEdited =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductInfoEdited

          productEdited.sku shouldEqual baseEditProductInfo.sku
          productEdited.info shouldEqual baseEditProductInfo.info
          productEdited.getMeta.currentState shouldEqual PRODUCT_STATE_ACTIVE
          productEdited.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          productEdited.getMeta.lastModifiedBy shouldEqual Some(MemberId("editingMember"))
        }
      }

      "executing invalid commands" should {
        "error on all commands" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))

          val commands = Seq(
            baseCreateProduct,
            baseActivateProduct
          )

          commands.map { command =>
            val response =
              eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(command, _)).reply
            println(command.getClass.getName.split('.').last)
            assert(response.isError)
            val responseError = response.getError
            responseError.getMessage shouldEqual s"${command.getClass.getName.split('.').last} command cannot be used on an active Product"
          }
        }

        "executing invalid commands" should {
          "error on all commands" in {
            eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
            eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
              ProductEnvelope(baseActivateProduct, _)
            )

            val commands = Seq(
              baseCreateProduct,
              baseActivateProduct,
            )

            commands.map { command =>
              val response =
                eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(command, _)).reply

              assert(response.isError)
              val responseError = response.getError
              responseError.getMessage shouldEqual s"${command.getClass.getName.split('.').last} command cannot be used on an active Product"
            }
          }
        }
      }
    }

    "in the InactivatedProductState" when {
      "executing InactivateProduct" should {
        "error due to already in InactivatedProductState" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseInactivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseInactivateProduct,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "InactivateProduct command cannot be used on an inactive Product"
        }
      }

      "executing ActivateProduct" should {
        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseInactivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseActivateProduct.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Product"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseInactivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseActivateProduct,
              _
            )
          )

          val productRescheduled =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductActivated

          productRescheduled.sku shouldBe Some(Sku(testSkuString))
          productRescheduled.meta.map(_.currentState) shouldBe Some(PRODUCT_STATE_ACTIVE)
          productRescheduled.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("activatingMember")

          val state = result.stateOfType[ActiveProductState]

          state.info shouldEqual baseProductInfo
          state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("activatingMember")
        }
      }

      "executing DeleteProduct" should {
        "succeed for a scheduled event" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseInactivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseDeleteProduct,
              _
            )
          )

          val productDeleted =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductDeleted

          productDeleted.sku shouldEqual baseDeleteProduct.sku
          productDeleted.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          productDeleted.getMeta.lastModifiedBy shouldEqual Some(MemberId("deletingMember"))
          productDeleted.getMeta.currentState shouldEqual PRODUCT_STATE_DELETED
        }
      }

      "executing EditProduct" should {
        "succeed for editing all fields" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseInactivateProduct, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
            ProductEnvelope(
              baseEditProductInfo,
              _
            )
          )

          val productEdited =
            result.reply.getValue.asMessage.getProductEventResponse.productEvent.asMessage.getProductInfoEdited

          productEdited.sku shouldEqual baseEditProductInfo.sku
          productEdited.info shouldEqual baseEditProductInfo.info
          productEdited.getMeta.currentState shouldEqual PRODUCT_STATE_INACTIVE
          productEdited.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          productEdited.getMeta.lastModifiedBy shouldEqual Some(MemberId("editingMember"))
        }
      }

      "executing invalid commands" should {
        "executing invalid commands" should {
          "error on all commands" in {
            eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
            eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseActivateProduct, _))
            eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](
              ProductEnvelope(baseInactivateProduct, _)
            )

            val commands = Seq(
              baseCreateProduct,
              baseInactivateProduct,
            )

            commands.map { command =>
              val response =
                eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(command, _)).reply

              assert(response.isError)
              val responseError = response.getError
              responseError.getMessage shouldEqual s"${command.getClass.getName.split('.').last} command cannot be used on an inactive Product"
            }
          }
        }
      }
    }

    "in the DeletedProductState" when {
      "executing commands other than Edit, Start, Cancel, or Reschedule" should {
        "error on all commands" in {
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseCreateProduct, _))
          eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(baseDeleteProduct, _))

          val commands = Seq(
            baseCreateProduct,
            baseActivateProduct,
            baseDeleteProduct,
            baseInactivateProduct
          )

          commands.map { command =>
            val response =
              eventSourcedTestKit.runCommand[StatusReply[ProductResponse]](ProductEnvelope(command, _)).reply
            println(command.getClass.getName.split('.').last)
            assert(response.isError)
            val responseError = response.getError
            responseError.getMessage shouldEqual "Product is deleted, no commands available"
          }
        }
      }

    }
  }
}
