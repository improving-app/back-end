package com.improving.app.store.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.{Counter, Tracer}
import com.improving.app.common.domain.MemberId
import com.improving.app.common.errors._
import com.improving.app.store.domain.Validation.{draftTransitionStoreInfoValidator, storeCommandValidator}
import com.improving.app.store.domain.util.{EditableStoreInfoUtil, StoreInfoUtil}

import java.time.Instant

object Store {
  val TypeKey: EntityTypeKey[StoreRequestEnvelope] = EntityTypeKey[StoreRequestEnvelope]("Store")

  // Counter metric for tenants
  private val totalStores: Counter =
    Counter("total-stores", "", "The total number of stores, active or suspended.", "each")

  // Counter metric for active tenants
  private val openStores: Counter =
    Counter("open-stores", "", "The total number of stores currently active.", "each")

  // Tracer for tracing call chains
  private val tracer = Tracer("Store")

  case class StoreRequestEnvelope(request: StoreRequestPB, replyTo: ActorRef[StatusReply[StoreEvent]])

  sealed private trait StoreState

  sealed private trait EmptyState extends StoreState

  private case object UninitializedState extends EmptyState

  sealed private trait InitializedState extends StoreState {
    def metaInfo: StoreMetaInfo
  }

  sealed private trait CreatedState extends InitializedState {
    val info: StoreInfo
    val metaInfo: StoreMetaInfo
  }

  sealed private trait InactiveState extends InitializedState {
    val metaInfo: StoreMetaInfo
  }
  sealed private trait DeletableState extends InitializedState {
    val metaInfo: StoreMetaInfo
  }

