package com.improving.app.gatling.demoScenario.gen

import com.improving.app.common.domain._
import com.improving.app.gateway.domain.tenant.{
  ActivateTenant,
  EditableTenantInfo,
  EstablishTenant,
  TenantOrganizationList
}
import com.improving.app.gatling.common.gen._

import java.time.LocalDateTime
import java.util.UUID
import scala.util.Random

object tenantGen {
  def genEstablishTenantReqs(
      creatingMembers: Seq[Some[MemberId]],
      numTenants: Int,
      orgId: Map[Some[MemberId], OrganizationId]
  ): Seq[EstablishTenant] = Random
    .shuffle(firstNames)
    .zip(Random.shuffle(lastNames))
    .zip(Random.shuffle(addresses))
    .zip(Random.shuffle(cityStates))
    .take(numTenants)
    .zip(creatingMembers)
    .flatMap {
      case (
            (((first: String, last: String), address: String), Seq(city: String, state: String)),
            member: Option[MemberId]
          ) =>
        Some(
          EstablishTenant(
            Some(TenantId(UUID.randomUUID().toString)),
            member,
            Some(
              EditableTenantInfo(
                Some("Demo-" + LocalDateTime.now().toString),
                Some(
                  EditableContact(
                    Some(first),
                    Some(last),
                    Some(s"$first.$last@orgorg.com"),
                    Some(genPhoneNumber),
                    Some(first.take(1) + last)
                  )
                ),
                Some(
                  EditableAddress(
                    line1 = Some(address),
                    line2 = None,
                    city = Some(city),
                    stateProvince = Some(state),
                    country = Some("Fakaria"),
                    postalCode = Some(
                      PostalCodeMessageImpl(
                        UsPostalCodeImpl(genPostalCode)
                      )
                    )
                  )
                ),
                Some(TenantOrganizationList(Seq(orgId(member))))
              )
            )
          )
        )

      case (((_, _), _), _) =>
        None
    }

  def genActivateTenantReqs(establishReqs: EstablishTenant): ActivateTenant =
    ActivateTenant(establishReqs.tenantId, establishReqs.onBehalfOf)
}
