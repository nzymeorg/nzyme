import React from "react";
import moment from "moment";
import numeral from "numeral";
import {toast} from "react-toastify";
import SystemService from "../../../services/SystemService";

const systemService = new SystemService();

export default function DatabaseUsageStatus({sizes, onRefresh, children}) {

  if (!sizes) {
    return children;
  }

  const recalculate = (e) => {
    e.preventDefault();
    systemService.recalculateDatabaseUsage(
      () => { toast.success("Recalculation queued."); onRefresh(); },
      (error) => {
        if (error.response && error.response.status === 429) {
          toast.info("Rows were counted less than 5 minutes ago.");
        } else {
          toast.info("A recalculation is already running.");
        }
        onRefresh();
      }
    );
  };

  let status;
  if (sizes.calculating) {
    status = <span><i className="fa-solid fa-play text-success" /> Counting rows now. Reload the page in a minute.</span>;
  } else if (sizes.estimated || !sizes.calculated_at) {
    status = <span>Rows have not been counted yet.</span>;
  } else {
    status = <span>Rows counted <span title={moment(sizes.calculated_at).format()}>{moment(sizes.calculated_at).fromNow()}</span>.</span>;
  }

  return (
    <React.Fragment>
      <p className="text-muted small">{status}</p>
      {sizes.calculation_error ?
        <p className="text-danger small">The last count failed: {sizes.calculation_error}</p> : null}

      {children}

      <button className="btn btn-sm btn-secondary mt-3" onClick={recalculate} disabled={sizes.calculating}>
        Recalculate now
      </button>
    </React.Fragment>
  )

}

export function formatRows(entry) {
  if (!entry || entry.rows === null || entry.rows === undefined) {
    return <span className="text-muted">not counted yet</span>;
  }

  const formatted = new Intl.NumberFormat().format(entry.rows);

  if (entry.rows_estimated) {
    return <span title="Planner estimate">&asymp; {formatted}</span>;
  }

  return formatted;
}

export function formatBytes(entry) {
  if (!entry || entry.bytes === null || entry.bytes === undefined) {
    return <span className="text-muted">not calculated yet</span>;
  }

  const formatted = numeral(entry.bytes).format("0,0b");

  if (entry.bytes_estimated) {
    return <span title="Estimate based on the share of rows">&asymp; {formatted}</span>;
  }

  return formatted;
}