  private case class DraftState(info: EditableStoreInfo, metaInfo: StoreMetaInfo)
      extends InactiveState
      with DeletableState
  private case class ReadyState(info: StoreInfo, metaInfo: StoreMetaInfo) extends CreatedState
  private case class OpenState(info: StoreInfo, metaInfo: StoreMetaInfo) extends CreatedState
  private case class ClosedState(info: StoreInfo, metaInfo: StoreMetaInfo) extends CreatedState with DeletableState
  private case class DeletedState(info: StoreInfo, metaInfo: StoreMetaInfo) extends InactiveState
  private case class TerminatedState(lastMeta: StoreMetaInfo) extends EmptyState
  def apply(persistenceId: PersistenceId): Behavior[StoreRequestEnvelope] = {
    Behaviors.setup(_ =>
      EventSourcedBehavior[StoreRequestEnvelope, StoreEvent, StoreState](
        persistenceId = persistenceId,
        emptyState = UninitializedState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
    )
  }

  private val commandHandler: (StoreState, StoreRequestEnvelope) => ReplyEffect[StoreEvent, StoreState] = {
    (state, envelope) =>
      val span = tracer.startSpan("commandHandler")
      try {
        val errors: Either[ReplyEffect[StoreEvent, StoreState], StoreRequestPB with StoreRequest] =
          envelope.request match {
            case r: StoreRequest => Right(r)
            case _ => Left(Effect.reply(envelope.replyTo)(StatusReply.Error("Message was not a StoreRequest")))
          }

        errors.map(storeCommandValidator) match {
          case Right(None) =>
            val result: Either[Error, StoreEvent] = state match {
              case _: EmptyState =>
                envelope.request match {
                  case command: CreateStore => createStore(command)
                  case _                    => Left(StateError("Message type not supported in empty state"))
                }
              case draftState: DraftState =>
                envelope.request match {
                  case command: MakeStoreReady          => makeStoreReady(draftState, command)
                  case command: DeleteStore             => deleteStore(draftState, command)
                  case command: TerminateStore          => terminateStore(draftState, command)
                  case command: EditStoreInfo           => editStoreInfo(draftState, command)
                  case command: AddProductsToStore      => addProductsToStore(draftState, command)
                  case command: RemoveProductsFromStore => removeProductsFromStore(draftState, command)
                  case _                                => Left(StateError("Message type not supported in draft state"))
                }
              case readyState: ReadyState =>
                envelope.request match {
                  case command: OpenStore               => openStore(readyState, command)
                  case command: CloseStore              => closeStore(readyState, command)
                  case command: TerminateStore          => terminateStore(readyState, command)
                  case command: EditStoreInfo           => editStoreInfo(readyState, command)
                  case command: AddProductsToStore      => addProductsToStore(readyState, command)
                  case command: RemoveProductsFromStore => removeProductsFromStore(readyState, command)
                  case _: DeleteStore                   => Left(StateError("Store must be closed before deleting"))
                  case _                                => Left(StateError("Message type not supported in ready state"))
                }
              case openState: OpenState =>
                envelope.request match {
                  case command: CloseStore              => closeStore(openState, command)
                  case command: TerminateStore          => terminateStore(openState, command)
                  case command: EditStoreInfo           => editStoreInfo(openState, command)
                  case command: AddProductsToStore      => addProductsToStore(openState, command)
                  case command: RemoveProductsFromStore => removeProductsFromStore(openState, command)
                  case _: DeleteStore                   => Left(StateError("Store must be closed before deleting"))
                  case _                                => Left(StateError("Message type not supported in open state"))
                }
              case closedState: ClosedState =>
                envelope.request match {
                  case command: OpenStore               => openStore(closedState, command)
                  case command: DeleteStore             => deleteStore(closedState, command)
                  case command: TerminateStore          => terminateStore(closedState, command)
                  case command: EditStoreInfo           => editStoreInfo(closedState, command)
                  case command: AddProductsToStore      => addProductsToStore(closedState, command)
                  case command: RemoveProductsFromStore => removeProductsFromStore(closedState, command)
                  case _ => Left(StateError("Message type not supported in closed state"))
                }
              case deletedState: DeletedState =>
                envelope.request match {
                  case command: TerminateStore => terminateStore(deletedState, command)
                  case _                       => Left(StateError("Message type not supported in deleted state"))
                }
            }
            result match {
              case Left(error)  => Effect.reply(envelope.replyTo)(StatusReply.Error(error.message))
              case Right(event) => Effect.persist(event).thenReply(envelope.replyTo) { _ => StatusReply.Success(event) }
            }
          case Right(Some(errors)) => Effect.reply(envelope.replyTo)(StatusReply.Error(errors.message))
          case Left(r)             => r
        }
      } finally span.end()
  }

  private val eventHandler: (StoreState, StoreEvent) => StoreState = { (state, event) =>
    val span = tracer.startSpan("commandHandler")
    try {
      event match {
        case StoreEvent.Empty => state
        case StoreCreated(_, Some(info), Some(metaInfo), _) =>
          state match {
            case s: EmptyState =>
              s match {
                case t: TerminatedState => t
                case _ =>
                  DraftState(info = info, metaInfo = metaInfo)
              }
            case x: StoreState => x
          }
        case StoreIsReady(_, Some(info), Some(metaInfo), _) =>
          state match {
            case _: DraftState => ReadyState(info, metaInfo)
            case x: StoreState => x
          }
        case StoreOpened(_, Some(info), Some(metaInfo), _) =>
          state match {
            case _: ReadyState  => OpenState(info, metaInfo)
            case _: ClosedState => OpenState(info, metaInfo)
            case x: StoreState  => x
          }
        case StoreClosed(_, Some(info), Some(metaInfo), _) =>
          state match {
            case _: ReadyState => ClosedState(info, metaInfo)
            case _: OpenState  => ClosedState(info, metaInfo)
            case x: StoreState => x
          }
        case StoreDeleted(_, Some(info), Some(metaInfo), _) =>
          state match {
            case _: DraftState  => DeletedState(info, metaInfo)
            case _: ClosedState => DeletedState(info, metaInfo)
            case x: StoreState  => x
          }
        case StoreTerminated(_, Some(metaInfo), _) =>
          state match {
            case _: InitializedState => TerminatedState(metaInfo)
            case x: StoreState       => x
          }
        case StoreInfoEdited(
              _,
              Some(newInfo),
              Some(metaInfo),
              _
            ) =>
          state match {
            case _: DraftState  => DraftState(newInfo, metaInfo)
            case _: ReadyState  => ReadyState(newInfo.toInfo, metaInfo)
            case _: OpenState   => OpenState(newInfo.toInfo, metaInfo)
            case _: ClosedState => ClosedState(newInfo.toInfo, metaInfo)
            case x: StoreState  => x
          }
        case _ => state
      }
    } finally span.end()
  }

  private def updateMetaInfo(metaInfo: Option[StoreMetaInfo], lastUpdatedByOpt: Option[MemberId]): StoreMetaInfo = {
    metaInfo.get.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(Timestamp(Instant.now())))
  }

