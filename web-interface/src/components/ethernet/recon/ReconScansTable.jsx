import React from "react";
import {Link} from "react-router-dom";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import numeral from "numeral";
import moment from "moment";
import ApiRoutes from "../../../util/ApiRoutes";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import ReconScanType from "./ReconScanType";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";

export default function ReconScansTable({scans, page, setPage, perPage, orderColumn, setOrderColumn,
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
      No scans match the current filters. Scans appear here once a device probes many ports of a host or many
      hosts in your network.
    </div>
  }

  const targetSummary = (s) => {
    if (s.is_port_scan) {
      return <span>{numeral(s.port_count).format("0,0")} ports</span>
    }

    if (s.type === "ARP_SWEEP" || s.type === "ICMP_SWEEP" || s.type === "DNS_REVERSE_SWEEP") {
      return <span>{numeral(s.host_count).format("0,0")} hosts</span>
    }

    return <span>{numeral(s.host_count).format("0,0")} hosts, {numeral(s.port_count).format("0,0")} ports</span>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>ID</th>
          <th>Type {columnSorting("type")}</th>
          <th>Scanner {columnSorting("source_address")}</th>
          <th>Scanner MAC {columnSorting("source_mac")}</th>
          <th>Target {columnSorting("target_address")}</th>
          <th>Probes {columnSorting("probe_count")}</th>
          <th>Answered {columnSorting("open_count")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {scans.scans.map((s) => {
          return (
            <tr key={s.uuid}>
              <td>
                <Link to={ApiRoutes.ETHERNET.RECON.SCANS.DETAILS(s.uuid)}>
                  <FullCopyShortenedId value={s.uuid} />
                </Link>
              </td>
              <td>
                <ReconScanType type={s.type} />{' '}
                {filterIcon("type", s.type)}
                {s.matching_exclusions && s.matching_exclusions.length > 0 ?
                  <span className="text-muted ms-1"
                        title={"Matches exclusion " + s.matching_exclusions.map((ex) => "\"" + ex.name + "\"").join(", ")
                          + ". No longer updated."}>
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
                             filterElement={filterIcon("target_address", s.target_address)}
                             suffixElement={<span className="text-muted">({targetSummary(s)})</span>} />
                  : targetSummary(s)}
              </td>
              <td>
                {numeral(s.probe_count).format("0,0")}{s.targets_truncated ? "+" : ""}{' '}
                {filterIcon("probe_count", s.probe_count)}
              </td>
              <td>{numeral(s.open_count).format("0,0")} {filterIcon("open_count", s.open_count)}</td>
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
