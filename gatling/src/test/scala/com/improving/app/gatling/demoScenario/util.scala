package com.improving.app.gatling.demoScenario

import com.improving.app.common.domain.{
  EditableAddress,
  EditableContact,
  MemberId,
  OrganizationId,
  PostalCodeMessageImpl,
  TenantId,
  UsPostalCodeImpl
}
import com.improving.app.gateway.domain.tenant.{
  ActivateTenant,
  EditableTenantInfo,
  EstablishTenant,
  TenantOrganizationList
}
import com.improving.app.gatling.common.util.{genPhoneNumber, genPostalCode}

import java.time.LocalDateTime
import java.util.UUID
import scala.io.Source
import scala.util.Random

object util {

  private val firstNamesFile = "firstNames.txt"
  private val lastNamesFile = "lastNames.txt"
  private val cityStatesFile = "fakariaCityStates.txt"
  private val addressesFile = "addresses.txt"

  private val firstNames: Seq[String] = Source.fromResource(firstNamesFile).getLines().toSeq

  private val lastNames: Seq[String] = Source.fromResource(lastNamesFile).getLines().toSeq

  private val addresses: Seq[String] = Source.fromResource(addressesFile).getLines().toSeq

  private val cityStates: Seq[Seq[String]] =
    Source.fromResource(cityStatesFile).getLines().toSeq.map { _.split(",").toSeq }

  def genEstablishTenantReqs(
      creatingMembersForTenant: Seq[(Some[MemberId], Some[TenantId])],
      numTenants: Int,
      orgId: OrganizationId
  ): Seq[EstablishTenant] = firstNames
    .zip(Random.shuffle(lastNames))
    .zip(Random.shuffle(addresses))
    .zip(Random.shuffle(cityStates))
    .take(numTenants)
    .zip(creatingMembersForTenant.flatMap(_._1))
    .flatMap {
      case ((((first: String, last: String), address: String), Seq(city: String, state: String)), member: MemberId) =>
        Some(
          EstablishTenant(
            Some(TenantId(UUID.randomUUID().toString)),
            Some(member),
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
                Some(TenantOrganizationList(Seq(orgId)))
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
