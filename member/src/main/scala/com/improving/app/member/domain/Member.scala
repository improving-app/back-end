package com.improving.app.member.domain

import com.improving.app.member.api
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityContext

import java.time.Instant

//import cats.Applicative
import cats.data._
import cats.data.Validated._
import cats.implicits._
import com.improving.app.organization.api.OrganizationId

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class Member(context: EventSourcedEntityContext) extends AbstractMember {

  override def emptyState: MemberState = MemberState.defaultInstance

  override def registerMember(
      currentState: MemberState,
      registerMember: api.RegisterMember
  ): EventSourcedEntity.Effect[api.MemberRegistered] =
    if (currentState == emptyState) {
      val event = Member
        .validateMemberData(registerMember.memberMap, registerMember.registeringMember)
        .map(rm => {

          val meta = api.MemberMetaInfo(
            createdOn = Instant.now().toEpochMilli,
            createdBy = rm._2,
            memberState = api.MemberState.Active
          )
          for {
            memToAdd <- rm._1
          } yield api.MemberRegistered(memToAdd.memberId, memToAdd.memberInfo, Some(meta))

        })
        .toEither
      event match {
        case Left(errs) => effects.error(errs.toList.mkString(", "))
        case Right(evt) => effects.emitEvent(evt.get).thenReply(_ => evt.get)
      }
    } else {
      effects.error(
        s"Member ${registerMember.memberMap.map(x => x.memberId).getOrElse("missing member Id")} has already been registered"
      )
    }

  override def memberRegistered(
      currentState: MemberState,
      memberRegistered: api.MemberRegistered
  ): MemberState =
    currentState.copy(
      memberId = memberRegistered.memberId,
      memberInfo = memberRegistered.memberInfo,
      memberMetaInfo = memberRegistered.memberMetaInfo
    )

  def updateMemberState[T <: AnyRef](
      currentState: MemberState,
      affectedMember: Option[api.MemberId],
      actingMember: Option[api.MemberId],
      newState: api.MemberState,
      baseError: String,
      f: (Option[api.MemberId], Option[api.MemberMetaInfo]) => T
  ): EventSourcedEntity.Effect[T] =
    if (currentState == emptyState)
      effects.error(baseError)
    else {
      Member
        .validateActingMember(actingMember)
        .andThen(am => {
          val now = Instant.now().toEpochMilli
          val meta = currentState.memberMetaInfo.get.copy(
            lastModifiedOn = now,
            lastModifiedBy = am,
            memberState = newState
          ) // Is it possible to not have a memberMetaInfo? ie is get safe?
          f(affectedMember, Some(meta)).validNel

        })
        .toEither match {
        case Left(err)  => effects.error(err.toList.mkString(", "))
        case Right(evt) => effects.emitEvent(evt).thenReply(_ => evt)
      }
    }

  override def activateMember(
      currentState: MemberState,
      activateMember: api.ActivateMember
  ): EventSourcedEntity.Effect[api.MemberActivated] = // TODO need rules for who can activate a member and when.  Ie don't want a suspended user to reactivate themselves
    updateMemberState(
      currentState,
      activateMember.memberId,
      activateMember.actingMember,
      api.MemberState.Active,
      s"Member Id:${activateMember.memberId} does not exist - cannot activate!",
      (x, y) => api.MemberActivated(x, y)
    )

  override def memberActivated(
      currentState: MemberState,
      memberActivated: api.MemberActivated
  ): MemberState = currentState.copy(memberMetaInfo = memberActivated.memberMeta)

  override def inactivateMember(
      currentState: MemberState,
      inactivateMember: api.InactivateMember
  ): EventSourcedEntity.Effect[api.MemberInactivated] =
    updateMemberState(
      currentState,
      inactivateMember.memberId,
      inactivateMember.actingMember,
      api.MemberState.Inactive,
      s"Member Id:${inactivateMember.memberId} does not exist - cannot activate!",
      (x, y) => api.MemberInactivated(x, y)
    )

  override def memberInactivated(
      currentState: MemberState,
      memberInactivated: api.MemberInactivated
  ): MemberState = currentState.copy(memberMetaInfo = memberInactivated.memberMeta)

  override def suspendMember(
      currentState: MemberState,
      suspendMember: api.SuspendMember
  ): EventSourcedEntity.Effect[
    api.MemberSuspended
  ] = // TODO need rules on who can suspend a member and when
    updateMemberState(
      currentState,
      suspendMember.memberId,
      suspendMember.actingMember,
      api.MemberState.Suspended,
      s"Member Id:${suspendMember.memberId} does not exist - cannot activate!",
      (x, y) => api.MemberSuspended(x, y)
    )

  override def memberSuspended(
      currentState: MemberState,
      memberSuspended: api.MemberSuspended
  ): MemberState = currentState.copy(memberMetaInfo = memberSuspended.memberMeta)

   override def terminateMember(
      currentState: MemberState,
      terminateMember: api.TerminateMember
  ): EventSourcedEntity.Effect[
    api.MemberTerminated
  ] = // TODO need rules on who can terminate a member and when.  Also Member termination may be long running as there may be much deletion that needs to happen - so may not get a response in time - may need to be asynchronous
    updateMemberState(
      currentState,
      terminateMember.memberId,
      terminateMember.actingMember,
      api.MemberState.Terminated,
      s"Member Id:${terminateMember.memberId} does not exist - cannot activate!",
      (x, y) => api.MemberTerminated(x, y)
    )

  override def memberTerminated(
      currentState: MemberState,
      memberTerminated: api.MemberTerminated
  ): MemberState = currentState.copy(memberMetaInfo = memberTerminated.memberMeta)

  override def updateMemberInfo(
    currentState: MemberState, 
    updateMember: api.UpdateMemberInfo):EventSourcedEntity.Effect[api.MemberInfoUpdated] = if (currentState == emptyState){
      effects.error(s"No Member to update - Member does not exist")
    }else {
      Member.validateMemberData(updateMember.memberMap, updateMember.actingMember).andThen{ mM => {
        val newMemberInfo = mM._1.get.memberInfo.get
        api.MemberInfoUpdated(
          mM._1.get.memberId,
          Some(currentState.memberInfo.get.copy(
            handle = newMemberInfo.handle,
            avatarUrl = newMemberInfo.avatarUrl,
            firstName = newMemberInfo.firstName,
            lastName = newMemberInfo.lastName,
            mobileNumber = newMemberInfo.mobileNumber,
            email = newMemberInfo.email,
            notificationPreference = newMemberInfo.notificationPreference,
            notificationOptIn = newMemberInfo.notificationOptIn,
            organizations = newMemberInfo.organizations,
            relatedMembers = newMemberInfo.relatedMembers,
            memberTypes = newMemberInfo.memberTypes
          )),
          Some(currentState.memberMetaInfo.get.copy(lastModifiedBy = updateMember.actingMember, lastModifiedOn = Instant.now().toEpochMilli))
        ).validNel
      }}.toEither match {
          case Left(err) => effects.error(err.toList.mkString(", "))
          case Right(evt) => effects.emitEvent(evt).thenReply(_ => evt)
        }
        
      
    }
  

    override def memberInfoUpdated(
      currentState: MemberState,
      memberUpdated:api.MemberInfoUpdated
    ):MemberState = currentState.copy(
      memberInfo = memberUpdated.memberInfo,
      memberMetaInfo = memberUpdated.memberMetaInfo
    )

  override def getMemberInfo(
    currentState:MemberState,
    retrieveMember:api.GetMemberInfo):EventSourcedEntity.Effect[api.MemberData] = {
      effects.reply(api.MemberData(currentState.memberId, currentState.memberInfo, currentState.memberMetaInfo))
    }



}
object Member {

