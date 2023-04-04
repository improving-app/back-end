package com.improving.app.organization.domain

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl._
import cats.data.Validated
import cats.implicits.toFoldableOps
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain._
import com.improving.app.organization._
import com.improving.app.organization.domain.OrganizationValidation.ValidationResult
import com.improving.app.organization.repository.OrganizationRepository
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.concurrent.ExecutionContext

object Organization {
  import com.improving.app.organization.domain.OrganizationStateOps._

  private val log =
    LoggerFactory.getLogger(
      "com.improving.app.organization.domain.Organization"
    )

  //TODO add all extra validation from the riddl (on things other than existince/nonexistence)

  val config: Config = ConfigFactory.load()

  def now: Instant = java.time.Instant.now()

  val index: Int = config.getInt("organization-service.tags")

  val tags: Vector[String] = Vector.tabulate(index)(i => s"organizations-$i")

  val OrganizationEntityKey: EntityTypeKey[OrganizationCommand] =
    EntityTypeKey[OrganizationCommand]("Organization")

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[OrganizationCommand] => Behavior[OrganizationCommand] = { entityContext =>
      Organization(entityContext.entityId, None)
    }
    ClusterSharding(system).init(Entity(OrganizationEntityKey)(behaviorFactory))
  }

  final case class OrganizationCommand(
      request: OrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  )

  private def emptyState(id: String): OrganizationState =
    InitialEmptyOrganizationState(Some(OrganizationId(id)))

  def apply(
      orgId: String, repo: Option[OrganizationRepository]
  ): Behavior[OrganizationCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting Organization {}", orgId)
      EventSourcedBehavior
        .withEnforcedReplies[
          OrganizationCommand,
          OrganizationEvent,
          OrganizationState
        ](
          persistenceId = PersistenceId("Organization", orgId),
          emptyState = emptyState(orgId),
          commandHandler = commandHandler(repo, context.executionContext, _, _),
          eventHandler = eventHandler
        )
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            context.log.debug("onRecoveryCompleted: [{}]", state)
          case (_, PostStop) =>
            context.log.info("Organization {} stopped", orgId)
        }
        .withTagger(_ => tags.toSet)
    }

  /**
   * @param actingMember the member that acted on the organization
   * @return new MetaInfo with draft status
   */
  def createMetaInfo(actingMember: Option[MemberId]): MetaInfo = {
    val timestamp = Timestamp.of(now.getEpochSecond, now.getNano)
    MetaInfo(
      createdOn = Some(timestamp),
      createdBy = actingMember,
      lastUpdated = Some(timestamp),
      lastUpdatedBy = actingMember,
      currentStatus = OrganizationStatus.ORGANIZATION_STATUS_DRAFT
    )
  }

  def updateMetaInfo(
      metaInfo: MetaInfo,
      actingMember: Option[MemberId],
      newStatus: Option[OrganizationStatus] = None,
      children: Seq[OrganizationId] = Seq.empty[OrganizationId]
  ): MetaInfo = {
    val timestamp = Timestamp.of(now.getEpochSecond, now.getNano)
    metaInfo.copy(
      lastUpdated = Some(timestamp),
      lastUpdatedBy = actingMember,
      currentStatus = newStatus.getOrElse(metaInfo.currentStatus)
    )
  }

  def updateInfo(info: Info, newInfo: Info): Info = {
    info.copy(
      name = newInfo.name,
      shortName = newInfo.shortName.orElse(info.shortName),
      address = newInfo.address.orElse(info.address),
      isPrivate = newInfo.isPrivate,
      url = newInfo.url.orElse(info.url),
      logo = newInfo.logo.orElse(info.logo),
      tenant = newInfo.tenant.orElse(info.tenant)
    )
  }


  /**
   * @param state - current state of the organization
   * @param command - command to be executed on the organization
   * @return boolean - validate if state transition is valid for the command.
   */
  private def isCommandValidForState(
      state: OrganizationState,
      command: OrganizationRequest
  ): Boolean = {
    //TODO how wedded are we to the specific errors in the riddl? If we don't mind having a generic mismatch message then this works fine
    state match {
      case _: InitialEmptyOrganizationState =>
        command match {
          case _: EstablishOrganizationRequest => true
          case _                               => false
        }
      case _: DraftOrganizationState =>
        command match { //TODO confirm you can Terminate from Draft
          case _: EditOrganizationInfoRequest | _: UpdateParentRequest | _: AddMembersToOrganizationRequest |
               _: RemoveMembersFromOrganizationRequest | _: AddOwnersToOrganizationRequest |
               _: RemoveOwnersFromOrganizationRequest | _: UpdateOrganizationContactsRequest |
               _: GetOrganizationByIdRequest | _: GetOrganizationInfoRequest | _: TerminateOrganizationRequest |
               _: ActivateOrganizationRequest => true
          case _ => false
        }
      case state: EstablishedOrganizationState if state.isSuspended =>
        command match {
          case _: ActivateOrganizationRequest | _: TerminateOrganizationRequest | _: GetOrganizationByIdRequest
               | _: GetOrganizationInfoRequest | _: SuspendOrganizationRequest => true
          case _ => false
        }
      case _: EstablishedOrganizationState =>
        command match {
          case _: EditOrganizationInfoRequest | _: UpdateParentRequest | _: AddMembersToOrganizationRequest |
               _: RemoveMembersFromOrganizationRequest | _: AddOwnersToOrganizationRequest |
               _: RemoveOwnersFromOrganizationRequest | _: UpdateOrganizationContactsRequest |
               _: SuspendOrganizationRequest | _: GetOrganizationByIdRequest | _: GetOrganizationInfoRequest |
               _: TerminateOrganizationRequest => true
          case _ => false
        }
      case _: TerminatedOrganizationState => false
      case OrganizationState.Empty => false
    }
  }

  private def buildInfoFromDraft(draft: DraftOrganizationState): Option[Info] = {
    for {
      required <- draft.requiredDraftInfo
      optional <- draft.optionalDraftInfo
    }
      yield Info(
        name = required.name,
        shortName = optional.shortName,
        address = optional.address,
        isPrivate = Some(required.isPrivate),
        url = optional.url,
        logo = optional.logo,
        tenant = required.tenant
      )
  }

  private def getOrganizationInfo(
      orgId: Option[OrganizationId],
      state: OrganizationState,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    log.info(s"getOrganizationInfo: orgId ${orgId.map(_.id)}")
    state match {
      case establishedOrganizationState: EstablishedOrganizationState =>
        Effect.reply(replyTo) {
          StatusReply.Success(
            OrganizationInfo(
              orgId,
              establishedOrganizationState.info
            )
          )
        }
      case draft: DraftOrganizationState =>
        Effect.reply(replyTo) {
          StatusReply.Success(
            OrganizationInfo(
              orgId,
              buildInfoFromDraft(draft)
            )
          )
        }
      case _ =>
        Effect.reply(replyTo) {
          StatusReply.Error(
            s"Organization ${orgId.map(_.id)} is not established"
          )
        }
    }
  }


  private def getOrganizationById(
      orgId: Option[OrganizationId],
      state: OrganizationState,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    log.info(s"getOrganizationById: orgId ${orgId.map(_.id)}")
    state match {
      case draft: DraftOrganizationState =>
        val org = for {
          required <- draft.requiredDraftInfo
          optional <- draft.optionalDraftInfo
        } yield com.improving.app.organization.Organization(
          orgId = orgId,
          info = buildInfoFromDraft(draft),
          parent = optional.parent,
          members = optional.members,
          owners = required.owners,
          contacts = optional.contacts,
          meta = draft.orgMeta
        )
        org match {
          case Some(org) => Effect.reply(replyTo) {
            StatusReply.Success(org)
          }
          case None => Effect.reply(replyTo) {
            StatusReply.Error(
              s"Internal error in draft state for organization ${orgId.map(_.id)}"
            )
          }
        }
      case establishedOrganizationState: EstablishedOrganizationState =>
        Effect.reply(replyTo) {
          StatusReply.Success(
            com.improving.app.organization.Organization(
              orgId,
              establishedOrganizationState.info,
              establishedOrganizationState.parent,
              establishedOrganizationState.members,
              establishedOrganizationState.owners,
              establishedOrganizationState.contacts,
              establishedOrganizationState.meta
            )
          )
        }
      case _ => Effect.reply(replyTo) {
        StatusReply.Error(
          s"Organization ${orgId.map(_.id)} is not established"
        )
      }
    }

  }

  private def updateOrganizationContacts(
      uocr: UpdateOrganizationContactsRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationContactsUpdated(
      uocr.orgId,
      uocr.contacts,
      uocr.actingMember
    )
    log.info(
      s"updateOrganizationContacts: UpdateOrganizationContactsRequest $uocr"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def updateParent(
      upr: UpdateParentRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    //TODO implement child/parent checks per riddl
    //TODO if you try to make it a root when you set parent=None, thot would also move any and all children to a different organization
    val event = ParentUpdated(
      upr.orgId,
      upr.newParent,
      upr.actingMember
    )
    log.info(
      s"updateParent: UpdateParentRequest $upr"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def terminateOrganization(
      tor: TerminateOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationTerminated(
      tor.orgId,
      tor.actingMember
    )
    log.info(
      s"terminateOrganization: TerminateOrganizationRequest $tor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def suspendOrganization(
      sor: SuspendOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationSuspended(
      sor.orgId,
      sor.actingMember
    )
    log.info(
      s"suspendOrganization: SuspendOrganizationRequest $sor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def activateOrganization(
      aor: ActivateOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationActivated(
      aor.orgId,
      aor.actingMember
    )
    log.info(
      s"activateOrganization: ActivateOrganizationRequest $aor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def editOrganizationInfo(
      eoir: EditOrganizationInfoRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
      val event = OrganizationInfoEdited(
        eoir.orgId,
        eoir.info,
        eoir.actingMember
      )
      log.info(s"editOrganizationInfo: EditOrganizationInfoRequest $eoir")
        Effect.persist(event).thenReply(replyTo) { _ =>
          StatusReply.Success(OrganizationEventResponse(event))
        }
  }

  private def removeOwnersFromOrganization(
      rfor: RemoveOwnersFromOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OwnersRemovedFromOrganization(
      rfor.orgId,
      rfor.removedOwners,
      rfor.actingMember
    )
    log.info(
      s"removeOwnersFromOrganization: RemoveOwnersFromOrganizationRequest $rfor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def addOwnersToOrganization(
      aoor: AddOwnersToOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OwnersAddedToOrganization(
      aoor.orgId,
      aoor.newOwners,
      aoor.actingMember
    )
    log.info(s"addOwnersToOrganization: OwnersAddedToOrganization $aoor")
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def removeMembersFromOrganization(
      rfor: RemoveMembersFromOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = MembersRemovedFromOrganization(
      rfor.orgId,
      rfor.removedMembers,
      rfor.actingMember
    )
    log.info(
      s"removeMembersFromOrganization: RemoveMembersFromOrganizationRequest $rfor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def addMembersToOrganization(
      amor: AddMembersToOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = MembersAddedToOrganization(
      amor.orgId,
      amor.newMembers,
      amor.actingMember
    )
    log.info(s"addMembersToOrganization: AddMembersToOrganizationRequest $amor")
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def establishOrganization(
      eor: EstablishOrganizationRequest,
      orgId: Option[OrganizationId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
      val event = OrganizationEstablished(
        orgId,
        eor.info.map(info => info.copy(isPrivate = info.isPrivate.orElse(Some(true)))),
        eor.parent,
        eor.members,
        eor.owners,
        eor.contacts,
        eor.actingMember
      )
      log.info(s"establishOrganization: event $event")
      Effect.persist(event).thenReply(replyTo) { _ =>
        StatusReply.Success(OrganizationEventResponse(event))
      }
  }

  private def handleCommandWithValidate[T <: OrganizationRequest](request: T,
          replyTo: ActorRef[StatusReply[OrganizationResponse]],
          validator: T => ValidationResult[T],
          handler: (T, ActorRef[StatusReply[OrganizationResponse]])  => ReplyEffect[OrganizationEvent, OrganizationState]): ReplyEffect[OrganizationEvent, OrganizationState] = {
    validator(request) match {
      case Validated.Valid(_) =>
        handler(request, replyTo)
      case Validated.Invalid(errors) =>
        Effect.reply(replyTo) {
          StatusReply.Error(
            errors
              .map {
                _.errorMessage
              }
              .toList
              .mkString(start = "Invalid request with errors: ", sep = ", ", end = ".")
          )
        }
    }
  }

  private val commandHandler: (
      Option[OrganizationRepository],
      ExecutionContext,
      OrganizationState,
      OrganizationCommand
  ) => ReplyEffect[OrganizationEvent, OrganizationState] = { (maybeRepo, executionContext, state, command: OrganizationCommand) =>
    {
        implicit val ec: ExecutionContext = executionContext
          command.request match {
            case cmd if !isCommandValidForState(state, cmd) =>
              val stateString = state match {
                case _: InitialEmptyOrganizationState => "Empty"
                case _: DraftOrganizationState => "Draft"
                case e: EstablishedOrganizationState if e.isSuspended => "Suspended"
                case _: EstablishedOrganizationState => "Active"
                case _: TerminatedOrganizationState => "Terminated"
                case _ => "Unknown"
              }
              Effect.reply(command.replyTo) {
                StatusReply.Error(
                  s"Invalid Command ${command.request.getClass.getSimpleName} for State $stateString"
                )
              }
            case uopc: UpdateOrganizationContactsRequest =>
              handleCommandWithValidate(uopc, command.replyTo, OrganizationValidation.validateUpdateOrganizationContactsRequest, updateOrganizationContacts)
            case upr: UpdateParentRequest =>
              val validateParent = OrganizationValidation.validateUpdateParentRequest(_, maybeRepo)
              handleCommandWithValidate(upr, command.replyTo, validateParent, updateParent)
            case tor: TerminateOrganizationRequest =>
              handleCommandWithValidate(tor, command.replyTo, OrganizationValidation.validateBasicRequest[TerminateOrganizationRequest], terminateOrganization)
            case sor: SuspendOrganizationRequest =>
              handleCommandWithValidate(sor, command.replyTo, OrganizationValidation.validateBasicRequest[SuspendOrganizationRequest], suspendOrganization)
            case aor: ActivateOrganizationRequest =>
              handleCommandWithValidate(aor, command.replyTo, OrganizationValidation.validateBasicRequest[ActivateOrganizationRequest], activateOrganization)
            case eoir: EditOrganizationInfoRequest =>
              handleCommandWithValidate(eoir, command.replyTo, OrganizationValidation.validateEditOrganizationInfoRequest, editOrganizationInfo)
            case rfor: RemoveOwnersFromOrganizationRequest =>
              val removeOwnersValidate = (x: RemoveOwnersFromOrganizationRequest) => OrganizationValidation.validateRemoveOwnersFromOrganizationRequest(x, state.getOwners)
              handleCommandWithValidate(rfor, command.replyTo, removeOwnersValidate, removeOwnersFromOrganization)
            case aoor: AddOwnersToOrganizationRequest =>
              handleCommandWithValidate(aoor, command.replyTo, OrganizationValidation.validateAddOwnersToOrganizationRequest, addOwnersToOrganization)
            case rfor: RemoveMembersFromOrganizationRequest =>
              handleCommandWithValidate(rfor, command.replyTo, OrganizationValidation.validateRemoveMembersFromOrganizationRequest, removeMembersFromOrganization)
            case amor: AddMembersToOrganizationRequest =>
              handleCommandWithValidate(amor, command.replyTo, OrganizationValidation.validateAddMembersToOrganizationRequest, addMembersToOrganization)
            case eor: EstablishOrganizationRequest =>
              val validateEstablish = OrganizationValidation.validateEstablishOrganizationRequest(_, state.getOrgId, maybeRepo)
              val establishOrganizationCommand = (x: EstablishOrganizationRequest, y: ActorRef[StatusReply[OrganizationResponse]]) => establishOrganization(x, state.getOrgId, y)
              handleCommandWithValidate(eor, command.replyTo, validateEstablish, establishOrganizationCommand)
            case gobir: GetOrganizationByIdRequest =>
              val getOrganizationByIdCommand = (x: GetOrganizationByIdRequest, y: ActorRef[StatusReply[OrganizationResponse]]) => getOrganizationById(x.orgId, state, y)
              handleCommandWithValidate(gobir, command.replyTo, OrganizationValidation.validateGetOrganizationByIdRequest, getOrganizationByIdCommand)
            case goir: GetOrganizationInfoRequest =>
              val getOrganizationInfoCommand = (x: GetOrganizationInfoRequest, y: ActorRef[StatusReply[OrganizationResponse]]) => getOrganizationInfo(x.orgId, state, y)
              handleCommandWithValidate(goir, command.replyTo, OrganizationValidation.validateGetOrganizationInfoRequest, getOrganizationInfoCommand)
            case other =>
              throw new RuntimeException(s"Invalid/Unhandled request $other")
          }
    }
  }

  private def emptyInitialEventHandler(emptyState: InitialEmptyOrganizationState, event: OrganizationEvent): Either[String, OrganizationState] = event match {
    case OrganizationEstablished(
        _,
        info,
        parent,
        members,
        owners,
        contacts,
        actingMember,
        _
        ) =>
      Right(DraftOrganizationState(
        orgId = emptyState.orgId,
        requiredDraftInfo = Some(RequiredDraftInfo(
          name = info.flatMap(_.name),
          isPrivate = info.flatMap(_.isPrivate).get, //The options here are to use get here, which I don't love, or not default it earlier, which I also don't love, or have two places where we're setting the default, which I really don't love. We could also make it an Option here but that seems kind of silly.
          tenant = info.flatMap(_.tenant),
          owners = owners
        )),
        optionalDraftInfo = Some(OptionalDraftInfo(
          shortName = info.flatMap(_.shortName),
          address = info.flatMap(_.address),
          url = info.flatMap(_.url),
          logo = info.flatMap(_.logo),
          parent = parent,
          contacts = contacts,
          members = members
        )),
        orgMeta = Some(createMetaInfo(actingMember))
      ))
    case other => Left(s"Event $other not supported in empty state")
  }

  private def draftEventHandler(draft: DraftOrganizationState, event: OrganizationEvent): Either[String, OrganizationState] = event match {
    case OrganizationInfoEdited(_, info, actingMember, _ ) =>
      val newMeta = draft.orgMeta.map(updateMetaInfo(_, actingMember))
      info match {
        case None => Right(draft.copy(orgMeta = newMeta))
        case Some(info) =>
          val newName: Option[String] = info.name.orElse(draft.requiredDraftInfo.flatMap(_.name))
          val newIsPrivate: Boolean = info.isPrivate.getOrElse(draft.requiredDraftInfo.map(_.isPrivate).get) //as ever I do not love get but we can only reach here from draft state and the required inf can't be none. But we may just want to change it into an option boolean in required. >:/
          val newTenant: Option[TenantId] = info.tenant.orElse(draft.requiredDraftInfo.flatMap(_.tenant))
          val newShortName: Option[String] = info.shortName.orElse(draft.optionalDraftInfo.flatMap(_.shortName))
          val newAddress: Option[Address] = info.address.orElse(draft.optionalDraftInfo.flatMap(_.address))
          val newUrl: Option[String] = info.url.orElse(draft.optionalDraftInfo.flatMap(_.url))
          val newLogo: Option[String] = info.logo.orElse(draft.optionalDraftInfo.flatMap(_.logo))
          Right(draft.copy(
            requiredDraftInfo = draft.requiredDraftInfo.map(_.copy(
              name = newName,
              isPrivate = newIsPrivate,
              tenant = newTenant
            )),
            optionalDraftInfo = draft.optionalDraftInfo.map(_.copy(
              shortName = newShortName,
              address = newAddress,
              url = newUrl,
              logo = newLogo
            )),
            orgMeta = newMeta
          ))
      }
    case ParentUpdated(_, newParent, actingMember, _) =>
      Right(draft.copy(
        optionalDraftInfo = draft.optionalDraftInfo.map(_.copy(parent = newParent)),
        orgMeta = draft.orgMeta.map(
          updateMetaInfo(
            _,
            actingMember
          )
        ))
      )
    case MembersAddedToOrganization(_, newMembers, actingMember, _) =>
      val dedupedMembers = (draft.getMembers ++ newMembers).distinct
      Right(draft.copy(
        optionalDraftInfo = draft.optionalDraftInfo.map(_.copy(members = dedupedMembers)),
        orgMeta = draft.orgMeta.map(updateMetaInfo(_, actingMember))
      ))
    case MembersRemovedFromOrganization(_, removedMembers, actingMember, _) =>
      val newMembers = draft.getMembers.filterNot(removedMembers.contains)
      Right(draft.copy(
        optionalDraftInfo = draft.optionalDraftInfo.map(_.copy(members = newMembers)),
        orgMeta = draft.orgMeta.map(updateMetaInfo(_, actingMember))
      ))
    case OwnersAddedToOrganization(_, newOwners, actingMember, _) =>
      val dedupedOwners = (draft.getOwners ++ newOwners).distinct
      Right(draft.copy(
        requiredDraftInfo = draft.requiredDraftInfo.map(_.copy(owners = dedupedOwners)),
        orgMeta = draft.orgMeta.map(updateMetaInfo(_, actingMember))
      ))
    case OwnersRemovedFromOrganization(_, removedOwners, actingMember, _) =>
      val newOwners = draft.getOwners.filterNot(removedOwners.contains)
      Right(draft.copy(
        requiredDraftInfo = draft.requiredDraftInfo.map(_.copy(owners = newOwners)),
        orgMeta = draft.orgMeta.map(updateMetaInfo(_, actingMember))
      ))
    case OrganizationContactsUpdated(_, newContacts, actingMember, _) =>
    val dedupedNewContacts = (draft.getContacts ++ newContacts).distinct
      Right(draft.copy(
        optionalDraftInfo = draft.optionalDraftInfo.map(_.copy(contacts = dedupedNewContacts)),
        orgMeta = draft.orgMeta.map(updateMetaInfo(_, actingMember))
      ))
    case OrganizationActivated(_, actingMember, _) =>
      Right(EstablishedOrganizationState(
        orgId = draft.orgId,
        info = Some(Info(
          name = draft.requiredDraftInfo.flatMap(_.name),
          shortName = draft.optionalDraftInfo.flatMap(_.shortName),
          isPrivate = draft.requiredDraftInfo.map(_.isPrivate),
          tenant = draft.requiredDraftInfo.flatMap(_.tenant),
          address = draft.optionalDraftInfo.flatMap(_.address),
          url = draft.optionalDraftInfo.flatMap(_.url),
          logo = draft.optionalDraftInfo.flatMap(_.logo)
        )),
        parent = draft.optionalDraftInfo.flatMap(_.parent),
        contacts = draft.optionalDraftInfo.map(_.contacts).getOrElse(Seq.empty[Contacts]),
        members = draft.optionalDraftInfo.map(_.members).getOrElse(Seq.empty[MemberId]),
        owners = draft.requiredDraftInfo.map(_.owners).getOrElse(Seq.empty[MemberId]),
        meta = draft.orgMeta.map(updateMetaInfo(_, actingMember, Some(OrganizationStatus.ORGANIZATION_STATUS_ACTIVE)))
      ))
    case OrganizationTerminated(orgId, actingMember, _) =>
      Right(TerminatedOrganizationState(
        orgId = orgId,
        lastMeta = draft.orgMeta.map(updateMetaInfo(_, actingMember, Some(OrganizationStatus.ORGANIZATION_STATUS_TERMINATED)))
      ))
    case _: OrganizationEstablished => Left("Organization already established")
    case other => Left(s"Event $other not supported in state draft")
  }

  private def activeEventHandler(established: EstablishedOrganizationState, event: OrganizationEvent): Either[String, OrganizationState] = event match {
    case OrganizationInfoEdited(_, newInfo, actingMember, _) =>
      val newMeta = established.meta.map(updateMetaInfo(_, actingMember))
      newInfo match {
        case None => Right(established.copy(meta = newMeta))
        case Some(info) => Right(established.copy(
          info = Some(Info(
            name = info.name.orElse(established.info.flatMap(_.name)),
            shortName = info.shortName.orElse(established.info.flatMap(_.shortName)),
            isPrivate = info.isPrivate.orElse(established.info.flatMap(_.isPrivate)),
            tenant = info.tenant.orElse(established.info.flatMap(_.tenant)),
            address = info.address.orElse(established.info.flatMap(_.address)),
            url = info.url.orElse(established.info.flatMap(_.url)),
            logo = info.logo.orElse(established.info.flatMap(_.logo))
          )),
          meta = newMeta
        ))
      }
    case ParentUpdated(_, newParent, actingMember, _) =>
      Right(established.copy(
        parent = newParent,
        meta = established.meta.map(updateMetaInfo(_, actingMember))
      ))
    case MembersAddedToOrganization(_, newMembers, actingMember, _) =>
      Right(established.copy(
        members = (established.members ++ newMembers).distinct,
        meta = established.meta.map(updateMetaInfo(_, actingMember))
      ))
    case MembersRemovedFromOrganization(_, removedMembers, actingMember, _) =>
      Right(established.copy(
        members = established.members.filterNot(removedMembers.contains),
        meta = established.meta.map(updateMetaInfo(_, actingMember))
      ))
    case OwnersAddedToOrganization(_, newOwners, actingMember, _) =>
      Right(established.copy(
        owners = (established.owners ++ newOwners).distinct,
        meta = established.meta.map(updateMetaInfo(_, actingMember))
      ))
    case OwnersRemovedFromOrganization(_, removedOwners, actingMember, _) =>
      Right(established.copy(
        owners = established.owners.filterNot(removedOwners.contains),
        meta = established.meta.map(updateMetaInfo(_, actingMember))
      ))
    case OrganizationContactsUpdated(_, newContacts, actingMember, _) =>
      Right(established.copy(
        contacts = (established.contacts ++ newContacts).distinct,
        meta = established.meta.map(updateMetaInfo(_, actingMember))
      ))
    case OrganizationSuspended(_, actingMember, _) =>
      Right(established.copy(
        meta = established.meta.map(updateMetaInfo(_, actingMember, Some(OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED)))
      ))
    case OrganizationTerminated(orgId, actingMember, _) => //TODO string for reason terminated? I remember Alex mentioning something about that
      Right(TerminatedOrganizationState(
        orgId = orgId,
        lastMeta = established.meta.map(updateMetaInfo(_, actingMember, Some(OrganizationStatus.ORGANIZATION_STATUS_TERMINATED)))
      ))
    case _: OrganizationEstablished => Left("Organization already established")
    case other => Left(s"Event $other not supported in state active")
  }

  private def suspendedEventHandler(suspended: EstablishedOrganizationState, event: OrganizationEvent): Either[String, OrganizationState] = event match {
    case OrganizationActivated(_, actingMember, _) =>
      Right(suspended.copy(
        meta = suspended.meta.map(updateMetaInfo(_, actingMember, Some(OrganizationStatus.ORGANIZATION_STATUS_ACTIVE)))
      ))
    case OrganizationSuspended(_, actingMember, _) =>
      Right(suspended.copy(meta = suspended.meta.map(updateMetaInfo(_, actingMember))))
    case OrganizationTerminated(orgId, actingMember, _) =>
      Right(TerminatedOrganizationState(orgId = orgId, lastMeta = suspended.meta.map(updateMetaInfo(_, actingMember, Some(OrganizationStatus.ORGANIZATION_STATUS_TERMINATED)))))
    case _: OrganizationEstablished => Left("Organization already established")
    case other => Left(s"Event $other not supported in state suspended")
  }

  private def terminatedEventHandler(event: OrganizationEvent): Either[String, OrganizationState] = event match {
    //TODO allow duplicate terminations?
    case _ => Left("No events allowed in state terminated")
  }


  private val eventHandler: (OrganizationState, OrganizationEvent) => OrganizationState = { (state, event) =>
    val stateOrError = state match {
      case empty: InitialEmptyOrganizationState => emptyInitialEventHandler(empty, event)
      case draft: DraftOrganizationState => draftEventHandler(draft, event)
      case established: EstablishedOrganizationState if !established.meta.map(_.currentStatus).contains(OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED) => activeEventHandler(established, event)
      case suspended: EstablishedOrganizationState => suspendedEventHandler(suspended, event)
      case _: TerminatedOrganizationState => terminatedEventHandler(event)
      case OrganizationState.Empty => throw new RuntimeException("This state should not be reachable")
    }

    stateOrError match {
      case Left(error) => throw new RuntimeException(s"Error handling event $event in state $state: $error")
      case Right(newState) => newState
    }

  }


}
