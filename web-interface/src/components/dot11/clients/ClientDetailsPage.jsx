import React, {useContext, useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import ApiRoutes from "../../../util/ApiRoutes";
import Dot11Service from "../../../services/Dot11Service";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {TapContext} from "../../../App";
import moment from "moment";
import ClientBSSIDHistory from "../util/ClientBSSIDHistory";
import ObservedProbeRequestsList from "./ObservedProbeRequestsList";
import ClientActivityHistogram from "./ClientActivityHistogram";
import DiscoPairsTable from "../disco/DiscoPairsTable";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import Dot11MacAddress from "../../shared/context/macs/Dot11MacAddress";
import MacAddressContextLine from "../../shared/context/macs/details/MacAddressContextLine";
import TapBasedSignalStrengthTable from "../../shared/TapBasedSignalStrengthTable";
import ClientSignalStrengthChart from "./ClientSignalStrengthChart";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import ReadOnlyTrilaterationResultFloorPlanWrapper
  from "../../shared/floorplan/ReadOnlyTrilaterationResultFloorPlanWrapper";
import TransparentIpAddressTable from "../../shared/context/transparent/TransparentIpAddressTable";
import TransparentHostnamesTable from "../../shared/context/transparent/TransparentHostnamesTable";
import {singleTapSelected} from "../../../util/Tools";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeUrl";

const dot11Service = new Dot11Service();

function ClientDetailsPage() {

  const {macParam} = useParams();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [client, setClient] = useState(null);

  const [discoPairsTimeRange, setDiscoPairsTimeRange] = useState(Presets.RELATIVE_HOURS_24);

  const [trilaterationTimeRange, setTrilaterationTimeRange] = useState(Presets.RELATIVE_MINUTES_15)
  const [trilaterationFloor, setTrilaterationFloor] = useState(null);
  const [trilaterationResult, setTrilaterationResult] = useState(null);
  const [trilaterationError, setTrilaterationError] = useState(null);
  const [trilaterationRevision, setTrilaterationRevision] = useState(0);

  const [frameCountHistogramTimeRange, setFrameCountHistogramTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "frame_count"));
  const [frameCountHistogramType, setFrameCountHistogramType] = useState("total_frames")
  const [frameCountHistogram, setFrameCountHistogram] = useState(null);

  const [connectedSignalStrengthHistogramTimeRange, setConnectedSignalStrengthHistogramTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "c_ss"));
  const [connectedSignalStrengthHistogram, setConnectedSignalStrengthHistogram] = useState(null);

  const [disconnectedSignalStrengthHistogramTimeRange, setDisconnectedSignalStrengthHistogramTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "d_ss"));
  const [disconnectedSignalStrengthHistogram, setDisconnectedSignalStrengthHistogram] = useState(null);

  usePageTitle(client ? `WiFi Client: ${client.mac.address}` : "WiFi Client Details");

  useEffect(() => {
    setClient(null);
    dot11Service.findMergedConnectedOrDisconnectedClient(macParam, selectedTaps, setClient);
  }, [selectedTaps]);

  useEffect(() => {
    setTrilaterationResult(null);
    if (trilaterationFloor == null) {
      dot11Service.findClientLocation(
          macParam, null, null, trilaterationTimeRange, setTrilaterationResult, setTrilaterationError
      );
    } else {
      dot11Service.findClientLocation(
          macParam, trilaterationFloor.location, trilaterationFloor.floor, trilaterationTimeRange, setTrilaterationResult, setTrilaterationError
      );
    }
  }, [macParam, selectedTaps, trilaterationFloor, trilaterationRevision, trilaterationTimeRange]);

  useEffect(() => {
    setFrameCountHistogram(null);
    dot11Service.getClientFrameCountHistogram(
        macParam, frameCountHistogramTimeRange, selectedTaps, setFrameCountHistogram
    )
  }, [macParam, selectedTaps, frameCountHistogramTimeRange]);

  useEffect(() => {
    if (singleTapSelected(selectedTaps)) {
      setConnectedSignalStrengthHistogram(null);
      dot11Service.getClientConnectedSignalStrengthHistogram(
          macParam, connectedSignalStrengthHistogramTimeRange, selectedTaps, setConnectedSignalStrengthHistogram
      )
    }
  }, [macParam, selectedTaps, connectedSignalStrengthHistogramTimeRange]);

  useEffect(() => {
    if (singleTapSelected(selectedTaps)) {
      setDisconnectedSignalStrengthHistogram(null);
      dot11Service.getClientDisconnectedSignalStrengthHistogram(
          macParam, disconnectedSignalStrengthHistogramTimeRange, selectedTaps, setDisconnectedSignalStrengthHistogram
      )
    }
  }, [macParam, selectedTaps, disconnectedSignalStrengthHistogramTimeRange]);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  const onFloorSelected = (locationUuid, floorUuid) => {
    setTrilaterationFloor({location: locationUuid, floor: floorUuid});
  }

  const onTrilaterationRefresh = () => {
    setTrilaterationRevision((prevRev => prevRev + 1));
  }

  if (!client) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><a href={ApiRoutes.DOT11.OVERVIEW}>WiFi</a></li>
                <li className="breadcrumb-item"><a href={ApiRoutes.DOT11.CLIENTS.INDEX}>Clients</a></li>
                <li className="breadcrumb-item active" aria-current="page">{client.mac.address}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-2">
            <a className="btn btn-primary float-end" href={ApiRoutes.DOT11.CLIENTS.INDEX}>Back</a>
          </div>

          <div className="col-md-12">
            <h1>
              Client &quot;{client.mac.address} ({client.mac.oui ? client.mac.oui : "Unknown Vendor"})&quot;
            </h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Client Information"
                                       fixedAppliedTimeRange={Presets.ALL_TIME}/>

                <dl className="mb-0">
                  <dt>MAC Address</dt>
                  <dd>
                    <Dot11MacAddress addressWithContext={client.mac} showOui={true}/>
                  </dd>
                  <dt>Last Connected to BSSID</dt>
                  <dd>
                    {client.connected_bssid ? <Dot11MacAddress addressWithContext={client.connected_bssid.mac}
                                                               href={ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(client.connected_bssid.mac.address)}
                                                               showOui={true}/> : "None"}{' '}
                  </dd>
                  <dt>Name &amp; Description (from Context)</dt>
                  <dd>
                    <MacAddressContextLine address={client.mac.address} context={client.mac.context}/>
                  </dd>
                </dl>
              </div>
            </div>
          </div>
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Activity"
                                       fixedAppliedTimeRange={Presets.ALL_TIME}/>

                <dl className="mb-0">
                  <dt>First Seen</dt>
                  <dd>
                    {moment(client.first_seen).format()}{' '}
                    <span className="text-muted">(Note: This value is affected by data retention times.)</span>
                  </dd>
                  <dt>Last Seen</dt>
                  <dd>{moment(client.last_seen).format()}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="IP Addresses"
                                       hideTimeRange={true} />

                <TransparentIpAddressTable addresses={client.transparent_ip_addresses}/>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Hostnames"
                                       hideTimeRange={true} />

                <TransparentHostnamesTable hostnames={client.transparent_hostnames}/>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Observed Connections to BSSIDs"
                                       fixedAppliedTimeRange={Presets.ALL_TIME}/>

                <ClientBSSIDHistory connectedBSSID={client.connected_bssid ? client.connected_bssid : null}
                                    bssids={client.connected_bssid_history}/>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Observed Probe Requests"
                                       fixedAppliedTimeRange={Presets.ALL_TIME}/>

                <ObservedProbeRequestsList probeRequests={client.probe_requests}/>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Average Data &amp; Control Frames Signal Strengths By Tap"
                                           fixedAppliedTimeRange={Presets.RELATIVE_MINUTES_15}/>

                    <TapBasedSignalStrengthTable strengths={client.connected_signal_strength}/>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Physical Location / Trilateration"
                                           helpLink="https://go.nzyme.org/wifi-trilateration"
                                           timeRange={trilaterationTimeRange}
                                           setTimeRange={setTrilaterationTimeRange}/>

                    <ReadOnlyTrilaterationResultFloorPlanWrapper data={trilaterationResult}
                                                                 onFloorSelected={onFloorSelected}
                                                                 taps={selectedTaps}
                                                                 onRefresh={onTrilaterationRefresh}
                                                                 error={trilaterationError}/>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Data &amp; Control Frames Signal Strength"
                                           urlKey="c_ss"
                                           timeRange={connectedSignalStrengthHistogramTimeRange}
                                           setTimeRange={setConnectedSignalStrengthHistogramTimeRange} />

                    <ClientSignalStrengthChart data={connectedSignalStrengthHistogram}
                                               urlKey="c_ss"
                                               timeRange={connectedSignalStrengthHistogramTimeRange}
                                               setTimeRange={setConnectedSignalStrengthHistogramTimeRange} />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Average Probe Request Frames Signal Strengths By Tap"
                                           fixedAppliedTimeRange={Presets.RELATIVE_MINUTES_15}/>

                    <TapBasedSignalStrengthTable strengths={client.disconnected_signal_strength}/>
                  </div>
                </div>
              </div>
            </div>
            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Probe Request Frames Signal Strength"
                                           urlKey="d_ss"
                                           timeRange={disconnectedSignalStrengthHistogramTimeRange}
                                           setTimeRange={setDisconnectedSignalStrengthHistogramTimeRange} />

                    <ClientSignalStrengthChart data={disconnectedSignalStrengthHistogram}
                                               urlKey="d_ss"
                                               timeRange={disconnectedSignalStrengthHistogramTimeRange}
                                               setTimeRange={setDisconnectedSignalStrengthHistogramTimeRange}/>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Recorded Frames"
                                       urlKey="frame_count"
                                       timeRange={frameCountHistogramTimeRange}
                                       setTimeRange={setFrameCountHistogramTimeRange} />

                <select className="form-select form-select-sm" style={{width: 250}}
                        onChange={(e) => {
                          setFrameCountHistogramType(e.target.value)
                        }}
                        value={frameCountHistogramType}>
                  <option value="total_frames">Total Frames</option>
                  <option value="connected_frames">Data & Control Frames</option>
                  <option value="disconnected_frames">Probe Request Frames</option>
                  <option value="disconnection_activity">Disconnection Frames</option>
                </select>

                <ClientActivityHistogram histogram={frameCountHistogram}
                                         parameter={frameCountHistogramType}
                                         timeRange={frameCountHistogramTimeRange}
                                         setTimeRange={setFrameCountHistogramTimeRange}
                                         urlKey="frame_count"
                                         type="bar"/>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Disconnection Pairs"
                                       timeRange={discoPairsTimeRange}
                                       setTimeRange={setDiscoPairsTimeRange}/>

                <DiscoPairsTable bssids={[client.mac.address]} highlightValue={client.mac.address}
                                 timeRange={discoPairsTimeRange}/>

                <p className="mb-0 mt-3 text-muted">
                  The MAC address of this client is <span className="highlighted">highlighted.</span>
                </p>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default ClientDetailsPage;