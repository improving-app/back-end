package com.improving.app.store.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors._
import com.improving.app.store.domain.StoreValidation.{
  createdStateStoreInfoValidator,
  draftTransitionStoreInfoValidator,
  storeDescriptionValidator,
  storeNameValidator,
  storeSponsoringOrgValidator
}

import java.time.Instant

object Store {
  val TypeKey: EntityTypeKey[StoreRequestEnvelope] = EntityTypeKey[StoreRequestEnvelope]("Store")

  case class StoreRequestEnvelope(request: StoreRequestPB, replyTo: ActorRef[StatusReply[StoreEvent]])

  sealed private trait StoreState

  sealed private trait EmptyState extends StoreState
  private case object UninitializedState extends EmptyState
  sealed private trait InitializedState extends StoreState {
    val metaInfo: StoreMetaInfo
  }

  sealed private trait CreatedState extends InitializedState {
    val info: StoreInfo
  }

  sealed private trait InactiveState extends InitializedState
  sealed private trait DeletableState extends InitializedState

  private case class DraftState(info: Option[EditableStoreInfo], metaInfo: StoreMetaInfo)
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
                DraftState(info = event.info, metaInfo = event.getMetaInfo)
            }
          case x: StoreState => x
        }
      case event: StoreIsReady =>
        state match {
          case _: DraftState => ReadyState(event.getInfo, event.getMetaInfo)
          case x: StoreState => x
        }
      case event: StoreOpened =>
        state match {
          case x: ReadyState  => OpenState(x.info, event.getMetaInfo)
          case x: ClosedState => OpenState(x.info, event.getMetaInfo)
          case x: StoreState  => x
        }
      case event: StoreClosed =>
        state match {
          case x: ReadyState => ClosedState(x.info, event.getMetaInfo)
          case x: OpenState  => ClosedState(x.info, event.getMetaInfo)
          case x: StoreState => x
        }
      case event: StoreDeleted =>
        state match {
          case _: DraftState  => DeletedState(event.getInfo, event.getMetaInfo)
          case x: ClosedState => DeletedState(x.info, event.getMetaInfo)
          case x: StoreState  => x
        }
      case event: StoreTerminated =>
        state match {
          case x: InitializedState => TerminatedState(event.getMetaInfo)
          case x: StoreState       => x
        }
      case event: StoreInfoEdited =>
        state match {
          case _: DraftState =>
            val nameValidationError = applyAllValidators[StoreInfoEdited](event =>
              storeNameValidator(event.info.map(_.name).getOrElse(""))
            )(event)
            val descriptionValidationError = applyAllValidators[StoreInfoEdited](event =>
              storeDescriptionValidator(event.info.map(_.description).getOrElse(""))
            )(event)
            val sponsoringOrgValidationError = applyAllValidators[StoreInfoEdited](event =>
              required("sponsoring org", storeSponsoringOrgValidator)(event.info.flatMap(_.sponsoringOrg))
            )(event)
            DraftState(
              event.info.map(i =>
                EditableStoreInfo(
                  if (nameValidationError.isDefined) None else Some(i.name),
                  if (descriptionValidationError.isDefined) None else Some(i.description),
                  if (sponsoringOrgValidationError.isDefined) None else i.sponsoringOrg,
                )
              ),
              event.getMetaInfo
            )
          case _: ReadyState  => ReadyState(event.getInfo, event.getMetaInfo)
          case _: OpenState   => OpenState(event.getInfo, event.getMetaInfo)
          case _: ClosedState => ClosedState(event.getInfo, event.getMetaInfo)
          case x: StoreState  => x
        }
    }
  }

  private def updateMetaInfo(metaInfo: StoreMetaInfo, lastUpdatedByOpt: Option[MemberId]): StoreMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(Timestamp(Instant.now())))
  }

  private def createStore(command: CreateStore): Either[Error, StoreEvent] = {
    val maybeValidationError = applyAllValidators[CreateStore](
      c => required("store id", storeIdValidator)(c.storeId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(command)
    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = StoreMetaInfo(
        createdOn = Some(Timestamp(Instant.now())),
        createdBy = Some(command.getOnBehalfOf)
      )

      Right(
        StoreCreated(
          storeId = command.storeId,
          info = command.info,
          metaInfo = Some(newMetaInfo),
        )
      )
    }
  }

  private def makeStoreReady(
      state: DraftState,
      command: MakeStoreReady
  ): Either[Error, StoreEvent] = {
    val draftInfo = state.info.getOrElse(EditableStoreInfo.defaultInstance)

    val maybeValidationError: Option[ValidationError] = applyAllValidators[MakeStoreReady](
      c => required("store id", storeIdValidator)(c.storeId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(command).orElse(
      draftTransitionStoreInfoValidator(draftInfo)
    )

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = command.onBehalfOf)
      Right(
        StoreIsReady(
          storeId = command.storeId,
          info = state.info.map(i => StoreInfo(i.getName, i.getDescription, i.sponsoringOrg)),
          metaInfo = Some(newMetaInfo)
        )
      )
    }
  }
  private def openStore(
      state: CreatedState,
      command: OpenStore
  ): Either[Error, StoreEvent] = {
    val maybeValidationError: Option[ValidationError] = applyAllValidators[OpenStore](
      c => required("store id", storeIdValidator)(c.storeId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(command)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = command.onBehalfOf)
      Right(
        StoreOpened(
          storeId = command.storeId,
          metaInfo = Some(newMetaInfo)
        )
      )
    }
  }

  private def closeStore(
      state: CreatedState,
      command: CloseStore
  ): Either[Error, StoreEvent] = {
    val maybeValidationError: Option[ValidationError] = applyAllValidators[CloseStore](
      c => required("store id", storeIdValidator)(c.storeId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(command)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = command.onBehalfOf)
      Right(
        StoreClosed(
          storeId = command.storeId,
          info = Some(state.info),
          metaInfo = Some(newMetaInfo)
        )
      )
    }
  }

  private def deleteStore(
      state: DeletableState,
      command: DeleteStore
  ): Either[Error, StoreEvent] = {
    val maybeValidationError: Option[ValidationError] = applyAllValidators[DeleteStore](
      c => required("store id", storeIdValidator)(c.storeId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(command)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = command.onBehalfOf)
      val info: Either[ValidationError, StoreInfo] = state match {
        case x: DraftState =>
          val draftInfo = x.info.getOrElse(EditableStoreInfo.defaultInstance)
          val newInfo =
            StoreInfo(draftInfo.getName, draftInfo.getDescription, draftInfo.sponsoringOrg)
          val maybeInfoValidationError: Option[ValidationError] = createdStateStoreInfoValidator(newInfo)
          if (maybeInfoValidationError.isDefined) {
            Left(maybeValidationError.get)
          } else {
            Right(newInfo)
          }
        case x: ClosedState => Right(x.info)
      }

      if (info.isLeft) {
        Left(info.left.getOrElse(ValidationError.apply("Unknown validation error")))
      } else {

        Right(
          StoreDeleted(
            storeId = command.storeId,
            info = Some(info.getOrElse(StoreInfo.defaultInstance)),
            metaInfo = Some(newMetaInfo)
          )
        )
      }
    }
  }

  private def terminateStore(state: InitializedState, terminate: TerminateStore): Either[Error, StoreEvent] = {
    val maybeValidationError: Option[ValidationError] = applyAllValidators[TerminateStore](
      c => required("store id", storeIdValidator)(c.storeId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(terminate)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = terminate.onBehalfOf)
      Right(
        StoreTerminated(
          storeId = terminate.storeId,
          metaInfo = Some(newMetaInfo)
        )
      )
    }
  }

  private def editStoreInfo(
      state: InitializedState,
      command: EditStoreInfo
  ): Either[Error, StoreInfoEdited] = {
    val maybeValidationError = applyAllValidators[EditStoreInfo](
      command => required("store id", storeIdValidator)(command.storeId),
      command => required("on behalf of", memberIdValidator)(command.onBehalfOf),
    )(command)
    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val stateInfo: Either[ValidationError, StoreInfo] = state match {
        case x: DraftState =>
          val draftInfo = x.info.getOrElse(EditableStoreInfo.defaultInstance)
          Right(StoreInfo(draftInfo.getName, draftInfo.getDescription, draftInfo.sponsoringOrg))
        case x: CreatedState => Right(x.info)
        case _               => Left(ValidationError.apply("Editing is not allowed in this state"))
      }

      if (stateInfo.isLeft) {
        Left(stateInfo.left.getOrElse(ValidationError.apply("Unknown validation error")))
      } else {
        val validStateInfo = stateInfo.getOrElse(StoreInfo.defaultInstance)
        val fieldsToUpdate = command.newInfo.get

        val updatedInfo = StoreInfo(
          name = fieldsToUpdate.name.getOrElse(validStateInfo.name),
          description = fieldsToUpdate.description.getOrElse(validStateInfo.description),
          sponsoringOrg = Some(
            fieldsToUpdate.sponsoringOrg.getOrElse(
              validStateInfo.sponsoringOrg.getOrElse(OrganizationId.defaultInstance)
            )
          )
        )

        val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

        Right(
          StoreInfoEdited(
            storeId = command.storeId,
            info = Some(updatedInfo),
            metaInfo = Some(updatedMetaInfo)
          )
        )
      }
    }
  }
}
