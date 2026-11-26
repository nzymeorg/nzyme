import React, {useContext, useEffect, useMemo, useState} from "react";
import {Link, useParams} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";
import usePageTitle from "../../../../util/UsePageTitle";
import ApiRoutes from "../../../../util/ApiRoutes";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import AutomaticDot11MacAddressLink from "../../../shared/context/macs/AutomaticDot11MacAddressLink";
import {Absolute} from "../../../shared/timerange/TimeRange";
import Dot11Service from "../../../../services/Dot11Service";
import DiscoFramesTable from "../DiscoFramesTable";
import ConnectionFramesTable from "./ConnectionFramesTable";
import FrameSignalChart from "../../signal/FrameSignalChart";
import {FrameSignalInvestigationContext} from "../../signal/FrameSignalInvestigation";
import {DISCO_SIGNAL_INVESTIGATION} from "../signal/DiscoSignalInvestigation";
import FrameSignalChartNotes, {frameSignalChartTitle} from "../../signal/FrameSignalChartNotes";
import {
  discoAnomalySignalLink,
  discoAnomalySignalTimeRange,
  discoFramesLink,
  discoSignalLink,
  discoSignalTransmitterFilters,
  isAnomalyOfDisconnectionFrames
} from "../signal/DiscoSignalLinks";
import {frameSignalChartKey} from "../../signal/FrameSignalChart";
import PivotLink from "../../../shared/PivotLink";
import Dot11MacAddress from "../../../shared/context/macs/Dot11MacAddress";
import {macFilter} from "../../../ethernet/assets/details/AssetProfileHelpers";
import DiscoAnomalyClient from "./DiscoAnomalyClient";
import {connectionFrameTypeToName, DISCO_ANOMALY_TYPES, discoAnomalyTypeToName} from "./DiscoAnomalyTypes";

const dot11Service = new Dot11Service();

const EVIDENCE_MARGIN_SECONDS = 60;
const MAX_REASON_CODES = 32;

