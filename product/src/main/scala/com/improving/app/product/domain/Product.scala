package com.improving.app.product.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.errors.{Error, StateError}
import com.improving.app.product.domain.ProductState._
import com.improving.app.product.domain.Validation.{
  draftTransitionProductInfoValidator,
  productCommandValidator,
  productQueryValidator
}
import com.improving.app.product.domain.util.{EditableProductInfoUtil, ProductInfoUtil}
import com.typesafe.scalalogging.StrictLogging

import java.time.{Clock, Instant}

object Product extends StrictLogging {

  private val clock: Clock = Clock.systemDefaultZone()

  val ProductEntityKey: EntityTypeKey[ProductEnvelope] = EntityTypeKey[ProductEnvelope]("Product")

  // Command wraps the request type
  final case class ProductEnvelope(request: ProductRequestPB, replyTo: ActorRef[StatusReply[ProductResponse]])

  private def emptyState(): ProductState = {
    UninitializedProductState
  }

  sealed private[domain] trait ProductState
  private[domain] object UninitializedProductState extends ProductState
  sealed private[domain] trait CreatedProductState extends ProductState {
    val meta: ProductMetaInfo
  }

  sealed private[domain] trait DefinedProductState extends ProductState {
    val info: ProductInfo
    val meta: ProductMetaInfo
  }

  private[domain] case class DraftProductState(editableInfo: EditableProductInfo, meta: ProductMetaInfo)
      extends CreatedProductState
  private[domain] case class ActiveProductState(info: ProductInfo, meta: ProductMetaInfo)
      extends CreatedProductState
      with DefinedProductState

  private[domain] case class InactiveProductState(info: ProductInfo, meta: ProductMetaInfo)
      extends CreatedProductState
      with DefinedProductState
  private[domain] case class DeletedProductState(info: ProductInfo, lastMeta: ProductMetaInfo) extends ProductState

