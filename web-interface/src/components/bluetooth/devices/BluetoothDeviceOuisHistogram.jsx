import React, {useContext, useEffect, useState} from "react";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import {TapContext} from "../../../App";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import BluetoothService from "../../../services/BluetoothService";
import {BLUETOOTH_DEVICES_FILTER_FIELDS} from "../BluetoothDevicesFilterFields";

const bluetoothService = new BluetoothService();

export default function BluetoothDeviceOuisHistogram({timeRange, filters, setFilters, monitorsReady, revision}) {

  const tapContext = useContext(TapContext);

  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [histogram, setHistogram] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setHistogram(null);

    if (monitorsReady) {
      bluetoothService.getDeviceOuisHistogram(
        setHistogram, timeRange, orderColumn, orderDirection, limit, 0, filters, selectedTaps
      );
    }
  }, [selectedTaps, limit, timeRange, filters, monitorsReady, orderColumn, orderDirection, revision]);

  if (!histogram || !monitorsReady) {
    return <LoadingSpinner />
  }

  if (histogram.total === 0) {
    return (
      <div className="alert alert-info mb-0 mt-2">
        No devices with OUI data recorded.
      </div>
    )
  }

  return <TwoColumnHistogram data={histogram}
                             columnTitles={["OUI", "Device Count"]}
                             columnFilterElements={[
                               {field: "ouis", fields: BLUETOOTH_DEVICES_FILTER_FIELDS, setFilters: setFilters},
                               null, null
                             ]}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}