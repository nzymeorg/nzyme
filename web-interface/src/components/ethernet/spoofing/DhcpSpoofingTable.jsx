import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../util/ApiRoutes";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import DhcpSpoofingFindingType from "./DhcpSpoofingFindingType";
import {DhcpOfferedValue} from "./DhcpOfferedParameters";

export default function DhcpSpoofingTable({findings, page, setPage, perPage, orderColumn, setOrderColumn,
                                           orderDirection, setOrderDirection, filterFields, setFilters}) {

  if (!findings) {
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

  if (findings.findings.length === 0) {
    return <div className="alert alert-info mb-0">
      No DHCP spoofing matches the current filters and time range. A finding appears here once an untrusted server
      answers clients, or a trusted server hands out a changed configuration.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>ID</th>
          <th>Type {columnSorting("type")}</th>
          <th>Server {columnSorting("server_address")}</th>
          <th>Server MAC {columnSorting("server_mac")}</th>
          <th>Gateway</th>
          <th>DNS Servers</th>
          <th>Domain</th>
          <th>Responses {columnSorting("response_count")}</th>
          <th>Transactions {columnSorting("transaction_count")}</th>
          <th>Victims {columnSorting("victim_count")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {findings.findings.map((f) => {
          return (
            <tr key={f.uuid}>
              <td>
                <Link to={ApiRoutes.ETHERNET.SPOOFING.DHCP.DETAILS(f.uuid)}>
                  <FullCopyShortenedId value={f.uuid} />
                </Link>
                {f.exclusions && f.exclusions.length > 0 ?
                  <span className="text-muted ms-1"
                        title={"Matches exclusion " + f.exclusions.map((ex) => "\"" + ex.name + "\"").join(", ")
                          + ". No longer updated."}>
                    (excluded)
                  </span> : null}
              </td>
              <td><DhcpSpoofingFindingType type={f.type} /> {filterIcon("type", f.type)}</td>
              <td>
                {f.server_address_data ?
                  <L4Address address={f.server_address_data} hidePort={true} withAssetName
                             filterElement={filterIcon("server_address", f.server_address)} />
                  : <span className="machine-data">{f.server_address}</span>}
              </td>
              <td>
                {f.server_mac_data ?
                  <EthernetMacAddress addressWithContext={f.server_mac_data} withAssetLink withAssetName
                                      filterElement={filterIcon("server_mac", f.server_mac)} />
                  : <span className="machine-data">{f.server_mac}</span>}
              </td>
              <td><DhcpOfferedValue parameters={f.offered} field="routers" compareTo={f.expected} /></td>
              <td><DhcpOfferedValue parameters={f.offered} field="dns_servers" compareTo={f.expected} /></td>
              <td><DhcpOfferedValue parameters={f.offered} field="domain_name" compareTo={f.expected} /></td>
              <td>{numeral(f.response_count).format("0,0")} {filterIcon("response_count", f.response_count)}</td>
              <td>{numeral(f.transaction_count).format("0,0")} {filterIcon("transaction_count", f.transaction_count)}</td>
              <td>{numeral(f.victim_count).format("0,0")} {filterIcon("victim_count", f.victim_count)}</td>
              <td title={moment(f.first_seen).format()}>{moment(f.first_seen).fromNow()}</td>
              <td title={moment(f.last_seen).format()}>{moment(f.last_seen).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={findings.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
