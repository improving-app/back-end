import com.improving.app.common.domain.{Address, CaPostalCodeImpl, PostalCodeMessageImpl, TenantId}
import com.improving.app.organization.domain.OrganizationInfo

object TestData {
  val baseAddress = Address(
    line1 = "line1",
    line2 = "line2",
    city = "city",
    stateProvince = "stateProvince",
    country = "country",
    postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
  )

  val baseOrganizationInfo = OrganizationInfo(
    name = "Organization Name",
    shortName = "OrgName",
    tenant = Some(TenantId("tenant")),
    isPublic = false,
    address = Some(baseAddress),
    url = "",
    logo = "",
  )
}
