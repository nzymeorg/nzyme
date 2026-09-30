import RESTClient from "../../util/RESTClient";

export default class IPAddressesService {

  findAddress(address, organizationId, tenantId, setAddress) {
    RESTClient.get(`/ethernet/ips/show/${address}`, { organization_id: organizationId, tenant_id: tenantId },
      (response) => setAddress(response.data)
    )
  }

}