  def apply(entityTypeHint: String, sku: String): Behavior[ProductEnvelope] =
    Behaviors.setup { context =>
      context.log.info("Starting Product {}", sku)
      EventSourcedBehavior
        .withEnforcedReplies[ProductEnvelope, ProductResponse, ProductState](
          persistenceId = PersistenceId(entityTypeHint, sku),
          emptyState = emptyState(),
          commandHandler = commandHandler,
          eventHandler = eventHandler
        )
        // .withRetention(RetentionCriteria.snapshotEvery(numberOfProducts = 100, keepNSnapshots = 2))
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            context.log.debug("onRecoveryCompleted: [{}]", state)
          case (_, PostStop) =>
            context.log.info("Product {} stopped", sku)
        }
    }

  // CommandHandler
  private val commandHandler: (ProductState, ProductEnvelope) => ReplyEffect[ProductResponse, ProductState] = {
    (state, command) =>
      def replyWithResponseProduct(response: ProductResponse): ReplyEffect[ProductResponse, ProductState] =
        response match {
          case productResponse: ProductEventResponse =>
            Effect
              .persist(productResponse)
              .thenReply(command.replyTo) { _: ProductState => StatusReply.Success(response) }
          case queryResponse: ProductData =>
            Effect
              .persist(queryResponse)
              .thenReply(command.replyTo) { _: ProductState => StatusReply.Success(response) }
          case _ =>
            Effect.reply(command.replyTo)(
              StatusReply.Error(s"${response.productPrefix} is not a supported product response")
            )
        }

      command.request match {
        case c: ProductCommand =>
          productCommandValidator(c) match {
            case None =>
              val result: Either[Error, ProductResponse] = state match {
                case UninitializedProductState =>
                  command.request match {
                    case createProductCommand: CreateProduct => createProduct(createProductCommand)
                    case _ =>
                      Left(
                        StateError(
                          s"${command.request.productPrefix} command cannot be used on an uninitialized Product"
                        )
                      )
                  }
                case DraftProductState(editableInfo, meta) =>
                  command.request match {
                    case activateProductCommand: ActivateProduct =>
                      activateProduct(Left(editableInfo), meta, activateProductCommand)
                    case editProductInfoCommand: EditProductInfo =>
                      editProductInfo(Left(editableInfo), meta, editProductInfoCommand)
                    case deleteProductCommand: DeleteProduct =>
                      deleteProduct(Left(editableInfo), meta, deleteProductCommand)
                    case _ =>
                      Left(StateError(s"${command.request.productPrefix} command cannot be used on a draft Product"))
                  }
                case x: DefinedProductState =>
                  x match {
                    case ActiveProductState(info, meta) =>
                      command.request match {
                        case inactivateProductCommand: InactivateProduct =>
                          inactivateProduct(Right(info), meta, inactivateProductCommand)
                        case deleteProductCommand: DeleteProduct =>
                          deleteProduct(Right(info), meta, deleteProductCommand)
                        case editProductInfoCommand: EditProductInfo =>
                          editProductInfo(Right(info), meta, editProductInfoCommand)
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} command cannot be used on an active Product"
                            )
                          )
                      }
                    case InactiveProductState(info, meta) =>
                      command.request match {
                        case activateProductCommand: ActivateProduct =>
                          activateProduct(Right(info), meta, activateProductCommand)
                        case deleteProductCommand: DeleteProduct =>
                          deleteProduct(Right(info), meta, deleteProductCommand)
                        case editProductInfoCommand: EditProductInfo =>
                          editProductInfo(Right(info), meta, editProductInfoCommand)
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} command cannot be used on an inactive Product"
                            )
                          )
                      }
                  }
                case _: DeletedProductState =>
                  command.request match {
                    case _ =>
                      Left(
                        StateError(s"Product is deleted, no commands available")
                      )
                  }
              }

              result match {
                case Left(error)    => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
                case Right(product) => replyWithResponseProduct(product)
              }
            case Some(errors) => Effect.reply(command.replyTo)(StatusReply.Error(errors.message))
          }
        case q: ProductQuery =>
          productQueryValidator(q) match {
            case None =>
              q match {
                case GetProductInfo(sku, _) =>
                  state match {
                    case definedState: DefinedProductState =>
                      replyWithResponseProduct(ProductData(sku, Some(definedState.info)))
                    case deletedState: DeletedProductState =>
                      replyWithResponseProduct(ProductData(sku, Some(deletedState.info)))
                    case state =>
                      Effect.reply(command.replyTo)(
                        StatusReply.Error(s"Cannot retrieve info from a Product in ${state.getClass.toString}")
                      )
                  }
              }
            case Some(errors) => Effect.reply(command.replyTo)(StatusReply.Error(errors.message))
          }
        case ProductRequestPB.Empty =>
          Effect.reply(command.replyTo)(StatusReply.Error("Message was not an ProductRequest"))
      }
  }

  // ProductHandler
  private val eventHandler: (ProductState, ProductResponse) => ProductState = { (state, response) =>
    response match {
      case productResponse: ProductEventResponse =>
        productResponse.productEvent match {
          case productCreatedEvent: ProductCreated =>
            state match {
              case UninitializedProductState =>
                DraftProductState(
                  editableInfo = productCreatedEvent.getInfo,
                  meta = productCreatedEvent.getMeta
                )
              case _ => state
            }

          case productActivatedEvent: ProductActivated =>
            state match {
              case DraftProductState(_, _) =>
                ActiveProductState(
                  info = productActivatedEvent.getInfo,
                  meta = productActivatedEvent.getMeta
                )
              case InactiveProductState(_, _) =>
                ActiveProductState(
                  info = productActivatedEvent.getInfo,
                  meta = productActivatedEvent.getMeta
                )
              case _ => state
            }

          case productInactivatedEvent: ProductInactivated =>
            state match {
              case DraftProductState(editableInfo, _) =>
                InactiveProductState(
                  info = editableInfo.toInfo,
                  meta = productInactivatedEvent.getMeta
                )
              case ActiveProductState(info, _) =>
                InactiveProductState(
                  info = info,
                  meta = productInactivatedEvent.getMeta
                )
              case _ => state
            }

          case productDeleteProduct: ProductDeleted =>
            state match {
              case DraftProductState(editableInfo, _) =>
                DeletedProductState(
                  info = editableInfo.toInfo,
                  lastMeta = productDeleteProduct.getMeta
                )
              case ActiveProductState(info, _) =>
                DeletedProductState(
                  info = info,
                  lastMeta = productDeleteProduct.getMeta
                )
              case InactiveProductState(info: ProductInfo, _) =>
                DeletedProductState(info = info, lastMeta = productDeleteProduct.getMeta)
              case _ => state
            }

          case productInfoEdited: ProductInfoEdited =>
            state match {
              case _: DraftProductState =>
                DraftProductState(
                  editableInfo = productInfoEdited.getInfo,
                  meta = productInfoEdited.getMeta
                )
              case _: ActiveProductState =>
                ActiveProductState(
                  info = productInfoEdited.getInfo.toInfo,
                  meta = productInfoEdited.getMeta
                )
              case _: InactiveProductState =>
                InactiveProductState(
                  info = productInfoEdited.getInfo.toInfo,
                  meta = productInfoEdited.getMeta
                )
              case _ => state
            }
          case _ => state
        }
      case _: ProductData        => state
      case ProductResponse.Empty => state

      case other =>
        throw new RuntimeException(s"Invalid/Unhandled product $other")
    }
  }

  private def createProduct(
      createProductCommand: CreateProduct
  ): Either[Error, ProductResponse] = {
    logger.info(s"creating for id ${createProductCommand.sku}")
    val now = Timestamp(Instant.now(clock))
    val newMeta = ProductMetaInfo(
      lastModifiedOn = Some(now),
      lastModifiedBy = createProductCommand.onBehalfOf,
      createdOn = Some(now),
      createdBy = createProductCommand.onBehalfOf,
      currentState = PRODUCT_STATE_DRAFT
    )
    val product = ProductCreated(
      createProductCommand.sku,
      createProductCommand.info,
      Some(newMeta)
    )
    Right(ProductEventResponse(product))
  }

  private def activateProduct(
      info: Either[EditableProductInfo, ProductInfo],
      meta: ProductMetaInfo,
      activateProductCommand: ActivateProduct
  ): Either[Error, ProductResponse] = {
    val now = Timestamp(Instant.now(clock))
    val newMeta = meta.copy(
      lastModifiedOn = Some(now),
      lastModifiedBy = activateProductCommand.onBehalfOf,
      currentState = PRODUCT_STATE_ACTIVE
    )

    info match {
      case Left(e: EditableProductInfo) =>
        val updatedInfo =
          if (activateProductCommand.info.isDefined)
            e.updateInfo(activateProductCommand.getInfo)
          else e
        val validationErrorsOpt = draftTransitionProductInfoValidator(updatedInfo)
        if (validationErrorsOpt.isEmpty) {
          Right(
            ProductEventResponse(
              ProductActivated(
                activateProductCommand.sku,
                Some(updatedInfo.toInfo),
                Some(newMeta)
              )
            )
          )
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(info: ProductInfo) =>
        Right(
          ProductEventResponse(
            ProductActivated(
              activateProductCommand.sku,
              Some(info),
              Some(newMeta)
            )
          )
        )
    }
  }

  private def inactivateProduct(
      info: Either[EditableProductInfo, ProductInfo],
      meta: ProductMetaInfo,
      inactivateProductCommand: InactivateProduct
  ): Either[Error, ProductResponse] = {
    val now = Some(Timestamp(Instant.now(clock)))
    val newMeta = meta.copy(
      lastModifiedOn = now,
      lastModifiedBy = inactivateProductCommand.onBehalfOf,
      currentState = PRODUCT_STATE_INACTIVE,
    )

    info match {
      case Left(e: EditableProductInfo) =>
        val validationErrorsOpt = draftTransitionProductInfoValidator(e)
        if (validationErrorsOpt.isEmpty) {
          Right(
            ProductEventResponse(
              ProductInactivated(
                inactivateProductCommand.sku,
                Some(e.toInfo),
                Some(newMeta)
              )
            )
          )
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(info: ProductInfo) =>
        Right(
          ProductEventResponse(
            ProductInactivated(
              inactivateProductCommand.sku,
              Some(info),
              Some(newMeta)
            )
          )
        )
    }
  }

  private def deleteProduct(
      info: Either[EditableProductInfo, ProductInfo],
      meta: ProductMetaInfo,
      deleteProductCommand: DeleteProduct
  ): Either[Error, ProductResponse] = {
    val now = Some(Timestamp(Instant.now(clock)))
    val newMeta = meta.copy(
      lastModifiedOn = now,
      lastModifiedBy = deleteProductCommand.onBehalfOf,
      currentState = PRODUCT_STATE_DELETED
    )
    info match {
      case Left(e: EditableProductInfo) =>
        val validationErrorsOpt = draftTransitionProductInfoValidator(e)
        if (validationErrorsOpt.isEmpty) {
          Right(
            ProductEventResponse(
              ProductDeleted(
                deleteProductCommand.sku,
                Some(newMeta)
              )
            )
          )
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(_: ProductInfo) =>
        Right(
          ProductEventResponse(
            ProductDeleted(
              deleteProductCommand.sku,
              Some(newMeta)
            )
          )
        )
    }
  }

  private def editProductInfo(
      info: Either[EditableProductInfo, ProductInfo],
      meta: ProductMetaInfo,
      editProductInfoCommand: EditProductInfo
  ): Either[Error, ProductResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = editProductInfoCommand.onBehalfOf,
      lastModifiedOn = Some(Timestamp(Instant.now(clock)))
    )

    editProductInfoCommand.info
      .map { editable =>
        val product = ProductInfoEdited(
          editProductInfoCommand.sku,
          Some(info match {
            case Right(i: ProductInfo)        => i.updateInfo(editable).toEditable
            case Left(e: EditableProductInfo) => e.updateInfo(editable)
          }),
          Some(newMeta)
        )
        Right(ProductEventResponse(product))
      }
      .getOrElse(
        Right(
          ProductEventResponse(
            ProductInfoEdited(
              editProductInfoCommand.sku,
              None,
              Some(newMeta)
            )
          )
        )
      )
  }
}
