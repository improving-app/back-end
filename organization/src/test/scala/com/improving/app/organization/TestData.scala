package com.improving.app.organization

import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain._

import java.time.Instant

object TestData {

  val testOrgId: OrganizationId = OrganizationId("test-organization-id")
  val testOrgId1: OrganizationId = OrganizationId("test-organization-id1")
  val parentIdTest: OrganizationId = OrganizationId("parent-id-test")
  val newParentId: OrganizationId = OrganizationId("new-parent-id")
  val testTenantId = "test-tenant-id"
  val establishingMemberId = "establishing-member-id"
  val now: Instant = Instant.now()
  val timestamp: Timestamp = Timestamp.of(now.getEpochSecond, now.getNano)
  val newMembers =
    Seq[MemberId](MemberId("new-member1"), MemberId("new-member2"))
  val testInfo = Info(
    Some("name-test"),
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
    Some(true),
    Some("www.test.com"),
    Some("N/A"),
    Some(TenantId(testTenantId))
  )
  val testMembers = Seq[MemberId](
    MemberId("test-member-id"),
    MemberId("member2"),
    MemberId("member3")
  )
  val testNewMembers = Seq[MemberId](
    MemberId("test-member-id2"),
    MemberId("member4"),
    MemberId("member5")
  )
  val testOwners = Seq[MemberId](
    MemberId("member10"),
    MemberId("member11"),
    MemberId("member12")
  )
  val testNewOwners = Seq[MemberId](
    MemberId("member20"),
    MemberId("member21"),
    MemberId("member22")
  )
  val testContacts = Seq[Contacts](
    Contacts(primaryContacts = Seq(MemberId("member81")))
  )
  val testActingMember = MemberId(establishingMemberId)
  val testActingMember2 = MemberId("test-acting-member2")
  val testActingMember3 = MemberId("test-acting-member3")
  val testActingMember4 = MemberId("test-acting-member4")
  val testActingMember5 = MemberId("test-acting-member5")

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
    Some(testNewName),
    Some(testNewShortName),
    Some(testNewAddress),
    Some(true),
    Some(testNewUrl),
    Some(testNewLogo),
    Some(testNewTenantId)
  )
  val testNewPartialTestInfo = Info(
    name = Some(testNewName),
    shortName = None,
    address = None,
    isPrivate = Some(false),
    url = Some(testNewUrl),
    logo = Some(testNewLogo),
    tenant = None
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

  val establishOrganizationRequest: EstablishOrganizationRequest = EstablishOrganizationRequest(
    Some(testInfo),
    Some(parentIdTest),
    testMembers,
    testOwners,
    testContacts,
    Some(testActingMember)
  )

  val activateOrganizationRequest: ActivateOrganizationRequest = ActivateOrganizationRequest(
    Some(testOrgId),
    Some(testActingMember2)
  )

  val suspendOrganizationRequest: SuspendOrganizationRequest = SuspendOrganizationRequest(
    Some(testOrgId),
    Some(testActingMember4)
  )

  val terminateOrganizationRequest: TerminateOrganizationRequest = TerminateOrganizationRequest(
    Some(testOrgId),
    Some(testActingMember5)
  )

  val getOrganizationInfoRequest: GetOrganizationInfoRequest = GetOrganizationInfoRequest(
    Some(testOrgId)
  )

  val getOrganizationByIdRequest: GetOrganizationByIdRequest = GetOrganizationByIdRequest(
    Some(testOrgId)
  )


  val editOrganizationInfoRequest: EditOrganizationInfoRequest = EditOrganizationInfoRequest(
    Some(testOrgId),
    Some(testNewPartialTestInfo),
    Some(testActingMember3)
  )


  val updateParentRequest: UpdateParentRequest = UpdateParentRequest(
    Some(testOrgId),
    Some(testNewParent),
    Some(testActingMember3)
  )

  val addMembersToOrganizationRequest: AddMembersToOrganizationRequest = AddMembersToOrganizationRequest(
    Some(testOrgId),
    testNewMembers :+ scala.util.Random.shuffle(testMembers).head,
    Some(testActingMember3)
  )

  val removeMembersFromOrganizationRequest: RemoveMembersFromOrganizationRequest = RemoveMembersFromOrganizationRequest(
    Some(testOrgId),
    scala.util.Random.shuffle(testMembers).take(2),
    Some(testActingMember3)
  )

  val addOwnersToOrganizationRequest: AddOwnersToOrganizationRequest = AddOwnersToOrganizationRequest(
    Some(testOrgId),
    testNewOwners :+ scala.util.Random.shuffle(testOwners).head,
    Some(testActingMember3)
  )

  val removeOwnersFromOrganizationRequest: RemoveOwnersFromOrganizationRequest = RemoveOwnersFromOrganizationRequest(
    Some(testOrgId),
    scala.util.Random.shuffle(testOwners).take(2),
    Some(testActingMember3)
  )

  val updateOrganizationContactsRequest: UpdateOrganizationContactsRequest = UpdateOrganizationContactsRequest(
    Some(testOrgId),
    testNewContacts :+ scala.util.Random.shuffle(testContacts).head,
    Some(testActingMember3)
  )

  val everyMinimalRequest: Seq[OrganizationRequest] = Seq(UpdateOrganizationContactsRequest(),
    UpdateParentRequest(),
    TerminateOrganizationRequest(),
    SuspendOrganizationRequest(),
    ActivateOrganizationRequest(),
    ReleaseOrganizationRequest(),
    EditOrganizationInfoRequest(),
    RemoveOwnersFromOrganizationRequest(),
    AddOwnersToOrganizationRequest(),
    RemoveMembersFromOrganizationRequest(),
    AddMembersToOrganizationRequest(),
    GetOrganizationInfoRequest(),
    EstablishOrganizationRequest(),
    GetOrganizationByIdRequest(),
    GetOrganizationsByOwnerRequest(),
    GetOrganizationsByMemberRequest(),
    GetRootOrganizationRequest(),
    GetDescendantOrganizationsRequest()
  )
}
