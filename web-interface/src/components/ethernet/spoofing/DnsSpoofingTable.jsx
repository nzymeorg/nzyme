import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../util/ApiRoutes";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import L4Address from "../shared/L4Address";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";

export default function DnsSpoofingTable({findings, page, setPage, perPage, orderColumn, setOrderColumn,
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
      No DNS spoofing matches the current filters and time range. A finding appears here once a device answers
      queries sent to a resolver with forged replies while the resolver is still answering.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>ID</th>
          <th>Resolver {columnSorting("server_address")}</th>
          <th>Spoofer {columnSorting("spoofer_mac")}</th>
          <th>Owner {columnSorting("owner_mac")}</th>
          <th>Forged Replies {columnSorting("forged_count")}</th>
          <th>Races Won {columnSorting("won_count")}</th>
          <th>Names {columnSorting("name_count")}</th>
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
                <Link to={ApiRoutes.ETHERNET.SPOOFING.DNS.DETAILS(f.uuid)}>
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
                {f.server_address_data ?
                  <L4Address address={f.server_address_data} hidePort={true} withAssetName
                             filterElement={filterIcon("server_address", f.server_address)} />
                  : <React.Fragment>
                      <span className="machine-data">{f.server_address}</span>{' '}
                      {filterIcon("server_address", f.server_address)}
                    </React.Fragment>}
              </td>
              <td>
                {f.spoofer_mac_data ?
                  <EthernetMacAddress addressWithContext={f.spoofer_mac_data} withAssetLink withAssetName
                                      filterElement={filterIcon("spoofer_mac", f.spoofer_mac)} />
                  : <span className="machine-data">{f.spoofer_mac}</span>}
              </td>
              <td>
                {f.owner_mac_data ?
                  <EthernetMacAddress addressWithContext={f.owner_mac_data} withAssetLink withAssetName
                                      filterElement={filterIcon("owner_mac", f.owner_mac)} />
                  : <span className="machine-data">{f.owner_mac}</span>}
              </td>
              <td>{numeral(f.forged_count).format("0,0")} {filterIcon("forged_count", f.forged_count)}</td>
              <td title={numeral(f.unanswered_count).format("0,0") + " forged replies were never answered by the owner"}>
                {numeral(f.won_count).format("0,0")} {filterIcon("won_count", f.won_count)}
              </td>
              <td>{numeral(f.name_count).format("0,0")} {filterIcon("name_count", f.name_count)}</td>
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
