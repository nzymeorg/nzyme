import React from "react";
import {Link} from "react-router-dom";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import numeral from "numeral";
import moment from "moment";
import ApiRoutes from "../../../util/ApiRoutes";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";

const signatureName = (signature) => {
  switch (signature) {
    case "ECHO_NONZERO_CODE": return "Echo, non-zero code";
    case "ECHO_TOS_4": return "Echo, TOS 4";
    case "INFORMATION_REQUEST": return "Information request";
    case "TIMESTAMP_REQUEST": return "Timestamp request";
    case "ADDRESS_MASK_REQUEST": return "Address mask request";
    default: return signature;
  }
}

export default function ReconFingerprintingTable({scans, page, setPage, perPage, orderColumn, setOrderColumn,
                                                  orderDirection, setOrderDirection, filterFields, setFilters}) {

  if (!scans) {
    return <LoadingSpinner />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={filterFields} field={field} value={value} />
  }

  if (scans.scans.length === 0) {
    return <div className="alert alert-info mb-0">
      No OS fingerprinting attempts match the current filters. They appear here when a device sends the probes
      that operating system detection tools use.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>ID</th>
          <th>Scanner {columnSorting("source_address")}</th>
          <th>Scanner MAC {columnSorting("source_mac")}</th>
          <th>Target {columnSorting("target_address")}</th>
          <th>Probe Signatures</th>
          <th>Probes {columnSorting("probe_count")}</th>
          <th>Target Answered</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {scans.scans.map((s) => {
          const signatures = s.enrichment && s.enrichment.signatures ? s.enrichment.signatures : [];

          return (
            <tr key={s.uuid}>
              <td>
                <Link to={ApiRoutes.ETHERNET.RECON.SCANS.DETAILS(s.uuid)}>
                  <FullCopyShortenedId value={s.uuid} />
                </Link>
                {s.matching_exclusions && s.matching_exclusions.length > 0 ?
                  <span className="text-muted ms-1" title={"Matches exclusion " + s.matching_exclusions.map((ex) => "\"" + ex.name + "\"").join(", ")}>
                    (excluded)
                  </span> : null}
              </td>
              <td>
                {s.source_address_data ?
                  <L4Address address={s.source_address_data} hidePort={true} withAssetName
                             filterElement={filterIcon("source_address", s.source_address)} />
                  : <span className="machine-data">{s.source}</span>}
              </td>
              <td>
                {s.source_mac_data ?
                  <EthernetMacAddress addressWithContext={s.source_mac_data} withAssetLink
                                      filterElement={filterIcon("source_mac", s.source_mac)} />
                  : <span className="text-muted">n/a</span>}
              </td>
              <td>
                {s.target_address_data ?
                  <L4Address address={s.target_address_data} hidePort={true} withAssetName
                             filterElement={filterIcon("target_address", s.target_address)} />
                  : <span className="machine-data">{s.target}</span>}
              </td>
              <td>{signatures.map(signatureName).join(", ")}</td>
              <td>{numeral(s.probe_count).format("0,0")}</td>
              <td>{s.open_count > 0 ? "Yes" : <span className="text-muted">No</span>}</td>
              <td title={moment(s.first_seen).format()}>{moment(s.first_seen).fromNow()}</td>
              <td title={moment(s.last_seen).format()}>{moment(s.last_seen).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={scans.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
