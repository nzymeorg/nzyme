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
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import {confidenceClass, linkKindTitle, linkStatus} from "./AssetIdentityHelpers";

export default function AssetLinksTable({links, page, setPage, perPage, orderColumn, setOrderColumn,
                                         orderDirection, setOrderDirection, filterFields, setFilters}) {

  if (!links) {
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

  if (links.links.length === 0) {
    return <div className="alert alert-info mb-0">
      No MAC address changes match the current filters and time range. A link appears here once a new asset looks
      like a device that recently went silent under another MAC address.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>ID</th>
          <th>Previous Address {columnSorting("predecessor_mac")}</th>
          <th>New Address {columnSorting("successor_mac")}</th>
          <th>Kind {columnSorting("kind")}</th>
          <th>Confidence {columnSorting("confidence")}</th>
          <th>Status {columnSorting("status")}</th>
          <th>New Address First Seen {columnSorting("successor_first_seen")}</th>
          <th>Detected {columnSorting("detected_at")}</th>
        </tr>
        </thead>
        <tbody>
        {links.links.map((l) => {
          return (
            <tr key={l.uuid}>
              <td>
                <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.LINK_DETAILS(l.uuid)}>
                  <FullCopyShortenedId value={l.uuid} />
                </Link>
              </td>
              <td>
                {l.predecessor_mac_data ?
                  <EthernetMacAddress addressWithContext={l.predecessor_mac_data} withAssetLink withAssetName
                                      filterElement={filterIcon("predecessor_mac", l.predecessor_mac)} />
                  : <span className="machine-data">{l.predecessor_mac}</span>}
              </td>
              <td>
                {l.successor_mac_data ?
                  <EthernetMacAddress addressWithContext={l.successor_mac_data} withAssetLink withAssetName
                                      filterElement={filterIcon("successor_mac", l.successor_mac)} />
                  : <span className="machine-data">{l.successor_mac}</span>}
              </td>
              <td>{linkKindTitle(l.kind)} {filterIcon("kind", l.kind)}</td>
              <td>
                <span className={confidenceClass(l.confidence)}>{numeral(l.confidence).format("0,0")}</span>{' '}
                {filterIcon("confidence", l.confidence)}
              </td>
              <td>{linkStatus(l.status)} {filterIcon("status", l.status)}</td>
              <td title={moment(l.successor_first_seen).format()}>{moment(l.successor_first_seen).fromNow()}</td>
              <td title={moment(l.detected_at).format()}>{moment(l.detected_at).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={links.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
