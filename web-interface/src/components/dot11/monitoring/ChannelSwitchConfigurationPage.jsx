import React, {useCallback, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import Dot11Service from "../../../services/Dot11Service";
import ApiRoutes from "../../../util/ApiRoutes";
import LoadingSpinner from "../../misc/LoadingSpinner";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeUrl";
import ChannelSwitchAnnouncements from "../channelswitches/ChannelSwitchAnnouncements";
import ChannelSwitchMonitorChart from "../channelswitches/ChannelSwitchMonitorChart";
import InlineTapSelector from "../../shared/InlineTapSelector";
import SimulatorButton from "./disco/configuration/simulator/SimulatorButton";
import ChannelSwitchMonitorRulesTable from "./ChannelSwitchMonitorRulesTable";
import {toast} from "react-toastify";
import usePageTitle from "../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

const MINIMUM = 10;
const MAXIMUM = 1000;

function ChannelSwitchConfigurationPage() {

  const { uuid } = useParams();

  const [ssid, setSSID] = useState(null);
  const [burstThreshold, setBurstThreshold] = useState("");
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(0);

  const [showSimulator, setShowSimulator] = useState(false);
  const [simulationTapUuid, setSimulationTapUuid] = useState(null);
  const [simulationTimeRange, setSimulationTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24, "simulation");

  usePageTitle(ssid ? `Channel Switch Monitor: ${ssid.ssid}` : "Channel Switch Monitor Configuration");

  useEffect(() => {
    let active = true;

    dot11Service.findMonitoredSSID(uuid, (found) => {
      if (active) {
        setSSID(found);
      }
    }, () => {});

    return () => {
      active = false;
    }
  }, [uuid, revision]);

  // Reloading the network after a detection method changed must not reset a threshold that is being edited.
  useEffect(() => {
    if (ssid) {
      setBurstThreshold(ssid.channel_switch_burst_threshold);
    }
  }, [ssid ? ssid.uuid : null]);

  const parsed = parseInt(burstThreshold, 10);
  const valid = /^\d+$/.test(String(burstThreshold).trim()) && parsed >= MINIMUM && parsed <= MAXIMUM;

  // The simulator keeps its tap selection in this page, so both go when it is hidden.
  const toggleSimulator = useCallback((toggled) => {
    setShowSimulator(toggled);
    if (!toggled) {
      setSimulationTapUuid(null);
    }
  }, []);

  const onSubmit = (e) => {
    e.preventDefault();

    dot11Service.setChannelSwitchMonitorConfiguration(uuid, parsed, () => {
      toast.success("Configuration updated.");
      setRedirect(true);
    })
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(uuid)} />
  }

  if (!ssid) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-7">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.OVERVIEW}>WiFi</Link></li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.MONITORING.INDEX}>Monitoring</Link></li>
                <li className="breadcrumb-item">Monitored Networks</li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(ssid.uuid)}>{ssid.ssid}</Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Channel Switch Monitor</li>
              </ol>
            </nav>
          </div>
          <div className="col-md-5">
            <Link className="btn btn-primary float-end" to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(uuid)}>Back</Link>
          </div>
        </div>

        <div className="col-md-12">
          <h1>
            Channel Switch Monitor
          </h1>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Detection Methods"
                                       helpLink="https://go.nzyme.org/wifi-network-monitoring-channel-switches-methods" />

                <ChannelSwitchMonitorRulesTable ssid={ssid} onChange={() => setRevision((previous) => previous + 1)} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Configure Rate Threshold"
                                       helpLink="https://go.nzyme.org/wifi-network-monitoring-channel-switches-threshold" />

                {ssid.channel_switch_disabled_rules.includes("BURST_RATE") ?
                    <div className="alert alert-warning">The rate threshold method is disabled for this network.</div>
                    : null}

                <p>
                  The monitor alerts when beacons of this network announce a channel switch at this rate or faster.
                </p>

                <label htmlFor="burst-threshold" className="form-label">Threshold</label>
                <div className="input-group mb-1" style={{width: 240}}>
                  <input type="number"
                         className="form-control"
                         id="burst-threshold"
                         value={burstThreshold}
                         min={MINIMUM}
                         max={MAXIMUM}
                         onChange={(e) => { setBurstThreshold(e.target.value); toggleSimulator(false); }}/>
                  <span className="input-group-text">frames per second</span>
                </div>
                <div className="form-text mb-3">The threshold must be between {MINIMUM} and {MAXIMUM}.</div>

                {valid ? <SimulatorButton isToggled={showSimulator} setToggled={toggleSimulator} />
                    : <button className="btn btn-secondary" disabled={true}>Simulate</button>}{' '}

                <button className="btn btn-primary" onClick={onSubmit} disabled={!valid}>
                  Save Configuration
                </button>
              </div>
            </div>
          </div>
        </div>

        {!showSimulator || !valid ? null :
            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Detection Simulator"
                                           urlKey="simulation"
                                           timeRange={simulationTimeRange}
                                           setTimeRange={setSimulationTimeRange}
                                           helpLink="https://go.nzyme.org/wifi-network-monitoring-channel-switches-threshold" />

                    <InlineTapSelector onTapSelected={(tapUuid) => setSimulationTapUuid(tapUuid)} />

                    <ChannelSwitchMonitorChart monitoredNetworkId={uuid}
                                               simulatedBurstThreshold={parsed}
                                               tapUuid={simulationTapUuid}
                                               timeRange={simulationTimeRange}
                                               setTimeRange={setSimulationTimeRange}
                                               urlKey="simulation" />

                    {simulationTapUuid ?
                        <p className="mt-2 text-muted">
                          Announcements at or above the threshold are highlighted on the chart with red background.
                        </p> : null}

                    {simulationTapUuid ?
                        <ChannelSwitchAnnouncements timeRange={simulationTimeRange}
                                                    monitoredNetworkId={uuid}
                                                    simulatedBurstThreshold={parsed}
                                                    taps={[simulationTapUuid]}
                                                    perPage={10}
                                                    emptyText="No announcement of this tap and time range reaches the simulated threshold." />
                        : null}
                  </div>
                </div>
              </div>
            </div>
        }
      </React.Fragment>
  )

}

export default ChannelSwitchConfigurationPage;