  type Error = String

  type ErrorOr[A] = ValidatedNel[Error, A]
  def validateActingMember(memberId: Option[api.MemberId]): ErrorOr[Option[api.MemberId]] = {
    Either
      .cond(memberId.isDefined, memberId, "Invalid Registering Member")
      .toValidatedNel // TODO what are valid cases for registering member - can only be active? or cannot be terminated/suspended - but must exist either way? do we need to do a lookup for it?
  }

  def validateMemberData(memberMap:Option[api.MemberMap], actingMember:Option[api.MemberId]): ErrorOr[(Option[api.MemberMap],Option[api.MemberId])] = {
    (
      validateMemberMap(memberMap),
      validateActingMember(actingMember)
    ).mapN((_:Option[api.MemberMap],_:Option[api.MemberId]))
  }

  def validateMemberMap(
      memberMap: Option[api.MemberMap]
  ): ErrorOr[Option[api.MemberMap]] = {
    validateMemberMapPresence(memberMap).andThen(a => {
      (validateMemberId(a.memberId), validateMemberInfo(a.memberInfo))
        .mapN(api.MemberMap(_: Option[api.MemberId], _: Option[api.MemberInfo]))
        .map(Option(_))
    })

  }

  def validateMemberMapPresence(
      memberMap: Option[api.MemberMap]
  ): ErrorOr[api.MemberMap] = {
    Either.cond(memberMap.isDefined, memberMap.get, "Member To Add is Empty").toValidatedNel
  }

