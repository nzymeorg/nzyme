import RESTClient from '../util/RESTClient'

class BluetoothService {

  findAllDevices(setDevices, organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, taps, limit, offset) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get("/bluetooth/devices", {
        organization_id: organizationId,
        tenant_id: tenantId,
        filters: filters,
        time_range: timeRange,
        order_column: orderColumn,
        order_direction: orderDirection,
        taps: tapsList,
        limit: limit,
        offset: offset
      },
        (response) => setDevices(response.data)
    )
  }

  findOneDevice(setDevice, organizationId, tenantId, mac, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/bluetooth/devices/show/${mac}`, {
        organization_id: organizationId,
        tenant_id: tenantId,
        taps: tapsList
      },
        (response) => setDevice(response.data)
    )
  }

  getRssiHistogramOfDevice(setData, mac, timeRange, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/bluetooth/devices/show/${mac}/rssi/histogram`, { time_range: timeRange, taps: tapsList },
        (response) => setData(response.data)
    )
  }

  getRssiOfDeviceByTap(setData, mac, timeRange, taps) {
    const tapsList = Array.isArray(taps) ? taps.join(",") : (taps === "*" ? "*" : null)

    RESTClient.get(`/bluetooth/devices/show/${mac}/rssi/bytap`, { time_range: timeRange, taps: tapsList },
        (response) => setData(response.data)
    )
  }

  findAllRules(setRules, limit, offset) {
    RESTClient.get("/bluetooth/monitoring/rules", { limit: limit, offset: offset },
      (response) => setRules(response.data)
    )
  }
}

export default BluetoothService;