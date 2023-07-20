package com.improving.app.gateway.domain.common

import com.github.dockerjava.api.exception.InternalServerErrorException
import com.improving.app.common.domain._
import com.improving.app.gateway.domain.demoScenario.StartScenario
import com.improving.app.gateway.domain.tenant.{EditableTenantInfo, EstablishTenant, TenantOrganizationList}
import com.typesafe.config.ConfigFactory

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

object util {

  def genPhoneNumber: String =
    s"(${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)})-${Random.nextInt(10)}${Random.nextInt(10)}${Random
        .nextInt(10)}-${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}"

  def genPostalCode: String = Random.alphanumeric.toString().take(5)

  def getHostAndPortForService(serviceName: String): (String, Int) = {
    val config = ConfigFactory
      .load("application.conf")
      .withFallback(ConfigFactory.defaultApplication())
    (
      config.getString(s"services.$serviceName.host"),
      config.getInt(s"services.$serviceName.port")
    )
  }

  def genEstablishTenantReqs(
      firstNames: Seq[String],
      lastNames: Seq[String],
      addresses: Seq[String],
      cityStates: Seq[Seq[String]],
      creatingMembersForTenant: Seq[(Some[MemberId], Some[TenantId])],
      parsed: StartScenario,
      orgId: OrganizationId
  ): Seq[Option[EstablishTenant]] =
    Random
      .shuffle(firstNames)
      .zip(Random.shuffle(lastNames))
      .zip(Random.shuffle(addresses))
      .zip(Random.shuffle(cityStates))
      .zip(creatingMembersForTenant.map(_._1))
      .take(parsed.numTenants)
      .map {
        case ((((first, last), address), Seq(city, state)), member) =>
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
                  Some(TenantOrganizationList(Seq(orgId)))
                )
              )
            )
          )

        case (((_, _), _), _) =>
          None
      }
}
