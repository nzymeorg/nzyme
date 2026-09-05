import React, {useState} from 'react';
import {Presets} from "../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import BluetoothDevicesTable from "./BluetoothDevicesTable";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeSelector";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import Filters from "../../shared/filtering/Filters";
import {BLUETOOTH_DEVICES_FILTER_FIELDS} from "../BluetoothDevicesFilterFields";
import {useLocation} from "react-router-dom";
import BluetoothDeviceCountHistogram from "./BluetoothDeviceCountHistogram";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function BluetoothDevicesPage() {

  usePageTitle("Bluetooth Devices");

  const urlQuery = useQuery();

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24));

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), BLUETOOTH_DEVICES_FILTER_FIELDS)
  );

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={BLUETOOTH_DEVICES_FILTER_FIELDS} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Active Devices"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <BluetoothDeviceCountHistogram timeRange={timeRange}
                                               setTimeRange={setTimeRange}
                                               filters={filters}
                                               revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Devices"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <BluetoothDevicesTable timeRange={timeRange}
                                       filters={filters}
                                       setFilters={setFilters}
                                       revision={revision} />
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
)

}