  def validateMemberId(memberId: Option[api.MemberId]): ErrorOr[Option[api.MemberId]] = {
    Either
      .cond(memberId.isDefined, memberId, "Missing MemberId in MemberToAdd")
      .toValidatedNel // MemberId is really just present or absent, no other validation makes sense
  }

  def validateGenericString(s: String): ErrorOr[String] =
    Either.cond(true, s, s"unexpected error on $s").toValidatedNel

  def validateNonEmptyString(s: String, fieldName: String): ErrorOr[String] =
    Either.cond(!s.isEmpty(), s, s"$fieldName is empty").toValidatedNel

  def validateMobileNumber(n: Option[String]): ErrorOr[Option[String]] = Either
    .cond(n.isEmpty || n.isDefined, n, "Invalid Mobile Number")
    .toValidatedNel // TODO add an actual mobile number validation

  def validateEmail(e: Option[String]): ErrorOr[Option[String]] = Either
    .cond(e.isEmpty || (e.isDefined && e.get.contains("@")), e, "Invalid Email Address")
    .toValidatedNel // TODO add an actual email validation

  def validateOrganizations(orgs: Seq[OrganizationId]): ErrorOr[Seq[OrganizationId]] =
    orgs.map(validateOrganization(_)).sequence

  def validateOrganization(org: OrganizationId): ErrorOr[OrganizationId] = {
    Either.cond(true, org, "bad organization ID - does not exist").toValidatedNel
  }

  def validateHasOneOfEmailPhone(memberInfo: api.MemberInfo): ErrorOr[api.MemberInfo] = {
    Either
      .cond(
        memberInfo.mobileNumber.isDefined || memberInfo.email.isDefined,
        memberInfo,
        "Must have at least on of Email, Mobile Phone Number"
      )
      .toValidatedNel
      .andThen(mi =>
        Either
          .cond(
            (mi.mobileNumber.isDefined && mi.notificationPreference == api.NotificationPreference.SMS) || (mi.email.isDefined && mi.notificationPreference == api.NotificationPreference.Email),
            mi,
            "Notification Preference must match included data (ie SMS pref without phone number, or opposite)"
          )
          .toValidatedNel
      )
  }

  def validateMemberInfo(memberInfo: Option[api.MemberInfo]): ErrorOr[Option[api.MemberInfo]] = {
    if (memberInfo.isDefined) {
      validateHasOneOfEmailPhone(memberInfo.get).andThen { x =>
        (
          validateHandle(x.handle, x.organizations),
          valid(x.avatarUrl),
          validateNonEmptyString(x.firstName, "firstName"),
          validateNonEmptyString(x.lastName, "lastName"),
          validateMobileNumber(x.mobileNumber),
          validateEmail(x.email),
          valid(x.notificationPreference),
          valid(x.notificationOptIn),
          validateOrganizations(x.organizations),
          validateGenericString(x.relatedMembers),
          valid(x.memberTypes.toSet.toSeq),// remove any duplicates - this may not be the right place for this code.
          valid(x.unknownFields)
        ).mapN(
          api.MemberInfo(
            _: String,
            _: String,
            _: String,
            _: String,
            _: Option[String],
            _: Option[String],
            _: api.NotificationPreference,
            _: Boolean,
            _: Seq[OrganizationId],
            _: String,
            _: Seq[api.MemberType],
            _: scalapb.UnknownFieldSet
          )
        ).map(Option(_))
      }

    } else invalidNel("MemberInfo is None - cannot validate")

  }

  def validateHandle(handle: String, organizations: Seq[OrganizationId]): ErrorOr[String] =
    Either
      .cond(!handle.isEmpty(), handle, "Handle is empty")
      .toValidatedNel // TODO check that the handle is unique across the organizations

}
