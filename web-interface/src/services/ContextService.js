import RESTClient from '../util/RESTClient'

class ContextService {

  findMacAddressContext(macAddress, organizationId, tenantId, setContext) {
    RESTClient.get("/context/mac/show/" + macAddress, { organization_id: organizationId, tenant_id: tenantId }, (response) => setContext(response.data))
  }

  findMacAddressContextByUuid(uuid, organizationId, tenantId, setContext) {
    RESTClient.get("/context/mac/organization/show/" + organizationId + "/tenant/show/" + tenantId + "/uuid/" + uuid,
        {}, (response) => setContext(response.data))
  }

  findAllMacAddressContext(organizationId, tenantId, addressFilter, setContext, limit, offset) {
    RESTClient.get(
        "/context/mac/organization/show/" + organizationId + "/tenant/show/" + tenantId,
        {address_filter: addressFilter, limit: limit, offset: offset},
        (response) => setContext(response.data)
    )
  }

  createMacAddressContext(macAddress, name, description, notes, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.post(
        "/context/mac",
        {
          mac_address: macAddress,
          name: name,
          description: description,
          notes: notes,
          organization_id: organizationId,
          tenant_id: tenantId
        },
        successCallback, errorCallback
    );
  }

  editMacAddressContext(uuid, name, description, notes, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put("/context/mac/organization/show/" + organizationId + "/tenant/show/" + tenantId + "/uuid/" + uuid,
        {
          name: name,
          description: description,
          notes: notes
        },
        successCallback, errorCallback
    )
  }

  deleteMacAddressContext(uuid, organizationId, tenantId, successCallback) {
    RESTClient.delete("/context/mac/organization/show/" + organizationId + "/tenant/show/" + tenantId + "/uuid/" + uuid,
        successCallback)
  }

  setMacAddressName(mac, name, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.put("/context/mac/organization/show/" + organizationId + "/tenant/show/" + tenantId + "/mac/" + mac + "/name",
      {name: name},
      successCallback, errorCallback
    )
  }

  findIpAddressContext(ipAddress, organizationId, tenantId, setContext) {
    RESTClient.get("/context/ip/show/" + ipAddress, { organization_id: organizationId, tenant_id: tenantId }, (response) => setContext(response.data))
  }

}

export default ContextService;