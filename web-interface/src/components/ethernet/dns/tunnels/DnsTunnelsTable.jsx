import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../../util/ApiRoutes";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import L4Address from "../../shared/L4Address";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import {DnsTunnelIndicators} from "./DnsTunnelIndicator";

export default function DnsTunnelsTable({findings, page, setPage, perPage, orderColumn, setOrderColumn,
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
      No DNS tunnels match the current filters and time range. A finding appears here once a client looks up many
      distinct names under one registered domain and those names look like encoded data.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>ID</th>
          <th>Client {columnSorting("client_address")}</th>
          <th>Client MAC {columnSorting("client_mac")}</th>
          <th>Domain {columnSorting("domain")}</th>
          <th>Indicators</th>
          <th>Queries {columnSorting("query_count")}</th>
          <th>Distinct Names {columnSorting("distinct_name_count")}</th>
          <th>Avg. Entropy {columnSorting("average_entropy")}</th>
          <th>Avg. Length {columnSorting("average_name_length")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {findings.findings.map((f) => {
          return (
            <tr key={f.uuid}>
              <td>
                <Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.DETAILS(f.uuid)}>
                  <FullCopyShortenedId value={f.uuid} />
                </Link>
                {f.exclusions && f.exclusions.length > 0 ?
                  <span className="text-muted ms-1"
                        title={"Matches exclusion " + f.exclusions.map((ex) => "\"" + ex.name + "\"").join(", ")
                          + ". No longer updated."}>
                    (excluded)
                  </span> : null}
              </td>
              <td>
                {f.client_address_data ?
                  <L4Address address={f.client_address_data} hidePort={true} withAssetName
                             filterElement={filterIcon("client_address", f.client_address)} />
                  : <React.Fragment>
                      <span className="machine-data">{f.client_address}</span>{' '}
                      {filterIcon("client_address", f.client_address)}
                    </React.Fragment>}
              </td>
              <td>
                {f.client_mac_data ?
                  <EthernetMacAddress addressWithContext={f.client_mac_data} withAssetLink withAssetName
                                      filterElement={filterIcon("client_mac", f.client_mac)} />
                  : f.client_mac ?
                    <React.Fragment>
                      <span className="machine-data">{f.client_mac}</span>{' '}
                      {filterIcon("client_mac", f.client_mac)}
                    </React.Fragment>
                    : <span className="text-muted">Unknown</span>}
              </td>
              <td><span className="machine-data">{f.domain}</span> {filterIcon("domain", f.domain)}</td>
              <td><DnsTunnelIndicators indicators={f.indicators} filterIcon={filterIcon} /></td>
              <td>{numeral(f.query_count).format("0,0")} {filterIcon("query_count", f.query_count)}</td>
              <td>{numeral(f.distinct_name_count).format("0,0")} {filterIcon("distinct_name_count", f.distinct_name_count)}</td>
              <td title={"Maximum: " + numeral(f.maximum_entropy).format("0.00") + " bits per character"}>
                {numeral(f.average_entropy).format("0.00")}
              </td>
              <td title={"Maximum: " + numeral(f.maximum_name_length).format("0,0") + " characters"}>
                {numeral(f.average_name_length).format("0.0")}
              </td>
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