export default function DiscoAnomalyDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);

  const [anomaly, setAnomaly] = useState(null);
  const [loadError, setLoadError] = useState(null);

  const [discoFrames, setDiscoFrames] = useState(null);
  const [discoOrderColumn, setDiscoOrderColumn] = useState("created_at");
  const [discoOrderDirection, setDiscoOrderDirection] = useState("ASC");
  const [discoPage, setDiscoPage] = useState(1);

  const [signalAnalysis, setSignalAnalysis] = useState(null);

  const [connectionFrames, setConnectionFrames] = useState(null);
  const [connectionPage, setConnectionPage] = useState(1);

  const [revision, setRevision] = useState(new Date());

  const perPage = 25;

  usePageTitle(anomaly
      ? "Disconnection Anomaly: " + discoAnomalyTypeToName(anomaly.anomaly_type, anomaly.title) : "Disconnection Anomaly");

  useEffect(() => {
    let active = true;

    setAnomaly(null);
    setLoadError(null);
    setDiscoPage(1);
    setConnectionPage(1);
    dot11Service.findDiscoAnomaly(uuid, organizationId, tenantId,
        (response) => active && setAnomaly(response),
        (error) => active && setLoadError(error));

    return () => {
      active = false;
    }
  }, [uuid, organizationId, tenantId, revision]);

  const tapUuid = anomaly ? anomaly.tap_uuid : null;
  const bssid = anomaly ? anomaly.bssid.mac.address : null;
  const anomalyType = anomaly ? anomaly.anomaly_type : null;
  const firstSeen = anomaly ? anomaly.first_seen : null;
  const lastSeen = anomaly ? anomaly.last_seen : null;

  // Reports are written up to ten seconds after a frame was captured.
  const evidenceTimeRange = useMemo(() => {
    if (!firstSeen || !lastSeen) {
      return null;
    }

    return Absolute(
        moment(firstSeen).subtract(EVIDENCE_MARGIN_SECONDS, "seconds").toDate(),
        moment(lastSeen).add(EVIDENCE_MARGIN_SECONDS + 10, "seconds").toDate()
    );
  }, [firstSeen, lastSeen]);

  useEffect(() => {
    if (!evidenceTimeRange) {
      return;
    }

    let active = true;

    setDiscoFrames(null);
    dot11Service.findDiscoFrames(organizationId, tenantId, evidenceTimeRange, {}, [tapUuid], [bssid],
        discoOrderColumn, discoOrderDirection, perPage, (discoPage - 1) * perPage,
        (response) => active && setDiscoFrames(response));

    return () => {
      active = false;
    }
  }, [organizationId, tenantId, evidenceTimeRange, tapUuid, bssid, discoOrderColumn, discoOrderDirection, discoPage]);

  const connectionTimeRange = useMemo(() => {
    if (!firstSeen || !lastSeen) {
      return null;
    }

    return Absolute(
        moment(firstSeen).subtract(EVIDENCE_MARGIN_SECONDS, "seconds").toDate(),
        moment(lastSeen).add(EVIDENCE_MARGIN_SECONDS, "seconds").toDate()
    );
  }, [firstSeen, lastSeen]);

  const client = anomaly && anomaly.client ? anomaly.client.mac.address : null;

  const signalTimeRange = useMemo(() => {
    if (!firstSeen || !lastSeen) {
      return null;
    }

    return discoAnomalySignalTimeRange(firstSeen, lastSeen);
  }, [firstSeen, lastSeen]);

  const signalPivot = (address, role) => {
    const link = discoAnomalySignalLink(anomaly, [address]);
    if (!link) {
      return null;
    }

    return <PivotLink to={link}
                      title={"Signal strength investigation of this " + role}
                      icon={true}
                      className="ms-2 small" />
  }

  const signalTransmitters = useMemo(() => bssid ? (client ? [bssid, client] : [bssid]) : null, [bssid, client]);

  useEffect(() => {
    setSignalAnalysis(null);

    if (!signalTimeRange || !signalTransmitters || !isAnomalyOfDisconnectionFrames(anomalyType)) {
      return;
    }

    let active = true;
    dot11Service.getFrameSignalCharts(DISCO_SIGNAL_INVESTIGATION.apiPath, organizationId, tenantId, signalTimeRange,
        discoSignalTransmitterFilters(signalTransmitters), [tapUuid], null,
        (response) => active && setSignalAnalysis(response),
        () => {});

    return () => {
      active = false;
    }
  }, [organizationId, tenantId, signalTimeRange, signalTransmitters, tapUuid, anomalyType]);

  useEffect(() => {
    if (!evidenceTimeRange) {
      return;
    }

    let active = true;

    setConnectionFrames(null);
    dot11Service.findDiscoAnomalyConnectionFrames(uuid, organizationId, tenantId, perPage,
        (connectionPage - 1) * perPage,
        (response) => active && setConnectionFrames(response),
        () => active && setConnectionFrames({total: 0, frames: []}));

    return () => {
      active = false;
    }
  }, [uuid, organizationId, tenantId, evidenceTimeRange, connectionPage]);

  if (loadError) {
    return (
        <div className="alert alert-warning">
          {loadError.response && loadError.response.status === 404
              ? "Anomaly not found." : "The anomaly could not be loaded."}{" "}
          <Link to={ApiRoutes.DOT11.DISCO.ANOMALIES.INDEX}>Back to Anomalies</Link>
        </div>
    )
  }

  if (!anomaly) {
    return <LoadingSpinner />
  }

  const type = DISCO_ANOMALY_TYPES[anomaly.anomaly_type];
  const name = discoAnomalyTypeToName(anomaly.anomaly_type, anomaly.title);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.OVERVIEW}>WiFi</Link></li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.DISCO.INDEX}>Disconnections</Link></li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.DISCO.ANOMALIES.INDEX}>Anomalies</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{name}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-8">
            <h1>{name}</h1>
          </div>

          <div className="col-4 text-end">
            <Link to={ApiRoutes.DOT11.DISCO.ANOMALIES.INDEX} className="btn btn-primary">Back to Anomalies</Link>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="What Happened"
                                       refreshAction={() => setRevision(new Date())}
                                       helpLink={type ? type.helpLink : "https://go.nzyme.org/disco-anomalies-types"} />

                {type ?
                    <React.Fragment>
                      <p>{type.observed(anomaly)}</p>
                      <p className="mb-0">{type.meaning}</p>
                    </React.Fragment>
                    : <p className="mb-0">This version of the web interface does not know this anomaly type.</p>}
              </div>
            </div>

            {type ?
                <div className="card mt-3">
                  <div className="card-body">
                    <CardTitleWithControls title="Possible False Positives" />

                    <ul className="mb-0">
                      {type.falsePositives.map((falsePositive, i) => <li key={i}>{falsePositive}</li>)}
                    </ul>
                  </div>
                </div> : null}
          </div>

          <div className="col-xl-12 col-xxl-6 mt-3 mt-xxl-0">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Details" helpLink="https://go.nzyme.org/disco-anomalies-details" />

                <dl className="mb-0">
                  <dt>Access Point</dt>
                  <dd>
                    <AutomaticDot11MacAddressLink mac={anomaly.bssid.mac.address}
                                                  type={anomaly.bssid.type}
                                                  addressWithContext={anomaly.bssid.mac}
                                                  filterElement={signalPivot(anomaly.bssid.mac.address, "access point")} />
                  </dd>
                  <dt>Monitored Network</dt>
                  <dd>
                    {anomaly.monitored_network_uuid ?
                        (userHasPermission(user, "dot11_monitoring_manage") ?
                            <Link to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(anomaly.monitored_network_uuid)}>
                              {anomaly.monitored_network_ssid}
                            </Link>
                            : <span className="text-break">{anomaly.monitored_network_ssid}</span>)
                        : <span className="text-muted">None</span>}
                  </dd>
                  <dt>Client</dt>
                  <dd>
                    <DiscoAnomalyClient anomaly={anomaly}
                                        filterElement={anomaly.client ? signalPivot(anomaly.client.mac.address, "client") : null} />
                  </dd>
                  <dt>Recorded by Tap</dt>
                  <dd>{anomaly.tap_name ? anomaly.tap_name : <span className="text-muted">Unknown</span>}</dd>
                  <dt>First Seen</dt>
                  <dd title={moment(anomaly.first_seen).fromNow()}>{moment(anomaly.first_seen).format()}</dd>
                  <dt>Last Seen</dt>
                  <dd title={moment(anomaly.last_seen).fromNow()}>
                    {moment(anomaly.last_seen).format()}{anomaly.is_ongoing ? " (Ongoing)" : ""}
                  </dd>
                  <dt>Recorded Frames</dt>
                  <dd>{numeral(anomaly.frame_count).format("0,0")}</dd>
                  <dt>Frame Types</dt>
                  <dd>{anomaly.frame_types.map(connectionFrameTypeToName).join(", ")}</dd>
                  <dt>Reason Codes</dt>
                  <dd className="mb-0">
                    {anomaly.reason_codes.length === 0 ? <span className="text-muted">None</span>
                        : anomaly.reason_codes.map((reason) => reason.code + " (" + reason.name + ")").join(", ")}
                    {anomaly.reason_codes.length >= MAX_REASON_CODES ?
                        <span className="text-muted"> The list shows the first {MAX_REASON_CODES}.</span> : null}
                  </dd>
                </dl>
              </div>
            </div>

            {anomaly.sample_clients.length > 0 ?
                <div className="card mt-3">
                  <div className="card-body">
                    <CardTitleWithControls title="Clients" />

                    <p className="text-muted">
                      The list shows up to 25 of the addresses that took part.
                    </p>

                    <ul className="list-unstyled mb-0">
                      {anomaly.sample_clients.map((sample) => {
                        return (
                            <li key={sample}>
                              <Dot11MacAddress address={sample} href={ApiRoutes.DOT11.CLIENTS.DETAILS(sample)} />
                            </li>
                        )
                      })}
                    </ul>
                  </div>
                </div> : null}
          </div>
        </div>

        {signalAnalysis && signalAnalysis.charts.length > 0 ?
            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Signal Strength"
                                           timeRange={signalTimeRange}
                                           internalLink={discoSignalLink(signalTransmitters, signalTimeRange)}
                                           helpLink="https://go.nzyme.org/signal-investigation-evidence" />

                    {signalAnalysis.charts.length >= signalAnalysis.default_charts ?
                        <p className="text-muted mb-0">
                          The charts show the {signalAnalysis.default_charts} channels of this tap with the most
                          important findings.
                        </p> : null}

                    {signalAnalysis.charts.map((chart, i) => {
                      return (
                          <div className={i === 0 ? null : "mt-4"} key={frameSignalChartKey(chart)}>
                            <h6 className="mb-0">{frameSignalChartTitle(chart)}</h6>

                            <FrameSignalInvestigationContext.Provider value={DISCO_SIGNAL_INVESTIGATION}>
                              <FrameSignalChart chart={chart}
                                                sources={signalAnalysis.sources}
                                                threshold={signalAnalysis.threshold}
                                                bucketSeconds={signalAnalysis.bucket_seconds}
                                                timeRange={signalTimeRange} />

                              <FrameSignalChartNotes chart={chart}
                                                     charts={signalAnalysis}
                                                     sources={signalAnalysis.sources} />
                            </FrameSignalInvestigationContext.Provider>
                          </div>
                      )
                    })}
                  </div>
                </div>
              </div>
            </div> : null}

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Disconnection Frames"
                                       timeRange={evidenceTimeRange}
                                       internalLink={discoFramesLink(macFilter("bssid", bssid), evidenceTimeRange)}
                                       helpLink="https://go.nzyme.org/disco-frames" />

                <p className="text-muted">
                  The tap recorded these frames for this access point from one minute before to one minute after
                  the anomaly.
                </p>

                <DiscoFramesTable frames={discoFrames}
                                  page={discoPage}
                                  setPage={setDiscoPage}
                                  perPage={perPage}
                                  orderColumn={discoOrderColumn}
                                  orderDirection={discoOrderDirection}
                                  setOrderColumn={setDiscoOrderColumn}
                                  setOrderDirection={setDiscoOrderDirection}
                                  signalTimeRange={signalTimeRange} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Connection Frames"
                                       timeRange={connectionTimeRange}
                                       helpLink="https://go.nzyme.org/disco-connection-frames" />

                <p className="text-muted">
                  The tap recorded these authentication, association and EAPOL frames for this access point
                  {anomaly.client ? " and client " : " "}in the same time.
                </p>

                <ConnectionFramesTable frames={connectionFrames}
                                       page={connectionPage}
                                       setPage={setConnectionPage}
                                       perPage={perPage} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
