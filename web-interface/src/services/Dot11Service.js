import RESTClient from '../util/RESTClient'

class Dot11Service {

  findAllSSIDNames(setSSIDs, organizationId, tenantId) {
    RESTClient.get("/dot11/networks/ssids/names", { organization_id: organizationId, tenant_id: tenantId }, function (response) {
      setSSIDs(response.data)
    })
  }

  findBSSID(bssid, taps, successCallback, errorCallback = undefined) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid, { taps: tapsList },
        successCallback, errorCallback)
  }

  getBSSIDAdvertisementHistogram(bssid, timeRange, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid + "/advertisements/histogram",
        { time_range: timeRange, taps: tapsList }, function (response) {
          setHistogram(response.data);
        })
  }

  getBSSIDActiveChannelHistogram(bssid, timeRange, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid + "/frequencies/histogram",
        { time_range: timeRange, taps: tapsList }, function (response) {
          setHistogram(response.data);
        })
  }

  getBSSIDSignalWaterfall(bssid, frequency, timeRange, taps, setWaterfall) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid + "/signal/waterfall",
        { frequency: frequency, time_range: timeRange, taps: tapsList }, function (response) {
          setWaterfall(response.data);
        })
  }

  findAllBSSIDs(timeRange, filters, orderColumn, orderDirection, limit, offset, taps, setBSSIDs) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids", { time_range: timeRange, limit: limit, offset: offset, order_column: orderColumn, order_direction: orderDirection, filters: filters, taps: tapsList },
        function (response) {
          setBSSIDs(response.data)
    })
  }

  findSSIDsOfBSSID(bssid, timeRange, taps, successCallback) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid + "/ssids", { time_range: timeRange, taps: tapsList },
        function (response) {
          successCallback(response.data.ssids);
    })
  }

  getBSSIDAndSSIDHistogram(timeRange, filters, taps, setBSSIDAndSSIDHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/histogram", { time_range: timeRange, filters: filters, taps: tapsList },
        function (response) {
          setBSSIDAndSSIDHistogram(response.data)
    })
  }

  findSSIDOfBSSID(bssid, ssid, timeRange, taps, successCallback, errorCallback = undefined) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid + "/ssids/show/" + encodeURIComponent(ssid),
        { time_range: timeRange, taps: tapsList }, successCallback, errorCallback)
  }

  getSSIDOfBSSIDAdvertisementHistogram(bssid, ssid, timeRange, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid + "/ssids/show/" + encodeURIComponent(ssid) + "/advertisements/histogram",
        { time_range: timeRange, taps: tapsList }, function (response) {
          setHistogram(response.data);
    })
  }

  getSSIDOfBSSIDActiveChannelHistogram(bssid, ssid, timeRange, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid + "/ssids/show/" + encodeURIComponent(ssid) + "/frequencies/histogram",
        { time_range: timeRange, taps: tapsList }, function (response) {
          setHistogram(response.data);
    })
  }

  getSSIDOfBSSIDSignalWaterfall(bssid, ssid, frequency, timeRange, taps, setWaterfall) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/networks/bssids/show/" + bssid + "/ssids/show/" + encodeURIComponent(ssid) + "/frequencies/show/" + frequency + "/signal/waterfall",
        { time_range: timeRange, taps: tapsList }, function (response) {
          setWaterfall(response.data);
    })
  }

  findConnectedClients(timeRange, filters, orderColumn, orderDirection, taps, setClients, limit, offset) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/clients/connected",
        { time_range: timeRange, taps: tapsList, filters: filters, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        function (response) {
          setClients(response.data);
        })
  }

  findDisconnectedClients(timeRange, filters, orderColumn, orderDirection, skipRandomized, taps, setClients, limit, offset) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/clients/disconnected",
        { skip_randomized: skipRandomized, time_range: timeRange, filters: filters, taps: tapsList, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        function (response) {
          setClients(response.data);
        })
  }

  getConnectedClientsHistogram(timeRange, filters, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/clients/connected/histogram", { time_range: timeRange, filters: filters, taps: tapsList },
        function (response) { setHistogram(response.data); })
  }

  getDisconnectedClientsHistogram(timeRange, filters, skipRandomized, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/clients/disconnected/histogram",
        { skip_randomized: skipRandomized, time_range: timeRange, filters: filters, taps: tapsList },
        function (response) { setHistogram(response.data); })
  }

  findMergedConnectedOrDisconnectedClient(clientMac, taps, setClient) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/dot11/clients/show/" + clientMac, { taps: tapsList }, function (response) {
      setClient(response.data);
    })
  }

  getClientFrameCountHistogram(clientMac, timeRange, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/dot11/clients/show/${clientMac}/histogram/frames`,
        { taps: tapsList, time_range: timeRange },
        (response) => {
          setHistogram(response.data);
        })
  }

  getClientConnectedSignalStrengthHistogram(clientMac, timeRange, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/dot11/clients/show/${clientMac}/histogram/signal/connected`,
        { taps: tapsList, time_range: timeRange },
        (response) => {
      setHistogram(response.data);
    })
  }

  getClientDisconnectedSignalStrengthHistogram(clientMac, timeRange, taps, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/dot11/clients/show/${clientMac}/histogram/signal/disconnected`,
        { taps: tapsList, time_range: timeRange },
        (response) => {
          setHistogram(response.data);
        })
  }

  findAllMonitoredSSIDs(organizationId, tenantId, setMonitoredSSIDs) {
    RESTClient.get("/dot11/monitoring/ssids", { organization_id: organizationId, tenant_id: tenantId}, function (response) {
      setMonitoredSSIDs(response.data.ssids);
    })
  }

  findMonitoredSSID(uuid, setMonitoredSSID, successCallback, errorCallback = undefined) {
    RESTClient.get("/dot11/monitoring/ssids/show/" + uuid, {}, function (response) {
      setMonitoredSSID(response.data);
      successCallback();
    }, errorCallback)
  }

  createMonitoredSSID(ssid, organizationId, tenantId, successCallback, errorCallback) {
    RESTClient.post("/dot11/monitoring/ssids",
        {ssid: ssid, tenant_id: tenantId, organization_id: organizationId}, successCallback, errorCallback);
  }

  deleteMonitoredSSID(uuid, successCallback) {
    RESTClient.delete("/dot11/monitoring/ssids/show/" + uuid, successCallback);
  }

  createMonitoredBSSID(ssidUUID, bssid, successCallback, errorCallback) {
    RESTClient.post("/dot11/monitoring/ssids/show/" + ssidUUID + "/bssids",
        {bssid: bssid}, successCallback, errorCallback);
  }

  deleteMonitoredBSSID(ssidUUID, bssidUUID, successCallback) {
    RESTClient.delete("/dot11/monitoring/ssids/show/" + ssidUUID + "/bssids/show/" + bssidUUID, successCallback);
  }

  deleteAllMonitoredBSSIDs(ssidUUID, successCallback) {
    RESTClient.delete("/dot11/monitoring/ssids/show/" + ssidUUID + "/bssids", successCallback);
  }

  createMonitoredBSSIDFingerprint(ssidUUID, bssidUUID, fingerprint, successCallback, errorCallback) {
    RESTClient.post("/dot11/monitoring/ssids/show/" + ssidUUID + "/bssids/show/" + bssidUUID + "/fingerprints",
        {fingerprint: fingerprint}, successCallback, errorCallback);
  }

  deleteMonitoredBSSIDFingerprint(ssidUUID, bssidUUID, fingerprintUUID, successCallback) {
    RESTClient.delete("/dot11/monitoring/ssids/show/" + ssidUUID + "/bssids/show/" + bssidUUID + "/fingerprints/show/" + fingerprintUUID, successCallback);
  }

  createMonitoredChannel(ssidUUID, frequency, successCallback, errorCallback) {
    RESTClient.post("/dot11/monitoring/ssids/show/" + ssidUUID + "/channels",
        {frequency: frequency}, successCallback, errorCallback);
  }

  deleteMonitoredChannel(ssidUUID, channelUUID, successCallback) {
    RESTClient.delete("/dot11/monitoring/ssids/show/" + ssidUUID + "/channels/show/" + channelUUID, successCallback);
  }

  createMonitoredSecuritySuite(ssidUUID, suite, successCallback, errorCallback) {
    RESTClient.post("/dot11/monitoring/ssids/show/" + ssidUUID + "/securitysuites",
        {suite: suite}, successCallback, errorCallback);
  }

  deleteMonitoredSecuritySuite(ssidUUID, suiteUUID, successCallback) {
    RESTClient.delete("/dot11/monitoring/ssids/show/" + ssidUUID + "/securitysuites/show/" + suiteUUID, successCallback);
  }

  enableMonitoredNetwork(ssidUUID, successCallback, errorCallback) {
    RESTClient.put("/dot11/monitoring/ssids/show/" + ssidUUID + "/enable", {}, successCallback, errorCallback);
  }

  disableMonitoredNetwork(ssidUUID, successCallback, errorCallback) {
    RESTClient.put("/dot11/monitoring/ssids/show/" + ssidUUID + "/disable", {}, successCallback, errorCallback);
  }

  setMonitoredNetworkAlertEnabledStatus(ssidUUID, alert, status, successCallback) {
    RESTClient.put("/dot11/monitoring/ssids/show/" + ssidUUID + "/alertenabledstatus/" + alert + "/set/" + status,
        {}, successCallback);
  }

  getMonitoredNetworkImportData(ssidUUID, setImportData) {
      RESTClient.get("/dot11/monitoring/ssids/show/" + ssidUUID + "/import/data", {}, function (response) {
        setImportData(response.data);
      })
  }

  writeMonitoredNetworkImportData(ssidUUID, bssids, channels, securitySuites, successCallback) {
    RESTClient.post("/dot11/monitoring/ssids/show/" + ssidUUID + "/import/data",
        {bssids: bssids, channels: channels, security_suites: securitySuites}, successCallback);
  }

  findBuiltinBandits(setBandits) {
    RESTClient.get("/dot11/bandits/builtin", {}, function (response) {
      setBandits(response.data);
    })
  }

  findBuiltinBandit(id, setBandit) {
    RESTClient.get("/dot11/bandits/builtin/show/" + id, {}, function (response) {
      setBandit(response.data);
    })
  }

  findCustomBandits(organizationUUID, tenantUUID, limit, offset, setBandits) {
    RESTClient.get("/dot11/bandits/custom",
        {limit: limit, offset: offset, organization_uuid: organizationUUID, tenant_uuid: tenantUUID},
        function (response) {
      setBandits(response.data);
    })
  }

  findCustomBandit(banditId, setBandits) {
    RESTClient.get("/dot11/bandits/custom/show/" + banditId, {}, function (response) {
      setBandits(response.data);
    })
  }

  createCustomBandit(organizationUUID, tenantUUID, name, description, successCallback) {
    RESTClient.post("/dot11/bandits/custom",
        { organization_id: organizationUUID, tenant_id: tenantUUID, name: name, description: description },
        successCallback);
  }

  editCustomBandit(banditUUID, name, description, successCallback) {
    RESTClient.put("/dot11/bandits/custom/show/" + banditUUID,
        { name: name, description: description }, successCallback);
  }

  deleteCustomBandit(banditUUID, successCallback) {
    RESTClient.delete("/dot11/bandits/custom/show/" + banditUUID, successCallback);
  }

  addFingerprintToCustomBandit(banditUUID, fingerprint, successCallback) {
    RESTClient.post("/dot11/bandits/custom/show/" + banditUUID + "/fingerprints",
        {fingerprint: fingerprint}, successCallback);
  }

  deleteFingerprintOfCustomBandit(banditUUID, fingerprint, successCallback) {
    RESTClient.delete("/dot11/bandits/custom/show/" + banditUUID + "/fingerprints/show/" + fingerprint,
        successCallback);
  }

  updateTrackDetectorConfig(bssid, ssid, frequency, tapUUID, frameThreshold, gapThreshold, signalCenterlineJitter, successCallback) {
    RESTClient.put("/dot11/networks/bssids/show/" + bssid + "/ssids/show/" + encodeURIComponent(ssid) + "/frequencies/show/" + frequency + "/signal/trackdetector/configuration",
        {
          tap_id: tapUUID,
          frame_threshold: frameThreshold,
          gap_threshold: gapThreshold,
          signal_centerline_jitter: signalCenterlineJitter
        },
        successCallback);
  }

  getDiscoHistogram(timeRange, filters, taps, bssids, monitoredNetworkId, setHistogram) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)
    const bssidList = bssids ? bssids.join(",") : null
    const monitoredNetworkIdParam = monitoredNetworkId ? monitoredNetworkId : null;

    RESTClient.get("/dot11/disco/histogram",
        { time_range: timeRange, filters: filters, taps: tapsList, bssids: bssidList, monitored_network_id: monitoredNetworkIdParam },
        function (response) {
          setHistogram(response.data)
    })
  }

  findDiscoFrames(organizationId, tenantId, timeRange, filters, taps, bssids, orderColumn, orderDirection, limit, offset, setFrames) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)
    const bssidList = bssids ? bssids.join(",") : null

    RESTClient.get("/dot11/disco/frames",
        { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, taps: tapsList,
          bssids: bssidList, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        function (response) {
          setFrames(response.data)
        })
  }

  getDiscoMacHistogram(histogramType, organizationId, tenantId, timeRange, filters, taps, bssids, orderColumn, orderDirection, limit, offset, setData) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)
    const bssidList = bssids ? bssids.join(",") : null

    RESTClient.get("/dot11/disco/histograms/macs/" + histogramType,
        { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, taps: tapsList,
          bssids: bssidList, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        function (response) {
          setData(response.data)
        })
  }

  getDiscoPairs(organizationId, tenantId, timeRange, filters, taps, bssids, orderColumn, orderDirection, limit, offset, setData) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)
    const bssidList = bssids ? bssids.join(",") : null

    RESTClient.get("/dot11/disco/histograms/pairs",
        { organization_id: organizationId, tenant_id: tenantId, time_range: timeRange, filters: filters, taps: tapsList,
          bssids: bssidList, order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        function (response) {
          setData(response.data)
        })
  }

  getDiscoReasonCodes(timeRange, filters, taps, bssids, orderColumn, orderDirection, limit, offset, setData) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)
    const bssidList = bssids ? bssids.join(",") : null

    RESTClient.get("/dot11/disco/histograms/reason_codes",
        { time_range: timeRange, filters: filters, taps: tapsList, bssids: bssidList,
          order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        function (response) {
          setData(response.data)
        })
  }

  getDiscoFrequencies(timeRange, filters, taps, bssids, orderColumn, orderDirection, limit, offset, setData) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)
    const bssidList = bssids ? bssids.join(",") : null

    RESTClient.get("/dot11/disco/histograms/frequencies",
        { time_range: timeRange, filters: filters, taps: tapsList, bssids: bssidList,
          order_column: orderColumn, order_direction: orderDirection, limit: limit, offset: offset },
        function (response) {
          setData(response.data)
        })
  }

  getDiscoDetectionConfiguration(monitoredNetworkUUID, setConfiguration) {
    RESTClient.get("/dot11/disco/config/detection", {monitored_network_id: monitoredNetworkUUID}, function (response) {
          setConfiguration(response.data);
    })
  }

  setDiscoDetectionConfiguration(methodType, configuration, monitoredNetworkUUID, successCallback) {
    RESTClient.put("/dot11/disco/config/detection",
        {monitored_network_id: monitoredNetworkUUID, method_type: methodType, configuration: configuration}, successCallback);
  }

  simulateDiscoDetectionConfiguration(methodType, configuration, monitoredNetworkUUID, tapId, setAnomalies) {
    RESTClient.post("/dot11/disco/config/detection/simulate",
        {
          monitored_network_id: monitoredNetworkUUID,
          tap_id: tapId,
          method_type: methodType,
          configuration: configuration
        }, function (response) {
      setAnomalies(response.data);
    })
  }

  simulateSimilarSSIDs(monitoredNetworkUUID, threshold, successCallback) {
    RESTClient.get("/dot11/monitoring/ssids/show/" + monitoredNetworkUUID + "/configuration/similarssids/simulate", {threshold: threshold}, successCallback)
  }

  setSimilarSSIDMonitorConfiguration(monitoredNetworkUUID, threshold, successCallback) {
    RESTClient.put("/dot11/monitoring/ssids/show/" + monitoredNetworkUUID + "/configuration/similarssids", {threshold: threshold}, successCallback)
  }

  addRestrictedSSIDSubstring(monitoredNetworkUUID, substring, successCallback) {
    RESTClient.post("/dot11/monitoring/ssids/show/" + monitoredNetworkUUID + "/configuration/restricted-ssid-substrings",
        {substring: substring}, successCallback)
  }

  deleteRestrictedSSIDSubstring(monitoredNetworkUUID, substringUUID, successCallback) {
    RESTClient.delete("/dot11/monitoring/ssids/show/" + monitoredNetworkUUID + "/configuration/restricted-ssid-substrings/show/" + substringUUID,
        successCallback)
  }

  findBSSIDLocation(bssid, locationUuid, floorUuid, timeRange, setResult, setErrorMessage) { // Floor UUID is optional.
    RESTClient.get("/dot11/locations/locate/bssid/" + bssid, { time_range: timeRange, location_uuid: locationUuid, floor_uuid: floorUuid},
        (response) => {
          setErrorMessage(null);
          setResult(response.data)
        }, (error) => {
          if (error.response && error.response.status === 400 && error.response.data.message) {
            setErrorMessage(error.response.data.message);
          } else {
            setErrorMessage("Unexpected error.");
          }
        })
  }

  findClientLocation(mac, locationUuid, floorUuid, timeRange, setResult, setErrorMessage) { // Floor UUID is optional.
    RESTClient.get("/dot11/locations/locate/client/" + mac, { time_range: timeRange, location_uuid: locationUuid, floor_uuid: floorUuid},
        (response) => {
          setErrorMessage(null);
          setResult(response.data)
        }, (error) => {
          if (error.response && error.response.status === 400 && error.response.data.message) {
            setErrorMessage(error.response.data.message);
          } else {
            setErrorMessage("Unexpected error.");
          }
        })
  }

  findAllFloorsOfLocation(locationId, setFloors, limit, offset) {
    RESTClient.get("/dot11/locations/show/" + locationId + "/floors",
        {limit: limit, offset: offset}, (response) => {
          setFloors(response.data);
        })
  }

  findAllMonitoredProbeRequests(organizationUUID, tenantUUID, limit, offset, setProbeRequests) {
    RESTClient.get(
        "/dot11/monitoring/proberequests",
        {limit: limit, offset: offset, organization_uuid: organizationUUID, tenant_uuid: tenantUUID},
        (response) => setProbeRequests(response.data)
    )
  }

  findMonitoredProbeRequest(organizationUUID, tenantUUID, uuid, setProbeRequest) {
    RESTClient.get(
        `/dot11/monitoring/proberequests/show/${uuid}`,
        {organization_uuid: organizationUUID, tenant_uuid: tenantUUID},
        (response) => setProbeRequest(response.data)
    )
  }

  createMonitoredProbeRequest(organizationUUID, tenantUUID, ssid, notes, onSuccess) {
    RESTClient.post(
        "/dot11/monitoring/proberequests",
        {organization_id: organizationUUID, tenant_id: tenantUUID, ssid: ssid, notes: notes},
        onSuccess
    )
  }

  updateMonitoredProbeRequest(organizationUUID, tenantUUID, uuid, ssid, notes, onSuccess) {
    RESTClient.put(
        `/dot11/monitoring/proberequests/show/${uuid}`,
        {organization_id: organizationUUID, tenant_id: tenantUUID, ssid: ssid, notes: notes},
        onSuccess
    )
  }

  deleteMonitoredProbeRequest(uuid, onSuccess) {
    RESTClient.delete(`/dot11/monitoring/proberequests/show/${uuid}`, onSuccess)
  }

  getSSIDMonitoringConfiguration(organizationUUID, tenantUUID, setConfiguration) {
    RESTClient.get(
        `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/configuration`,
        {},
        (response) => setConfiguration(response.data)
    )
  }

  updateSSIDMonitoringConfiguration(newConfig, organizationUUID, tenantUUID, successCallback, errorCallback) {
    RESTClient.put(`/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/configuration`,
        { change: newConfig }, successCallback, errorCallback)
  }

  findAllKnownNetworks(organizationUUID, tenantUUID, orderColumn, orderDirection, limit, offset, setKnownNetworks) {
    RESTClient.get(
        `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}`,
        {limit: limit, offset: offset, order_column: orderColumn, order_direction: orderDirection},
        (response) => setKnownNetworks(response.data)
    )
  }

  findAllKnownNetworksByPattern(organizationUUID, tenantUUID, regex, orderColumn, orderDirection, limit, offset, setKnownNetworks, onError) {
    RESTClient.get(
      `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}`,
      {regex: regex, limit: limit, offset: offset, order_column: orderColumn, order_direction: orderDirection},
      (response) => setKnownNetworks(response.data), onError
    )
  }

  deleteKnownNetwork(uuid, organizationUUID, tenantUUID, onSuccess) {
    RESTClient.delete(
        `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/show/${uuid}`,
        onSuccess
    )
  }

  deleteAllKnownNetworks(organizationUUID, tenantUUID, onSuccess) {
    RESTClient.delete(
        `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}`,
        onSuccess
    )
  }

  approveAllKnownNetworks(organizationUUID, tenantUUID, onSuccess) {
    RESTClient.put(
      `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/approve`,
      {},
      onSuccess
    )
  }

  approveAllKnownNetworksByPattern(organizationUUID, tenantUUID, regex, onSuccess, onError) {
    RESTClient.put(
      `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/approve`,
      {regex: regex},
      onSuccess, onError
    )
  }

  approveKnownNetwork(uuid, organizationUUID, tenantUUID, onSuccess) {
    RESTClient.put(
        `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/show/${uuid}/approve`,
        {},
        onSuccess
    )
  }

  revokeKnownNetwork(uuid, organizationUUID, tenantUUID, onSuccess) {
    RESTClient.put(
        `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/show/${uuid}/revoke`,
        {},
        onSuccess
    )
  }

  ignoreKnownNetwork(uuid, organizationUUID, tenantUUID, onSuccess) {
    RESTClient.put(
        `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/show/${uuid}/ignore`,
        {},
        onSuccess
    )
  }

  unignoreKnownNetwork(uuid, organizationUUID, tenantUUID, onSuccess) {
    RESTClient.put(
        `/dot11/monitoring/networks/organization/${organizationUUID}/tenant/${tenantUUID}/show/${uuid}/unignore`,
        {},
        onSuccess
    )
  }

  getMonitoredClientsConfiguration(ssidUUID, setConfiguration) {
    RESTClient.get(`/dot11/monitoring/ssids/show/${ssidUUID}/configuration/clients`, {},
        (response) => { setConfiguration(response.data); })
  }

  updateMonitoredClientsConfiguration(newConfig, ssidUUID, successCallback, errorCallback) {
    RESTClient.put(`/dot11/monitoring/ssids/show/${ssidUUID}/configuration/clients`,
        { change: newConfig }, successCallback, errorCallback)
  }

  findMonitoredClients(ssidUUID, limit, offset, setClients) {
    RESTClient.get(`/dot11/monitoring/ssids/show/${ssidUUID}/clients`, {limit: limit, offset: offset},
        (response) => { setClients(response.data); })
  }

  approveMonitoredClient(ssidUUID, clientUUID, onSuccess) {
    RESTClient.put(`/dot11/monitoring/ssids/show/${ssidUUID}/clients/show/${clientUUID}/approve`, {}, onSuccess)
  }

  revokedMonitoredClient(ssidUUID, clientUUID, onSuccess) {
    RESTClient.put(`/dot11/monitoring/ssids/show/${ssidUUID}/clients/show/${clientUUID}/revoke`, {}, onSuccess)
  }

  ignoreMonitoredClient(ssidUUID, clientUUID, onSuccess) {
    RESTClient.put(`/dot11/monitoring/ssids/show/${ssidUUID}/clients/show/${clientUUID}/ignore`, {}, onSuccess)
  }

  unignoreMonitoredClient(ssidUUID, clientUUID, onSuccess) {
    RESTClient.put(`/dot11/monitoring/ssids/show/${ssidUUID}/clients/show/${clientUUID}/unignore`, {}, onSuccess)
  }

  deleteMonitoredClient(ssidUUID, clientUUID, onSuccess) {
    RESTClient.delete(`/dot11/monitoring/ssids/show/${ssidUUID}/clients/show/${clientUUID}`, onSuccess)
  }

  deleteAllMonitoredClients(ssidUUID, onSuccess) {
    RESTClient.delete(`/dot11/monitoring/ssids/show/${ssidUUID}/clients`, onSuccess)
  }

}

export default Dot11Service
