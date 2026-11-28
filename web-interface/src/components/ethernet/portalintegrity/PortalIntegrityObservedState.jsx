import React, {useEffect, useState} from "react";
import moment from "moment";
import numeral from "numeral";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import PortalIntegrityService from "../../../services/ethernet/PortalIntegrityService";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import IPAddressLink from "../shared/IPAddressLink";
import TLSCertificateFingerprint from "../tls/TLSCertificateFingerprint";
import {truncate} from "../../../util/Tools";
import PortalIntegrityResult from "./PortalIntegrityResult";

const portalIntegrityService = new PortalIntegrityService();

const KINDS = {
  PORTAL: "Captive Portal",
  NO_PORTAL: "No Captive Portal",
  FAILED: "Failed Request"
};

const none = <span className="text-muted">n/a</span>;

const importable = (outcome) => outcome.kind !== "FAILED" && outcome.result !== "AS_EXPECTED";

export default function PortalIntegrityObservedState({controlUrl, timeRange, revision, onImport}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [state, setState] = useState(null);
  const [failure, setFailure] = useState(null);

  useEffect(() => {
    let current = true;

    setState(null);
    setFailure(null);
    portalIntegrityService.findObservedState(controlUrl.uuid, organizationId, tenantId, timeRange, (response) => {
      if (current) {
        setState(response);
      }
    }, (error) => {
      if (current) {
        setFailure(error.response.status === 422 && error.response.data
          ? error.response.data.message : "Could not load the observed state.");
      }
    });

    return () => {
      current = false;
    }
  }, [controlUrl.uuid, controlUrl.updated_at, organizationId, tenantId, timeRange, revision]);

  const importOutcomes = (outcomes) => (e) => {
    e.preventDefault();
    onImport(outcomes);
  }

  if (failure) {
    return <div className="mb-0 alert alert-danger">{failure}</div>
  }

  if (!state) {
    return <GenericWidgetLoadingSpinner height={100} />
  }

  if (state.outcomes.length === 0) {
    return (
      <div className="mb-0 alert alert-info">
        The captive portal probe sent no reports for this control URL in the selected time range.
      </div>
    )
  }

  const portals = state.outcomes.filter((o) => o.kind === "PORTAL" && importable(o));

  return (
    <>
      {state.reports_limited ?
        <div className="alert alert-info">
          Only the latest {numeral(state.report_limit).format("0,0")} reports of the control URL in the time range
          were looked at, over both IP versions together.
        </div> : null}

      {state.outcomes_limited ?
        <div className="alert alert-warning">
          The reports showed more than {numeral(state.outcome_limit).format("0,0")} different outcomes. Only
          the {numeral(state.outcome_limit).format("0,0")} seen last are listed.
        </div> : null}

      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Observed</th>
          <th>Final URL</th>
          <th>Address</th>
          <th>Certificate</th>
          <th>Reports</th>
          <th>First Seen</th>
          <th>Last Seen</th>
          <th>Status</th>
          {onImport ? <th>&nbsp;</th> : null}
        </tr>
        </thead>
        <tbody>
        {state.outcomes.map((o, i) => {
          return (
            <tr key={i}>
              <td className="text-nowrap">{KINDS[o.kind] ? KINDS[o.kind] : o.kind}</td>
              <td className="text-break" title={o.kind === "FAILED" ? undefined : o.final_url}>
                {o.kind === "FAILED" ? truncate(o.error, 200, false)
                  : <span className="machine-data">{truncate(o.final_url, 100, false)}</span>}
              </td>
              <td className="text-nowrap">{o.final_address ? <IPAddressLink ip={o.final_address} /> : none}</td>
              <td className="text-nowrap">
                {o.final_certificate ? <TLSCertificateFingerprint sha256={o.final_certificate} /> : none}
              </td>
              <td>{numeral(o.report_count).format("0,0")}</td>
              <td title={moment(o.first_seen).format()}>{moment(o.first_seen).fromNow()}</td>
              <td title={moment(o.last_seen).format()}>{moment(o.last_seen).fromNow()}</td>
              <td><PortalIntegrityResult controlUrl={{result: o.result, reasons: o.reasons, last_error: o.error}} /></td>
              {onImport ?
                <td className="text-end text-nowrap">
                  {importable(o) ?
                    <a href="#" onClick={importOutcomes([o])} title="Add this to the expectation form">Import</a> : null}
                </td> : null}
            </tr>
          )
        })}
        </tbody>
      </table>

      {onImport && portals.length > 1 ?
        <div className="mt-3">
          <button type="button" className="btn btn-sm btn-outline-secondary" onClick={importOutcomes(portals)}>
            Import All {numeral(portals.length).format("0,0")} Captive Portals
          </button>
        </div> : null}
    </>
  )

}
