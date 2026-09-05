import React, {useContext, useEffect, useState} from "react";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import {TapContext} from "../../../App";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import BluetoothService from "../../../services/BluetoothService";

const bluetoothService = new BluetoothService();

export default function BluetoothDeviceManufacturersHistogram({timeRange, filters, revision}) {

  const tapContext = useContext(TapContext);
  const [organizationId, tenantId] = useSelectedTenant();

  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    bluetoothService.getDeviceManufacturersHistogram(setHistogram, timeRange, limit, 0, filters, selectedTaps);
  }, [selectedTaps, limit, timeRange, filters, revision]);

  if (!histogram) {
    return <LoadingSpinner />
  }

  if (histogram.total === 0) {
    return (
      <div className="alert alert-info mb-0 mt-2">
        No devices with manufacturer data recorded.
      </div>
    )
  }

  return <TwoColumnHistogram data={histogram}
                             columnTitles={["Manufacturer", "Device Count"]}
                             limit={limit}
                             setLimit={setLimit} />

}