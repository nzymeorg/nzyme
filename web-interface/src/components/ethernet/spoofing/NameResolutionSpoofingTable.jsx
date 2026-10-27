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
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import NameResolutionProtocols from "./NameResolutionProtocol";

const addressList = (addresses) => {
  if (!addresses || addresses.length === 0) {
    return <span className="text-muted">Unknown</span>
  }

  return addresses.map((a) => <div key={a} className="text-nowrap"><span className="machine-data">{a}</span></div>);
}

export default function NameResolutionSpoofingTable({findings, page, setPage, perPage, orderColumn, setOrderColumn,
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
      No name resolution poisoning matches the current filters and time range. A finding appears here once a device
      answers LLMNR, NBT-NS or mDNS queries for more different names than a device legitimately has.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>ID</th>
          <th>Responder {columnSorting("responder_mac")}</th>
          <th>Addresses</th>
          <th>Protocols</th>
          <th>Names {columnSorting("name_count")}</th>
          <th>Responses {columnSorting("response_count")}</th>
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
                <Link to={ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.DETAILS(f.uuid)}>
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
                {f.responder_mac_data ?
                  <EthernetMacAddress addressWithContext={f.responder_mac_data} withAssetLink withAssetName
                                      filterElement={filterIcon("responder_mac", f.responder_mac)} />
                  : <span className="machine-data">{f.responder_mac}</span>}
              </td>
              <td>{addressList(f.responder_addresses)}</td>
              <td><NameResolutionProtocols protocols={f.protocols} /></td>
              <td>{numeral(f.name_count).format("0,0")} {filterIcon("name_count", f.name_count)}</td>
              <td>{numeral(f.response_count).format("0,0")} {filterIcon("response_count", f.response_count)}</td>
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
