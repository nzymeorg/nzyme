import React, {useState} from 'react';
import {Presets} from "../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import BluetoothDevicesTable from "./BluetoothDevicesTable";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeSelector";

export default function BluetoothDevicesPage() {

  usePageTitle("Bluetooth Devices");

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24));

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <h1>Bluetooth Devices</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Devices"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}/>

                <BluetoothDevicesTable timeRange={timeRange} />
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
)

}