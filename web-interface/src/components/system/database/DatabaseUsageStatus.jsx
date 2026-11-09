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
    status = <span><i className="fa-solid fa-play text-success" /> Counting rows now. Refresh the page in a minute to see the result.</span>;
  } else if (sizes.estimated || !sizes.calculated_at) {
    status = <span>Rows have not been counted yet. The first count runs shortly after node start. Global row counts marked with &asymp; are planner estimates.</span>;
  } else {
    status = <span>Rows counted <span title={moment(sizes.calculated_at).format()}>{moment(sizes.calculated_at).fromNow()}</span>. Counts are refreshed every hour and after every purge. Sizes marked with &asymp; are shares of the table sizes, split by row count.</span>;
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
    return <span className="text-muted" title="Not counted yet. The first count runs shortly after node start.">not counted yet</span>;
  }

  const formatted = new Intl.NumberFormat().format(entry.rows);

  if (entry.rows_estimated) {
    return <span title="Estimate from the PostgreSQL planner statistics, refreshed by every analyze. The exact count follows with the first usage calculation.">&asymp; {formatted}</span>;
  }

  return formatted;
}

export function formatBytes(entry) {
  if (!entry || entry.bytes === null || entry.bytes === undefined) {
    return <span className="text-muted" title="Not calculated yet. The first calculation runs shortly after node start.">not calculated yet</span>;
  }

  const formatted = numeral(entry.bytes).format("0,0b");

  if (entry.bytes_estimated) {
    return <span title="Tables are shared between all tenants, so this size cannot be measured. It is the share of each table size that matches the share of rows.">&asymp; {formatted}</span>;
  }

  return formatted;
}
