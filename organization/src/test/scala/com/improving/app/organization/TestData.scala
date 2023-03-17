package com.improving.app.organization

import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain._

import java.time.Instant

object TestData {

  val testOrgId: OrganizationId = OrganizationId("test-organization-id")
  val parentIdTest: OrganizationId = OrganizationId("parent-id-test")
  val newParentId: OrganizationId = OrganizationId("new-parent-id")
  val testTenantId = "test-tenant-id"
  val establishingMemberId = "establishing-member-id"
  val now: Instant = Instant.now()
  val timestamp: Timestamp = Timestamp.of(now.getEpochSecond, now.getNano)
  val newMembers =
    Seq[MemberId](MemberId("new-member1"), MemberId("new-member2"))
  val testInfo = Info(
    "name-test",
    Some("shortname-test"),
    Some(
      Address(
        "line1",
        "line2",
        "city",
        "state",
        "canada",
        Some(
          PostalCodeMessageImpl(CaPostalCodeImpl.apply("1234"))
        )
      )
    ),
    true,
    Some("www.test.com"),
    Some("N/A"),
    Some(TenantId(testTenantId))
  )
  val testMembers = Seq[MemberId](
    MemberId("test-member-id"),
    MemberId("member2"),
    MemberId("member3")
  )
  val testOwners = Seq[MemberId](
    MemberId("member10"),
    MemberId("member11"),
    MemberId("member12")
  )
  val testContacts = Seq[Contacts](
    Contacts(primaryContacts = Seq(MemberId("member81")))
  )
  val testActingMember = MemberId(establishingMemberId)
  val testActingMember2 = MemberId("test-acting-member2")
  val testActingMember3 = MemberId("test-acting-member3")
  val testActingMember4 = MemberId("test-acting-member4")

  val testNewName = "test-new-name"
  val testNewShortName = "test-new-short-name"
  val testNewAddress = Address(
    "updated-line1",
    "updated-line2",
    "updated-city",
    "updated-state",
    "usa",
    Some(
      PostalCodeMessageImpl(CaPostalCodeImpl.apply("5678"))
    )
  )
  val testNewUrl = "test-new-url"
  val testNewLogo = "test-new-logo"
  val testNewTenantId = TenantId("test-new-tenant-id")
  val testNewParent = OrganizationId("test-new-parent-id")
  val testNewTestInfo = Info(
    testNewName,
    Some(testNewShortName),
    Some(testNewAddress),
    true,
    Some(testNewUrl),
    Some(testNewLogo),
    Some(testNewTenantId)
  )
  val testNewPartialTestInfo = Info(
    testNewName,
    None,
    None,
    false,
    Some(testNewUrl),
    Some(testNewLogo)
  )
  val testNewContacts = Seq[Contacts](
    Contacts(
      primaryContacts = Seq(MemberId("member81")),
      billingContacts = Seq(MemberId("member82")),
      distributionContacts = Seq(MemberId("member83"))
    )
  )
  val organizationEstablished: OrganizationEstablished =
    OrganizationEstablished(
      Some(testOrgId),
      Some(testInfo),
      Some(parentIdTest),
      testMembers,
      testOwners,
      testContacts,
      Some(testActingMember)
    )
  val establishOrganizationRequest = EstablishOrganizationRequest(
    Some(testInfo),
    Some(parentIdTest),
    testMembers,
    testOwners,
    testContacts,
    Some(testActingMember)
  )
}
