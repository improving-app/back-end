package com.improving.app.gatling.demoScenario.gen

import com.improving.app.common.domain.{
  EditableAddress,
  MemberId,
  OrganizationId,
  PostalCodeMessageImpl,
  TenantId,
  UsPostalCodeImpl
}
import com.improving.app.gateway.domain.organization.{
  ActivateOrganization,
  EditableOrganizationInfo,
  EstablishOrganization
}
import com.improving.app.gatling.common.gen._

import scala.util.Random

object organizationGen {

  def genEstablishOrg(
      creatingMemberForTenant: (Some[MemberId], Some[TenantId]),
      orgId: OrganizationId
  ): Option[EstablishOrganization] = (
    Random
      .shuffle(addresses)
      .zip(Random.shuffle(cityStates))
      .head,
    creatingMemberForTenant
  ) match {
    case ((address, Seq(city, state)), (member, tenant)) =>
      Some(
        EstablishOrganization(
          Some(orgId),
          member,
          Some(
            EditableOrganizationInfo(
              name = Some("The Demo Corporation"),
              shortName = Some("DemoCorp"),
              tenant = tenant,
              isPublic = Some(true),
              address = Some(
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
              url = Some("demo.corp"),
              logo = None
            )
          )
        )
      )
    case ((_, _), (_, _)) => None
  }

  def genActivateOrgReqs(establishReqs: EstablishOrganization): ActivateOrganization =
    ActivateOrganization(establishReqs.organizationId, establishReqs.onBehalfOf)

}
