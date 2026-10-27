import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Dot11Service from "../../../services/Dot11Service";
import ApiRoutes from "../../../util/ApiRoutes";
import moment from "moment";
import MonitoredBSSIDs from "./MonitoredBSSIDs";
import {toast} from "react-toastify";
import RefreshGears from "../../misc/RefreshGears";
import MonitoredChannelsTable from "./MonitoredChannelsTable";
import MonitoredSecuritySuitesTable from "./MonitoredSecuritySuitesTable";
import MonitoringDisabledWarning from "./MonitoringDisabledWarning";
import ToggleMonitoringStatusButton from "./ToggleMonitoringStatusButton";
import MonitoredNetworkAlertStatusTable from "./MonitoredNetworkAlertStatusTable";
import DiscoDetectionDetails from "./disco/DiscoDetectionDetails";
import MonitoredNetworkDiscoChart from "./disco/MonitoredNetworkDiscoChart";
import InlineTapSelector from "../../shared/InlineTapSelector";
import {isValidMACAddress} from "../../../util/Tools";
import {Presets} from "../../shared/timerange/TimeRange";
import usePageTitle from "../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

function MonitoredNetworkDetailsPage() {

  const {uuid} = useParams();

  const [ssid, setSSID] = useState(null);
  const [revision, setRevision] = useState(0);

  const [newBSSID, setNewBSSID] = useState("");
  const [bssidFormSubmitting, setBSSIDFormSubmitting] = useState(false);

  const [discoSimulationTapUuid, setDiscoSimulationTapUuid] = useState(null);

  const [newChannel, setNewChannel] = useState("");
  const [channelFormSubmitting, setChannelFormSubmitting] = useState(false);
  const [existingChannels, setExistingChannels] = useState([]);

  const [newSecuritySuite, setNewSecuritySuite] = useState("");
  const [securitySuiteFormSubmitting, setSecuritySuiteFormSubmitting] = useState(false);
  const [existingSecuritySuites, setExistingSecuritySuites] = useState([]);

  const [isLoading, setIsLoading] = useState(false);

  const [deleted, setDeleted] = useState(false);

  usePageTitle(ssid ? `Monitored Network: ${ssid.ssid}` : "Monitored WiFi Network");

  const onDelete = function () {
    if (!confirm("Really delete monitored network and all it's configuration?")) {
      return;
    }

    dot11Service.deleteMonitoredSSID(ssid.uuid, function () {
      toast.success("Monitored network deleted.");
      setDeleted(true);
    });
  }

  const addBSSID = function (bssid) {
    setBSSIDFormSubmitting(true);
    dot11Service.createMonitoredBSSID(ssid.uuid, bssid, function () {
      bumpRevision();
      toast.success("Monitored BSSID added.");
      setNewBSSID("");
      setBSSIDFormSubmitting(false);
    }, function () {
      toast.error("Could not add monitored BSSID. Please check nzyme log file.");
      setBSSIDFormSubmitting(false);
    })
  }

  const addChannel = function (channel) {
    setChannelFormSubmitting(true);
    dot11Service.createMonitoredChannel(ssid.uuid, channel, function () {
      bumpRevision();
      toast.success("Monitored channel added.");
      setNewChannel("");
      setChannelFormSubmitting(false);
    }, function () {
      toast.error("Could not add monitored channel. Please check nzyme log file.");
      setChannelFormSubmitting(false);
    })
  }

  const addSecuritySuite = function (suite) {
    setSecuritySuiteFormSubmitting(true);
    dot11Service.createMonitoredSecuritySuite(ssid.uuid, suite, function () {
      bumpRevision();
      toast.success("Monitored security suite added.");
      setNewSecuritySuite("");
      setSecuritySuiteFormSubmitting(false);
    }, function () {
      toast.error("Could not add monitored security suite. Please check nzyme log file.");
      setSecuritySuiteFormSubmitting(false);
    })
  }

  const bumpRevision = function () {
    setRevision(revision+1);
  }

  const addBSSIDFormEnabled = function () {
    const existingBSSIDs = ssid.bssids.map(function(bssid){
      return bssid.bssid
    });

    return isValidMACAddress(newBSSID) && !existingBSSIDs.includes(newBSSID.toUpperCase());
  }

  const addChannelFormEnabled = function () {
    return parseInt(newChannel, 10) > 0 && !existingChannels.includes(parseInt(newChannel, 10));
  }

  const addSecuritySuiteFormEnabled = function () {
    return (newSecuritySuite === "NONE" || (newSecuritySuite.includes("-") && newSecuritySuite.includes("/")))
        && !existingSecuritySuites.includes(newSecuritySuite);
  }

  const deleteAllBSSIDs = function (e) {
    e.preventDefault();

    if (!confirm("Really delete all monitored BSSID configurations?")) {
      return;
    }

    dot11Service.deleteAllMonitoredBSSIDs(ssid.uuid, function () {
      bumpRevision();
      toast.success("BSSID monitoring configurations deleted.");
    })
  }

  // More complex loading/refreshing logic because of modals that may be open on the page.
  useEffect(() => {
    setIsLoading(true);
    dot11Service.findMonitoredSSID(uuid, setSSID, function() {
      setIsLoading(false);
    });
  }, [uuid, revision]);

  useEffect(() => {
    if (ssid) {
      let ec = [];
      ssid.channels.map(function (channel, i) {
        ec.push(channel.frequency);
      });

      let ess = [];
      ssid.security_suites.map(function (suite, i) {
        ess.push(suite.suite);
      });

      setExistingSecuritySuites(ess);
      setExistingChannels(ec);
    }
  }, [ssid])

  if (deleted) {
    return <Navigate to={ApiRoutes.DOT11.MONITORING.INDEX} />
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
                <li className="breadcrumb-item active" aria-current="page">{ssid.ssid}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-5">
            <span className="float-end">
              <Link className="btn btn-secondary" to={ApiRoutes.DOT11.MONITORING.CONFIGURATION_IMPORT(ssid.uuid)}>
                Import Configuration
              </Link>{' '}
              <ToggleMonitoringStatusButton ssid={ssid} bumpRevision={bumpRevision}/>{' '}
              <a className="btn btn-danger" href="#" onClick={onDelete}>Delete</a>{' '}
              <Link className="btn btn-primary" to={ApiRoutes.DOT11.MONITORING.INDEX}>Back</Link>
            </span>
          </div>

          <div className="col-md-12">
            <h1>
              Monitored Network &quot;{ssid.ssid}&quot; {isLoading ? <RefreshGears/> : null}
            </h1>
          </div>
        </div>

        <MonitoringDisabledWarning show={!ssid.is_enabled}/>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Alert Status <small>Last 15 minutes</small></h3>

                <p className="text-muted">
                  It can take up to 5 minutes for an alert to automatically resolve after resolution. Manually mark an
                  alert as resolved on the alert details page if you want to speed this up.
                </p>

                <MonitoredNetworkAlertStatusTable ssid={ssid} renderControls={true} bumpRevision={bumpRevision}/>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Metadata</h3>

                <dl className="mb-0">
                  <dt>Configuration created at</dt>
                  <dd title={moment(ssid.creeated_at).format()}>{moment(ssid.created_at).fromNow()}</dd>
                  <dt>Configuration last changed at</dt>
                  <dd title={moment(ssid.updated_at).format()}>{moment(ssid.updated_at).fromNow()}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <div className="row mb-2">
                  <div className="col-md-8">
                    <h3>Monitored BSSIDs / Access Points of Network {isLoading ? <RefreshGears/> : null}</h3>
                  </div>
                  <div className="col-md-4">
                    <button type="button" onClick={deleteAllBSSIDs} className="btn btn-danger float-end button-sm">Delete all BSSIDs</button>
                  </div>
                </div>

                <MonitoredBSSIDs ssidUuid={ssid.uuid}
                                 bssids={ssid.bssids}
                                 bssidAlertingEnabled={ssid.enabled_unexpected_bssid}
                                 fingerprintAlertingEnabled={ssid.enabled_unexpected_fingerprint}
                                 bumpRevision={bumpRevision}
                                 parentIsLoading={isLoading}/>

                <div className="input-group mb-3">
                  <input type="text"
                         className="form-control"
                         placeholder="18:7C:0B:D6:EC:F8"
                         value={newBSSID}
                         onChange={(e) => setNewBSSID(e.target.value)}/>
                  <button className="btn btn-secondary"
                          disabled={!addBSSIDFormEnabled()}
                          onClick={() => {
                            addBSSID(newBSSID)
                          }}>
                    {bssidFormSubmitting ? "Please wait..." : "Add BSSID"}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Monitored Channels {isLoading ? <RefreshGears/> : null}</h3>

                <MonitoredChannelsTable ssid={ssid}
                                        alertingEnabled={ssid.enabled_unexpected_channel}
                                        bumpRevision={bumpRevision}/>

                <div className="input-group mb-3">
                  <input type="number"
                         className="form-control"
                         placeholder="2462"
                         value={newChannel}
                         onChange={(e) => setNewChannel(e.target.value)}/>
                  <span className="input-group-text">MHz</span>
                  <button className="btn btn-secondary"
                          disabled={!addChannelFormEnabled()}
                          onClick={() => {
                            addChannel(newChannel)
                          }}>
                    {channelFormSubmitting ? "Please wait..." : "Add Frequency "}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Monitored Security Suites {isLoading ? <RefreshGears/> : null}</h3>

                <MonitoredSecuritySuitesTable ssid={ssid}
                                              alertingEnabled={ssid.enabled_unexpected_security_suites}
                                              bumpRevision={bumpRevision}/>

                <div className="input-group mb-3">
                  <input type="text"
                         className="form-control"
                         placeholder="CCMP-CCMP/PSK"
                         value={newSecuritySuite}
                         onChange={(e) => setNewSecuritySuite(e.target.value)}/>
                  <button className="btn btn-secondary"
                          disabled={!addSecuritySuiteFormEnabled()}
                          onClick={() => {
                            addSecuritySuite(newSecuritySuite)
                          }}>
                    {securitySuiteFormSubmitting ? "Please wait..." : "Add Security Suite "}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <h3>Deauthentication/Disassociation Monitor</h3>

                <div className="mt-3">
                  <DiscoDetectionDetails monitoredNetwork={ssid}/>
                </div>

                <InlineTapSelector onTapSelected={(tapUuid) => setDiscoSimulationTapUuid(tapUuid)}/>

                <MonitoredNetworkDiscoChart timeRange={Presets.RELATIVE_HOURS_24}
                                            selectedTapUuid={discoSimulationTapUuid}
                                            monitoredNetwork={ssid}/>

                <p className="mt-2 mb-0 text-muted">
                  Anomalies highlighted on chart with red background. <strong>You can configure this monitor
                  from the alert overview at the top of this page.</strong>
                </p>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
)

}

export default MonitoredNetworkDetailsPage;