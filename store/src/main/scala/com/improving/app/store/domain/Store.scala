package com.improving.app.store.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.common.errors.Validation.{applyAllValidators, storeIdValidator}
import com.improving.app.common.errors._
import com.improving.app.common.service.util.{doForOptionIfHas, doForSameIfHas, doIfHas}
import com.improving.app.store.domain.StoreValidation.draftTransitionStoreInfoValidator

import java.time.Instant

object Store {
  val TypeKey: EntityTypeKey[StoreRequestEnvelope] = EntityTypeKey[StoreRequestEnvelope]("Store")

  case class StoreRequestEnvelope(request: StoreRequestPB, replyTo: ActorRef[StatusReply[StoreEvent]])

  sealed private trait StoreState

  sealed private trait EmptyState extends StoreState

  private case object UninitializedState extends EmptyState

  sealed private trait InitializedState extends StoreState {
    def metaInfo: StoreMetaInfo
  }

  sealed private trait CreatedState extends InitializedState {
    val info: StoreInfo
    override val metaInfo: StoreMetaInfo
  }

  sealed private trait InactiveState extends InitializedState
  sealed private trait DeletableState extends InitializedState

  private case class DraftState(info: EditableStoreInfo, metaInfo: StoreMetaInfo)
      extends InactiveState
      with DeletableState
  private case class ReadyState(info: StoreInfo, metaInfo: StoreMetaInfo) extends CreatedState
  private case class OpenState(info: StoreInfo, metaInfo: StoreMetaInfo) extends CreatedState
  private case class ClosedState(info: StoreInfo, metaInfo: StoreMetaInfo) extends CreatedState with DeletableState
  private case class DeletedState(info: StoreInfo, metaInfo: StoreMetaInfo) extends InactiveState
  private case class TerminatedState(lastMeta: StoreMetaInfo) extends EmptyState

  def apply(persistenceId: PersistenceId): Behavior[StoreRequestEnvelope] = {
    Behaviors.setup(context =>
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
      val result: Either[Error, StoreEvent] = state match {
        case _: EmptyState =>
          envelope.request match {
            case command: CreateStore => createStore(command)
            case _                    => Left(StateError("Message type not supported in empty state"))
          }
        case draftState: DraftState =>
          envelope.request match {
            case command: MakeStoreReady => makeStoreReady(draftState, command)
            case command: DeleteStore    => deleteStore(draftState, command)
            case command: TerminateStore => terminateStore(draftState, command)
            case command: EditStoreInfo  => editStoreInfo(draftState, command)
            case _                       => Left(StateError("Message type not supported in draft state"))
          }
        case readyState: ReadyState =>
          envelope.request match {
            case command: OpenStore      => openStore(readyState, command)
            case command: CloseStore     => closeStore(readyState, command)
            case command: TerminateStore => terminateStore(readyState, command)
            case command: EditStoreInfo  => editStoreInfo(readyState, command)
            case _: DeleteStore          => Left(StateError("Store must be closed before deleting"))
            case _                       => Left(StateError("Message type not supported in ready state"))
          }
        case openState: OpenState =>
          envelope.request match {
            case command: CloseStore     => closeStore(openState, command)
            case command: TerminateStore => terminateStore(openState, command)
            case command: EditStoreInfo  => editStoreInfo(openState, command)
            case _: DeleteStore          => Left(StateError("Store must be closed before deleting"))
            case _                       => Left(StateError("Message type not supported in open state"))
          }
        case closedState: ClosedState =>
          envelope.request match {
            case command: OpenStore      => openStore(closedState, command)
            case command: DeleteStore    => deleteStore(closedState, command)
            case command: TerminateStore => terminateStore(closedState, command)
            case command: EditStoreInfo  => editStoreInfo(closedState, command)
            case _                       => Left(StateError("Message type not supported in closed state"))
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
  }

  private val eventHandler: (StoreState, StoreEvent) => StoreState = { (state, event) =>
    event match {
      case StoreEvent.Empty => state
      case event: StoreCreated =>
        state match {
          case s: EmptyState =>
            s match {
              case t: TerminatedState => t
              case _ =>
                DraftState(info = event.info, metaInfo = event.metaInfo)
            }
          case x: StoreState => x
        }
      case event: StoreIsReady =>
        state match {
          case _: DraftState => ReadyState(event.info, event.metaInfo)
          case x: StoreState => x
        }
      case event: StoreOpened =>
        state match {
          case x: ReadyState  => OpenState(x.info, event.metaInfo)
          case x: ClosedState => OpenState(x.info, event.metaInfo)
          case x: StoreState  => x
        }
      case event: StoreClosed =>
        state match {
          case x: ReadyState => ClosedState(x.info, event.metaInfo)
          case x: OpenState  => ClosedState(x.info, event.metaInfo)
          case x: StoreState => x
        }
      case event: StoreDeleted =>
        state match {
          case _: DraftState  => DeletedState(event.info, event.metaInfo)
          case x: ClosedState => DeletedState(x.info, event.metaInfo)
          case x: StoreState  => x
        }
      case event: StoreTerminated =>
        state match {
          case _: InitializedState => TerminatedState(event.metaInfo)
          case x: StoreState       => x
        }
      case event: StoreInfoEdited =>
        state match {
          case _: DraftState  => DraftState(event.info.getEditableInfo, event.metaInfo)
          case _: ReadyState  => ReadyState(event.info.getInfo, event.metaInfo)
          case _: OpenState   => OpenState(event.info.getInfo, event.metaInfo)
          case _: ClosedState => ClosedState(event.info.getInfo, event.metaInfo)
          case x: StoreState  => x
        }
    }
  }

  private def updateMetaInfo(metaInfo: StoreMetaInfo, lastUpdatedByOpt: MemberId): StoreMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Timestamp(Instant.now()))
  }

  private def createStore(command: CreateStore): Either[Error, StoreEvent] = {
    val newMetaInfo = StoreMetaInfo(
      createdOn = Timestamp(Instant.now()),
      createdBy = command.onBehalfOf,
      lastUpdated = Timestamp(Instant.now()),
      lastUpdatedBy = command.onBehalfOf
    )

    Right(
      StoreCreated(
        storeId = command.storeId,
        info = command.getInfo,
        metaInfo = newMetaInfo
      )
    )
  }

  private def makeStoreReady(
      state: DraftState,
      command: MakeStoreReady
  ): Either[Error, StoreEvent] = {
    val validationErrorsOpt = draftTransitionStoreInfoValidator(state.info)
    if (validationErrorsOpt.isEmpty) {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = command.onBehalfOf)
      Right(
        StoreIsReady(
          storeId = command.storeId,
          info = StoreInfo(state.info.getName, state.info.getDescription, state.info.getSponsoringOrg),
          metaInfo = newMetaInfo
        )
      )
    } else
      Left(StateError(validationErrorsOpt.get.message))
  }

