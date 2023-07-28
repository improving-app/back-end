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
      numMembers: Int,
      creatingMembersForTenant: Map[Some[MemberId], Some[TenantId]],
      establishOrganization: EstablishOrganization
  ): Seq[RegisterMember] =
    (creatingMembersForTenant.keys ++ Random
      .shuffle(
        ((creatingMembersForTenant.size - 1) to numMembers).map(_ => Some(MemberId(UUID.randomUUID().toString)))
      ))
      .zip(
        (creatingMembersForTenant.keys ++ Random
          .shuffle(
            ((creatingMembersForTenant.size - 1) to numMembers)
              .map(_ => creatingMembersForTenant.keys.toSeq(Random.nextInt(creatingMembersForTenant.size)))
          ))
          .zip(
            creatingMembersForTenant.values ++ Random
              .shuffle(
                ((creatingMembersForTenant.size - 1) to numMembers)
                  .map(_ => creatingMembersForTenant.values.toSeq(Random.nextInt(creatingMembersForTenant.size)))
              )
          )
          .toSeq
      )
      .zip(
        Random
          .shuffle(firstNames)
          .take(numMembers)
          .zip(Random.shuffle(lastNames).take(numMembers))
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
      .toSeq

  def genActivateMember(registerMember: RegisterMember): ActivateMember =
    ActivateMember(registerMember.memberId, registerMember.onBehalfOf)
}
