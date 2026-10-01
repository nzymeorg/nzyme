import RESTClient from "../../util/RESTClient";

export default class IPAddressesService {

  findAddress(address, organizationId, tenantId, limit, offset, setAddress) {
    RESTClient.get(`/ethernet/ips/show/${address}`, { organization_id: organizationId, tenant_id: tenantId, limit: limit, offset: offset },
      (response) => setAddress(response.data)
    )
  }

}