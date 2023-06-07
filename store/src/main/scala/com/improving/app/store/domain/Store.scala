package com.improving.app.store.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.common.errors._
import com.improving.app.store.domain.StoreOrEditableInfo.InfoOrEditable.Info
import com.improving.app.store.domain.StoreValidation.{draftTransitionStoreInfoValidator, storeCommandValidator}

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
      val errors: Either[ReplyEffect[StoreEvent, StoreState], StoreRequestPB with StoreRequest] =
        envelope.request match {
          case r: StoreRequest => Right(r)
          case _               => Left(Effect.reply(envelope.replyTo)(StatusReply.Error("Message was not a StoreRequest")))
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
        case Right(Some(errors)) => Effect.reply(envelope.replyTo)(StatusReply.Error(errors.message))
        case Left(r)             => r
      }
  }

  private val eventHandler: (StoreState, StoreEvent) => StoreState = { (state, event) =>
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
            Some(infoOrEditable: StoreOrEditableInfo),
            Some(metaInfo),
            _
          ) =>
        state match {
          case _: DraftState  => DraftState(infoOrEditable.getEditableInfo, metaInfo)
          case _: ReadyState  => ReadyState(infoOrEditable.getInfo, metaInfo)
          case _: OpenState   => OpenState(infoOrEditable.getInfo, metaInfo)
          case _: ClosedState => ClosedState(infoOrEditable.getInfo, metaInfo)
          case x: StoreState  => x
        }
      case _ => state
    }
  }

  private def updateMetaInfo(metaInfo: Option[StoreMetaInfo], lastUpdatedByOpt: Option[MemberId]): StoreMetaInfo = {
    metaInfo.get.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(Timestamp(Instant.now())))
  }

  private def createStore(command: CreateStore): Either[Error, StoreEvent] = {
    command.info match {
      case Some(_) =>
        val newMetaInfo = StoreMetaInfo(
          createdOn = Some(Timestamp(Instant.now())),
          createdBy = command.onBehalfOf,
          lastUpdated = Some(Timestamp(Instant.now())),
          lastUpdatedBy = command.onBehalfOf
        )

        Right(
          StoreCreated(
            storeId = command.storeId,
            info = command.info,
            metaInfo = Some(newMetaInfo)
          )
        )
      case None => Left(ValidationError("EditableStoreInfo not provided in message"))
    }
  }

  private def makeStoreReady(
      state: DraftState,
      command: MakeStoreReady
  ): Either[Error, StoreEvent] = {
    val validationErrorsOpt = draftTransitionStoreInfoValidator(state.info)
    if (validationErrorsOpt.isEmpty) {
      val info = updateDraftInfo(state.info, command.info)
      val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)
      Right(
        StoreIsReady(
          storeId = command.storeId,
          info = Some(
            StoreInfo(
              info.getName,
              info.getDescription,
              info.sponsoringOrg
            )
          ),
          metaInfo = Some(newMetaInfo)
        )
      )
    } else
      Left(StateError(validationErrorsOpt.get.message))

  }

  private def openStore(
      state: CreatedState,
      command: OpenStore
  ): Either[Error, StoreEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)
    Right(
      StoreOpened(
        storeId = command.storeId,
        info = Some(state.info),
        metaInfo = Some(newMetaInfo)
      )
    )
  }

  private def closeStore(
      state: CreatedState,
      command: CloseStore
  ): Either[Error, StoreEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = command.onBehalfOf)
    Right(
      StoreClosed(
        storeId = command.storeId,
        info = Some(state.info),
        metaInfo = Some(newMetaInfo)
      )
    )
  }

  private def deleteStore(
      state: DeletableState,
      command: DeleteStore
  ): Either[Error, StoreEvent] = {
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

  }

  private def terminateStore(state: InitializedState, terminate: TerminateStore): Either[Error, StoreEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = Some(state.metaInfo), lastUpdatedByOpt = terminate.onBehalfOf)

    state match {
      case _ =>
        Right(
          StoreTerminated(
            storeId = terminate.storeId,
            metaInfo = Some(newMetaInfo)
          )
        )
    }
  }

  private def updateDraftInfo(
      stateInfo: EditableStoreInfo,
      newInfoOpt: Option[EditableStoreInfo]
  ): EditableStoreInfo = {
    newInfoOpt match {
      case Some(fieldsToUpdate) =>
        stateInfo.copy(
          name = fieldsToUpdate.name.orElse(stateInfo.name),
          description = fieldsToUpdate.description.orElse(stateInfo.description),
          sponsoringOrg = fieldsToUpdate.sponsoringOrg.orElse(stateInfo.sponsoringOrg),
        )
      case None => stateInfo
    }
  }

  private def editStoreInfo(
      state: InitializedState,
      command: EditStoreInfo
  ): Either[Error, StoreInfoEdited] = state match {
    case state: CreatedState =>
      val updatedInfo = command.newInfo match {
        case Some(fieldsToUpdate) =>
          val stateInfo = state.info

          Some(
            StoreInfo(
              name = fieldsToUpdate.name
                .getOrElse(stateInfo.name),
              description = fieldsToUpdate.description
                .getOrElse(stateInfo.description),
              sponsoringOrg = Some(
                fieldsToUpdate.sponsoringOrg
                  .getOrElse(stateInfo.getSponsoringOrg)
              )
            )
          )
        case None => None
      }
      val updatedMetaInfo = updateMetaInfo(Some(state.metaInfo), command.onBehalfOf)

      Right(
        StoreInfoEdited(
          storeId = command.storeId,
          info = Some(StoreOrEditableInfo(StoreOrEditableInfo.InfoOrEditable.Info(updatedInfo match {
            case Some(info) => info
            case None       => state.info
          }))),
          metaInfo = Some(updatedMetaInfo)
        )
      )

    case DraftState(editableInfo, _) =>
      val updatedMetaInfo = updateMetaInfo(Some(state.metaInfo), command.onBehalfOf)
      Right(
        StoreInfoEdited(
          storeId = command.storeId,
          info = Some(
            StoreOrEditableInfo(
              StoreOrEditableInfo.InfoOrEditable.EditableInfo(updateDraftInfo(editableInfo, command.newInfo))
            )
          ),
          metaInfo = Some(updatedMetaInfo)
        )
      )

    case DeletedState(_, _) => Left(StateError("Cannot edit a deleted store"))
  }
}
