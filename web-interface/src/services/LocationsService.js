import RESTClient from '../util/RESTClient'

class LocationsService {

  findAll(organizationId, tenantId, setLocations) {
    RESTClient.get(`/locations/organizations/${organizationId}/tenants/${tenantId}`, {}, (response) => {
      setLocations(response.data)
    })
  }

  findNoLocation(organizationId, tenantId, setNoLocation) {
    RESTClient.get(`/locations/organizations/${organizationId}/tenants/${tenantId}/none`, {}, (response) => {
      setNoLocation(response.data)
    }, () => setNoLocation({name: "No Location", tap_count: 0, alert_count: 0, taps: []}))
  }

  findOne(locationId, organizationId, tenantId, setLocation, errorCallback = undefined) {
    RESTClient.get(`/locations/organizations/${organizationId}/tenants/${tenantId}/show/${locationId}`, {}, (response) => {
      setLocation(response.data)
    }, errorCallback)
  }

}

export default LocationsService;