  private def createStore(command: CreateStore): Either[Error, StoreEvent] = {
    val span = tracer.startSpan("createStore")
    try {
      command.info match {
        case Some(_) =>
          val newMetaInfo = StoreMetaInfo(
            createdOn = Some(Timestamp(Instant.now())),
            createdBy = command.onBehalfOf,
            lastUpdated = Some(Timestamp(Instant.now())),
            lastUpdatedBy = command.onBehalfOf
          )
          totalStores.add(1L)
          Right(
            StoreCreated(
              storeId = command.storeId,
              info = command.info,
              metaInfo = Some(newMetaInfo)
            )
          )
        case None => Left(ValidationError("EditableStoreInfo not provided in message"))
      }
    } finally span.end()
  }

  private def makeStoreReady(
      state: DraftState,
      command: MakeStoreReady
  ): Either[Error, StoreEvent] = {
    val span = tracer.startSpan("makeStoreReady")
    try {
      val validationErrorsOpt = draftTransitionStoreInfoValidator(state.info)
      if (validationErrorsOpt.isEmpty) {
        val info = state.info.updateInfo(command.getInfo)
        val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)
        Right(
          StoreIsReady(
            storeId = command.storeId,
            info = Some(
              StoreInfo(
                info.getName,
                info.getDescription,
                info.products,
                info.event,
                info.sponsoringOrg
              )
            ),
            metaInfo = Some(newMetaInfo)
          )
        )
      } else
        Left(StateError(validationErrorsOpt.get.message))

    } finally span.end()
  }

  private def openStore(
      state: CreatedState,
      command: OpenStore
  ): Either[Error, StoreEvent] = {
    val span = tracer.startSpan("openStore")
    try {
      val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)
      openStores.incr()
      Right(
        StoreOpened(
          storeId = command.storeId,
          info = Some(state.info),
          metaInfo = Some(newMetaInfo)
        )
      )
    } finally span.end()
  }

  private def closeStore(
      state: CreatedState,
      command: CloseStore
  ): Either[Error, StoreEvent] = {
    val span = tracer.startSpan("closeStore")
    try {
      val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)
      openStores.decr()
      Right(
        StoreClosed(
          storeId = command.storeId,
          info = Some(state.info),
          metaInfo = Some(newMetaInfo)
        )
      )
    } finally span.end()
  }

  private def deleteStore(
      state: DeletableState,
      command: DeleteStore
  ): Either[Error, StoreEvent] = {
    val span = tracer.startSpan("deleteStore")
    try {
      val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)

      state match {
        case DraftState(editable, _) =>
          Right(
            StoreDeleted(
              storeId = command.storeId,
              info = Some(
                StoreInfo(
                  editable.getName,
                  editable.getDescription,
                  editable.products,
                  editable.event,
                  editable.sponsoringOrg
                )
              ),
              metaInfo = Some(newMetaInfo)
            )
          )
        case ClosedState(info, _) =>
          Right(
            StoreDeleted(
              storeId = command.storeId,
              info = Some(info),
              metaInfo = Some(newMetaInfo)
            )
          )
      }
    } finally span.end()
  }

  private def terminateStore(state: InitializedState, terminate: TerminateStore): Either[Error, StoreEvent] = {
    val span = tracer.startSpan("terminateStore")
    try {
      val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = terminate.onBehalfOf)

      state match {
        case _ =>
          totalStores.decr()
          Right(
            StoreTerminated(
              storeId = terminate.storeId,
              metaInfo = Some(newMetaInfo)
            )
          )
      }
    } finally span.end()
  }

  private def editStoreInfo(
      state: InitializedState,
      command: EditStoreInfo
  ): Either[Error, StoreInfoEdited] = {
    val span = tracer.startSpan("editStoreInfo")
    try {
      val updatedMetaInfo = updateMetaInfo(Some(state.metaInfo), command.onBehalfOf)
      state match {
        case state: CreatedState =>
          Right(
            StoreInfoEdited(
              storeId = command.storeId,
              newInfo = command.newInfo.map(info => state.info.toEditable.updateInfo(info)),
              metaInfo = Some(updatedMetaInfo)
            )
          )

        case DraftState(editableInfo, _) =>
          Right(
            StoreInfoEdited(
              storeId = command.storeId,
              newInfo = command.newInfo.map(info => editableInfo.updateInfo(info)),
              metaInfo = Some(updatedMetaInfo)
            )
          )

        case DeletedState(_, _) => Left(StateError("Cannot edit a deleted store"))
      }
    } finally span.end()
  }

  private def addProductsToStore(state: InitializedState, command: AddProductsToStore): Either[Error, StoreEvent] = {
    val span = tracer.startSpan("addProductsToStore")
    try {
      val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)

      state match {
        case initialized: InitializedState =>
          initialized match {
            case created: CreatedState =>
              Right(
                ProductsAddedToStore(
                  storeId = command.storeId,
                  info = Some(
                    StoreOrEditableInfo(
                      StoreOrEditableInfo.InfoOrEditable.Info(created.info.addAllProducts(command.skus))
                    )
                  ),
                  metaInfo = Some(newMetaInfo)
                )
              )
            case draft: DraftState =>
              Right(
                ProductsAddedToStore(
                  storeId = command.storeId,
                  info = Some(
                    StoreOrEditableInfo(
                      StoreOrEditableInfo.InfoOrEditable.EditableInfo(draft.info.addAllProducts(command.skus))
                    )
                  ),
                  metaInfo = Some(newMetaInfo)
                )
              )
            case DeletedState(_, _) => Left(StateError("Cannot add members to a deleted store"))

          }
      }
    } finally span.end()
  }

  private def removeProductsFromStore(
      state: InitializedState,
      command: RemoveProductsFromStore
  ): Either[Error, StoreEvent] = {
    val span = tracer.startSpan("removeProductsFromStore")
    try {
      val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)

      state match {
        case initialized: InitializedState =>
          initialized match {
            case created: CreatedState =>
              Right(
                ProductsRemovedFromStore(
                  storeId = command.storeId,
                  info = Some(
                    StoreOrEditableInfo(
                      StoreOrEditableInfo.InfoOrEditable.Info(
                        created.info
                          .copy(products = created.info.products.filter(product => !command.skus.contains(product)))
                      )
                    )
                  ),
                  metaInfo = Some(newMetaInfo)
                )
              )
            case draft: DraftState =>
              Right(
                ProductsRemovedFromStore(
                  storeId = command.storeId,
                  info = Some(
                    StoreOrEditableInfo(
                      StoreOrEditableInfo.InfoOrEditable.EditableInfo(
                        draft.info
                          .copy(products = draft.info.products.filter(product => !command.skus.contains(product)))
                      )
                    )
                  ),
                  metaInfo = Some(newMetaInfo)
                )
              )
            case DeletedState(_, _) => Left(StateError("Cannot add members to a deleted store"))

          }
      }
    } finally span.end()
  }
}
