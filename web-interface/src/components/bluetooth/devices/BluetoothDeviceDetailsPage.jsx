import React, {useContext, useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import LoadingSpinner from "../../misc/LoadingSpinner";
import BluetoothService from "../../../services/BluetoothService";
import {TapContext} from "../../../App";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import MacAddressContextLine from "../../shared/context/macs/details/MacAddressContextLine";
import moment from "moment";
import BluetoothMacAddress from "../../shared/context/macs/BluetoothMacAddress";
import GroupedParameterList from "../../shared/GroupedParameterList";
import {transformTag, transformTransport} from "../BluetoothTools";
import TapBasedSignalStrengthTable from "../../shared/TapBasedSignalStrengthTable";
import {BluetoothDeviceSignalStrengthHistogram} from "./BluetoothDeviceSignalStrengthHistogram";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeSelector";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";

const btService = new BluetoothService();

export default function BluetoothDeviceDetailsPage() {

  const {macParam} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [device, setSelectedDevice] = useState(null);
  const [rssiHistogram, setRssiHistogram] = useState(null);
  const [tapRssis, setTapRssis] = useState(null);

  const [rssiHistogramTimerange, setRssiHistogramTimerange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "rssi"));
  const [tapRssiTimerange, setTapRssiTimerange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_MINUTES_15, "tap_rssi"));

  usePageTitle(device ? `Bluetooth Device: ${device.device.mac.address}` : "Bluetooth Device Details");

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    btService.findOneDevice(setSelectedDevice, organizationId, tenantId, macParam, selectedTaps);
    btService.getRssiHistogramOfDevice(setRssiHistogram, macParam, rssiHistogramTimerange, selectedTaps);
    btService.getRssiOfDeviceByTap(setTapRssis, macParam, tapRssiTimerange, selectedTaps);
  }, [macParam, rssiHistogramTimerange, tapRssiTimerange]);

  const deviceTags = () => {
    if (!device.device.tags || device.device.tags.length === 0 || device.device.tags[0] == null) {
      return <div className="alert alert-info mt-2 mb-0">The make or model of this device was not identified because
        nzyme could not determine any specific properties.</div>
    }

    return (
        <React.Fragment>
          This device has been identified as:

          <ul className="mb-0 mt-2">
            {device.device.tags.map((t, i) => {
              return <li key={i}>{transformTag(t)}</li>
            })}
          </ul>
        </React.Fragment>
    )
  }

  if (!device) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">Bluetooth</li>
                <li className="breadcrumb-item"><a href={ApiRoutes.BLUETOOTH.DEVICES.INDEX}>Devices</a></li>
                <li className="breadcrumb-item">{device.device.mac.address}</li>
                <li className="breadcrumb-item active" aria-current="page">Details</li>
              </ol>
            </nav>
          </div>

          <div className="col-12">
            <h1>
              Bluetooth Device &quot;{device.device.mac.address}&quot;
            </h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="row">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Device Information"
                                           fixedAppliedTimeRange={Presets.ALL_TIME}/>

                    <dl className="mb-0">
                      <dt>MAC Address</dt>
                      <dd>
                        <BluetoothMacAddress addressWithContext={device.device.mac}/>
                      </dd>
                      <dt>Transport</dt>
                      <dd>
                        <GroupedParameterList list={device.device.transports}
                                              valueTransform={(t) => transformTransport(t, true)}/>
                      </dd>
                      <dt>Bluetooth Vendor</dt>
                      <dd><GroupedParameterList list={device.device.companies}/></dd>
                      <dt>MAC OUI Vendor</dt>
                      <dd><GroupedParameterList list={device.device.ouis}/></dd>
                      <dt>Name:</dt>
                      <dd>
                        <GroupedParameterList list={device.device.names}/>
                      </dd>
                      <dt>Name &amp; Description (from Context)</dt>
                      <dd>
                        <MacAddressContextLine address={device.device.mac.address} context={device.device.mac.context}/>
                      </dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Signal Strength by Tap"
                                           urlKey="tap_rssi"
                                           timeRange={tapRssiTimerange}
                                           setTimeRange={setTapRssiTimerange} />

                    <TapBasedSignalStrengthTable strengths={tapRssis} />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="row">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Activity" fixedAppliedTimeRange={Presets.ALL_TIME}/>

                    <dl className="mb-0">
                      <dt>First Seen</dt>
                      <dd>
                        {moment(device.device.first_seen).format()}{' '}
                        <span className="text-muted">
                          (Note: This value is affected by data retention times.)
                        </span>
                      </dd>
                      <dt>Last Seen</dt>
                      <dd>{moment(device.device.last_seen).format()}</dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Tags"
                                           fixedAppliedTimeRange={Presets.ALL_TIME}/>

                    {deviceTags()}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Signal Strength"
                                       urlKey="rssi"
                                       timeRange={rssiHistogramTimerange}
                                       setTimeRange={setRssiHistogramTimerange} />

                <BluetoothDeviceSignalStrengthHistogram data={rssiHistogram} setTimeRange={setRssiHistogramTimerange} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}