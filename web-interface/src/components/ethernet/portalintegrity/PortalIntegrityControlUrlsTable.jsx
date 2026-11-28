import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import {TapContext} from "../../../App";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import PortalIntegrityService from "../../../services/ethernet/PortalIntegrityService";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../misc/Paginator";
import ApiRoutes from "../../../util/ApiRoutes";
import {truncate} from "../../../util/Tools";
import PortalIntegrityResult, {portalExpectationToName} from "./PortalIntegrityResult";

const portalIntegrityService = new PortalIntegrityService();

export default function PortalIntegrityControlUrlsTable({revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [data, setData] = useState(null);
  const [failed, setFailed] = useState(false);

  const perPage = 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setPage(1);
  }, [organizationId, tenantId, selectedTaps]);

  useEffect(() => {
    let current = true;

    setData(null);
    setFailed(false);
    portalIntegrityService.findAllControlUrls(organizationId, tenantId, selectedTaps, perPage, (page-1)*perPage, (response) => {
      if (!current) {
        return;
      }

      // The list got shorter than the page that was asked for.
      if (response.control_urls.length === 0 && response.total > 0 && page > 1) {
        setPage(1);
        return;
      }

      setData(response);
    }, () => {
      if (current) {
        setFailed(true);
      }
    });

    return () => {
      current = false;
    }
  }, [organizationId, tenantId, selectedTaps, page, revision]);

  const presented = (c) => {
    if (c.last_error) {
      return <span className="text-muted" title={c.last_error}>n/a</span>
    }

    if (!c.last_portal_presented) {
      return <span>No Captive Portal</span>
    }

    return <span title={c.last_final_url}>{truncate(c.last_final_url, 45, false)}</span>
  }

  if (failed) {
    return <div className="mb-0 alert alert-danger">Could not load the control URLs.</div>
  }

  if (!data) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (data.total === 0) {
    return (
      <div className="mb-0 alert alert-info">
        No control URLs were recorded for the selected taps. They are recorded with the first report of a captive
        portal probe.
      </div>
    )
  }

  return (
    <>
      {data.taps_at_limit.length > 0 ?
        <div className="alert alert-warning">
          The limit of {numeral(data.maximum_per_tap).format("0,0")} control URLs per tap is reached
          on {data.taps_at_limit.join(", ")}. New control URLs of {data.taps_at_limit.length === 1 ? "this tap" : "these taps"} are
          not recorded.
        </div> : null}

      <strong>Total:</strong> {numeral(data.total).format("0,0")}

      <table className="table table-sm table-hover table-striped mb-4 mt-3">
        <thead>
        <tr>
          <th>Probe Name</th>
          <th className="hide-narrow">Tap</th>
          <th>IP Version</th>
          <th>Control URL</th>
          <th>Expectation</th>
          <th>Status</th>
          <th className="hide-narrow">Presented Portal</th>
          <th>Last Report</th>
        </tr>
        </thead>
        <tbody>
        {data.control_urls.map((c) => {
          return (
            <tr key={c.uuid}>
              <td className="text-break">
                <Link to={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.CONTROL_URL_DETAILS(c.uuid)}>{c.probe_name}</Link>
              </td>
              <td className="hide-narrow">{c.tap_name ? c.tap_name : <span className="text-muted">n/a</span>}</td>
              <td>IPv{c.ip_version}</td>
              <td title={c.control_url}>{truncate(c.control_url, 80, false)}</td>
              <td>{portalExpectationToName(c.expectation)}</td>
              <td><PortalIntegrityResult controlUrl={c} /></td>
              <td className="hide-narrow">{presented(c)}</td>
              <td title={moment(c.last_report_at).format()}>{moment(c.last_report_at).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={data.total} perPage={perPage} setPage={setPage} page={page} />
    </>
  )

}
