package com.improving.app.gatling.demoScenario.gen

import com.improving.app.common.domain.{EditableContact, MemberId, TenantId}
import com.improving.app.gateway.domain.member.{
  ActivateMember,
  EditableMemberInfo,
  NotificationPreference,
  RegisterMember
}
import com.improving.app.gateway.domain.organization.EstablishOrganization
import com.improving.app.gateway.domain.tenant.EstablishTenant
import com.improving.app.gatling.common.gen._

import java.util.UUID
import scala.util.Random

object memberGen {
  def genRegisterMembers(
      numMembersForOrg: Int,
      creatingMemberForTenant: (Option[MemberId], Option[EstablishTenant]),
      establishOrganization: EstablishOrganization
  ): Seq[RegisterMember] = {
    (creatingMemberForTenant._1 +: Random
      .shuffle(
        (1 until numMembersForOrg).map(_ => Some(MemberId(UUID.randomUUID().toString)))
      ))
      .zip(
        (0 until numMembersForOrg)
          .map(_ => creatingMemberForTenant)
      )
      .flatMap { case (id, (creatingMember, tenant)) =>
        val firstName = tenant
          .flatMap(_.tenantInfo.flatMap(_.primaryContact.flatMap(_.firstName)))
          .getOrElse("No First Name Found In Tenant Request")
        val lastName = tenant
          .flatMap(_.tenantInfo.flatMap(_.primaryContact.flatMap(_.lastName)))
          .getOrElse("No Last Name Found In Tenant Request")
        Some(
          RegisterMember(
            id,
            Some(
              EditableMemberInfo(
                handle = Some(s"${firstName.take(1)}.${lastName.take(3)}"),
                avatarUrl = Some(s"${establishOrganization.getOrganizationInfo.shortName}.org"),
                firstName = Some(firstName),
                lastName = Some(lastName),
                notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION),
                contact = tenant.flatMap(_.tenantInfo.flatMap(_.primaryContact)),
                organizationMembership = Seq(establishOrganization.getOrganizationId),
                tenant = tenant.flatMap(_.tenantId)
              )
            ),
            creatingMember
          )
        )
      }
  }

  def genActivateMember(registerMember: RegisterMember): ActivateMember =
    ActivateMember(registerMember.memberId, registerMember.onBehalfOf)
}
