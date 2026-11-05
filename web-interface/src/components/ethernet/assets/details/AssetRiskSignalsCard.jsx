import React, {useEffect, useState} from "react";
import moment from "moment";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetsService from "../../../../services/ethernet/AssetsService";
import {Presets} from "../../../shared/timerange/TimeRange";
import {formatCount, riskSignalDescription, riskSignalTitle} from "./AssetProfileHelpers";
import SortableTable from "./SortableTable";

const SEVERITY_RANK = { CRITICAL: 4, WARNING: 3, NOTICE: 2, INFO: 1 };

const assetsService = new AssetsService();

export default function AssetRiskSignalsCard({asset, organizationId, tenantId, revision, onRefresh}) {

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_DAYS_7);
  const [risks, setRisks] = useState(null);

  useEffect(() => {
    setRisks(null);
    assetsService.getAssetProfileRisks(asset.uuid, organizationId, tenantId, timeRange, setRisks);
  }, [asset.uuid, organizationId, tenantId, timeRange, revision]);

  const title = (
    <React.Fragment>
      <CardTitleWithControls title="Risk Signals"
                             timeRange={timeRange}
                             setTimeRange={setTimeRange}
                             doNotPersistTimeRange={true}
                             refreshAction={onRefresh} />
      <p className="text-muted">
        The time range applies to the TLS and inbound session signals. Protocol signals come from the asset attributes,
        which are kept for the attribute retention time set in the asset settings. Detections and alerts follow their
        own retention.
      </p>
    </React.Fragment>
  );

  const severityText = (severity) => {
    switch (severity) {
      case "CRITICAL": return <span className="text-danger">Critical</span>;
      case "WARNING": return <span className="text-warning">Warning</span>;
      case "NOTICE": return <span>Notice</span>;
      default: return <span className="text-muted">Info</span>;
    }
  }

  // The detail of the inbound signal is the list of remote countries, which is not shown.
  const signalDetail = (s) => {
    if (!s.detail || s.signal === "INBOUND_EXTERNAL_SESSIONS") {
      return null;
    }

    return <span className="machine-data"> ({s.detail})</span>;
  }

  const columns = [
    { key: "severity", label: "Severity", value: (s) => SEVERITY_RANK[s.severity] || 0, render: (s) => severityText(s.severity) },
    { key: "signal", label: "Signal", value: (s) => riskSignalTitle(s.signal), render: (s) => (
        <React.Fragment>
          <strong>{riskSignalTitle(s.signal)}</strong>
          {signalDetail(s)}
          <br />
          <span className="text-muted small">{riskSignalDescription(s.signal)}</span>
        </React.Fragment>
      ) },
    { key: "count", label: "Count", value: (s) => s.count, render: (s) => formatCount(s.count) },
    { key: "last_seen", label: "Last Seen", value: (s) => s.last_seen ? moment(s.last_seen).valueOf() : 0,
      render: (s) => s.last_seen ? <span title={moment(s.last_seen).format()}>{moment(s.last_seen).fromNow()}</span> : <span className="text-muted">n/a</span> },
  ];

  if (!risks) {
    return <React.Fragment>{title}<LoadingSpinner /></React.Fragment>
  }

  const tlsCoverage = () => {
    if (!risks.tls_sessions) {
      return null;
    }

    const tls13 = risks.tls_versions["TLSv1.3"] || 0;
    const share = Math.round(tls13 / risks.tls_sessions * 100);

    return (
      <p className="text-muted small mb-0 mt-2">
        {formatCount(risks.tls_sessions)} TLS {risks.tls_sessions === 1 ? "session" : "sessions"} in the time range,{' '}
        {formatCount(tls13)} ({share}%) of them TLS 1.3. The certificate signals cannot inspect TLS 1.3 sessions, because
        the certificates are encrypted.
      </p>
    )
  }

  if (risks.signals.length === 0) {
    return (
      <React.Fragment>
        {title}
        <div className="alert alert-success mb-0">No risk signals.</div>
        {tlsCoverage()}
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      {title}

      <SortableTable columns={columns} rows={risks.signals} rowKey={(s) => s.signal} defaultColumn="severity" />
      {tlsCoverage()}
    </React.Fragment>
  )

}