  private def openStore(
      state: CreatedState,
      command: OpenStore
  ): Either[Error, StoreEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = command.onBehalfOf)
    Right(
      StoreOpened(
        storeId = command.storeId,
        info = state.info,
        metaInfo = newMetaInfo
      )
    )
  }

  private def closeStore(
      state: CreatedState,
      command: CloseStore
  ): Either[Error, StoreEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = command.onBehalfOf)
    Right(
      StoreClosed(
        storeId = command.storeId,
        info = state.info,
        metaInfo = newMetaInfo
      )
    )
  }

  private def deleteStore(
      state: DeletableState,
      command: DeleteStore
  ): Either[Error, StoreEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = command.onBehalfOf)

    state match {
      case DraftState(editable, _) =>
        Right(
          StoreDeleted(
            storeId = command.storeId,
            info = StoreInfo(editable.getName, editable.getDescription, editable.getSponsoringOrg),
            metaInfo = newMetaInfo
          )
        )
      case ClosedState(info, _) =>
        Right(
          StoreDeleted(
            storeId = command.storeId,
            info = info,
            metaInfo = newMetaInfo
          )
        )
    }

  }

  private def terminateStore(state: InitializedState, terminate: TerminateStore): Either[Error, StoreEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = terminate.onBehalfOf)

    state match {
      case DraftState(_, _) =>
        Right(
          StoreTerminated(
            storeId = terminate.storeId,
            metaInfo = newMetaInfo
          )
        )
      case DeletedState(_, _) =>
        Right(
          StoreTerminated(
            storeId = terminate.storeId,
            metaInfo = newMetaInfo
          )
        )
      case _: CreatedState =>
        Right(
          StoreTerminated(
            storeId = terminate.storeId,
            metaInfo = newMetaInfo
          )
        )
    }
  }

  private def editStoreInfo(
      state: InitializedState,
      command: EditStoreInfo
  ): Either[Error, StoreInfoEdited] = state match {
    case state: CreatedState =>
      val fieldsToUpdate = command.newInfo

      val updatedInfo = StoreInfo(
        name = doForSameIfHas[String](fieldsToUpdate.name, state.info.name),
        description = doForSameIfHas[String](fieldsToUpdate.description, state.info.description),
        sponsoringOrg = doForSameIfHas[OrganizationId](fieldsToUpdate.sponsoringOrg, state.info.sponsoringOrg)
      )

      val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

      Right(
        StoreInfoEdited(
          storeId = command.storeId,
          info = StoreOrEditableInfo(StoreOrEditableInfo.InfoOrEditable.Info(updatedInfo)),
          metaInfo = updatedMetaInfo
        )
      )

    case DraftState(editableInfo, _) =>
      val fieldsToUpdate = command.newInfo

      val updatedInfo = editableInfo.copy(
        name = doForOptionIfHas[String](fieldsToUpdate.name, editableInfo.name),
        description = doForOptionIfHas[String](fieldsToUpdate.description, editableInfo.description),
        sponsoringOrg = doForOptionIfHas[OrganizationId](
          fieldsToUpdate.sponsoringOrg,
          editableInfo.sponsoringOrg,
        )
      )

      val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

      Right(
        StoreInfoEdited(
          storeId = command.storeId,
          info = StoreOrEditableInfo(StoreOrEditableInfo.InfoOrEditable.EditableInfo(updatedInfo)),
          metaInfo = updatedMetaInfo
        )
      )
    case DeletedState(_, _) => Left(StateError("Cannot edit a deleted store"))
  }
}
