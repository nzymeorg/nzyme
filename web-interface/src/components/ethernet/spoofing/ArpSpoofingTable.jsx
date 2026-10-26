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

export default function ArpSpoofingTable({findings, page, setPage, perPage, orderColumn, setOrderColumn,
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
      No ARP poisoning matches the current filters and time range. A finding appears here once a device claims an
      address with its own MAC address while the legitimate owner of the address is still claiming it too.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>ID</th>
          <th>Address {columnSorting("address")}</th>
          <th>Spoofer {columnSorting("spoofer_mac")}</th>
          <th>Owner {columnSorting("owner_mac")}</th>
          <th>Claims {columnSorting("claim_count")}</th>
          <th>Unsolicited {columnSorting("unsolicited_count")}</th>
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
                <Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.DETAILS(f.uuid)}>
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
                {f.address_data ?
                  <L4Address address={f.address_data} hidePort={true} withAssetName
                             filterElement={filterIcon("address", f.address)} />
                  : <span className="machine-data">{f.address}</span>}
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
              <td>{numeral(f.claim_count).format("0,0")} {filterIcon("claim_count", f.claim_count)}</td>
              <td>{numeral(f.unsolicited_count).format("0,0")} {filterIcon("unsolicited_count", f.unsolicited_count)}</td>
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
