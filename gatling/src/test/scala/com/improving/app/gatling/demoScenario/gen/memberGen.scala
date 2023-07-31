package com.improving.app.gatling.demoScenario.gen

import com.improving.app.common.domain.{EditableContact, MemberId, TenantId}
import com.improving.app.gateway.domain.member.{
  ActivateMember,
  EditableMemberInfo,
  NotificationPreference,
  RegisterMember
}
import com.improving.app.gateway.domain.organization.EstablishOrganization
import com.improving.app.gatling.common.gen._

import java.util.UUID
import scala.util.Random

object memberGen {
  def genRegisterMembers(
      numMembersForOrg: Int,
      creatingMemberForTenant: (Some[MemberId], Some[TenantId]),
      establishOrganization: EstablishOrganization
  ): Seq[RegisterMember] = {
    (creatingMemberForTenant._1 +: Random
      .shuffle(
        (1 until numMembersForOrg).map(_ => Some(MemberId(UUID.randomUUID().toString)))
      ))
      .zip(
        creatingMemberForTenant +:
          (1 until numMembersForOrg)
            .map(_ => creatingMemberForTenant)
      )
      .zip(
        Random
          .shuffle(firstNames)
          .take(numMembersForOrg)
          .zip(Random.shuffle(lastNames).take(numMembersForOrg))
      )
      .flatMap { case ((id, (creatingMember, tenant)), (firstName, lastName)) =>
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
                contact = Some(
                  EditableContact(
                    firstName = Some(firstName),
                    lastName = Some(lastName),
                    emailAddress = Some(
                      s"${firstName.toLowerCase}.${lastName.toLowerCase}@${establishOrganization.organizationInfo.get.shortName}.org"
                    ),
                    phone = Some(genPhoneNumber),
                    userName = Some(s"${firstName.take(1)}$lastName")
                  )
                ),
                organizationMembership = Seq(establishOrganization.getOrganizationId),
                tenant = tenant
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
