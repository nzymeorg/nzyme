import RESTClient from "../util/RESTClient";

class BriefingService {

  getBriefing(type, organizationId, tenantId, taps, since, timeRange, setBriefing, errorCallback = undefined) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/briefing/" + type.toLowerCase(), {
      organization_id: organizationId,
      tenant_id: tenantId,
      taps: tapsList,
      since: since,
      time_range: since === "TIME_RANGE" ? timeRange : null
    }, (response) => setBriefing(response.data), errorCallback)
  }

  markSeen(type, successCallback) {
    RESTClient.put("/briefing/" + type.toLowerCase() + "/seen", {}, successCallback)
  }

}

export default BriefingService;
