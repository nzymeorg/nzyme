import React, {useContext} from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import {toast} from "react-toastify";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../../util/ApiRoutes";
import {userHasPermission} from "../../../../util/Tools";
import {UserContext} from "../../../../App";
import AssetsService from "../../../../services/ethernet/AssetsService";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import FindingsBulkActions from "./FindingsBulkActions";
import useRowSelection from "./useRowSelection";
import {confidenceClass, linkKindTitle, linkStatus} from "./AssetIdentityHelpers";

const assetsService = new AssetsService();

export default function AssetLinksTable({links, page, setPage, perPage, orderColumn, setOrderColumn,
                                         orderDirection, setOrderDirection, filterFields, setFilters, bulk}) {

  const user = useContext(UserContext);
  const rows = links ? links.links : [];
  const selection = useRowSelection(rows);
  const canManage = bulk && userHasPermission(user, "ethernet_assets_manage");

  const done = (message) => () => {
    selection.clear();
    toast.success(message);
    bulk.onChanged();
  };

  const deleteSelected = (e) => {
    e.preventDefault();
    if (!confirm("Really delete the selected links?")) {
      return;
    }
    assetsService.deleteAssetLinks(selection.selected, bulk.organizationId, bulk.tenantId, done("Selected links deleted."));
  };

  const deleteMatching = (e) => {
    e.preventDefault();
    if (!confirm("Really delete all links that match the current filters and time range? This also affects "
        + "links on other pages of the list and cannot be undone.")) {
      return;
    }
    assetsService.deleteFilteredAssetLinks(bulk.organizationId, bulk.tenantId, bulk.filters, bulk.timeRange,
      done("Matching links deleted."));
  };

  const statusSelected = (status, verb) => (e) => {
    e.preventDefault();
    assetsService.setAssetLinksStatus(selection.selected, bulk.organizationId, bulk.tenantId, status,
      done("Selected links " + verb + "."));
  };

  const statusMatching = (status, verb) => (e) => {
    e.preventDefault();
    if (!confirm("Really mark all links that match the current filters and time range as " + verb + "? This also "
        + "affects links on other pages of the list.")) {
      return;
    }
    assetsService.setFilteredAssetLinksStatus(bulk.organizationId, bulk.tenantId, bulk.filters, bulk.timeRange, status,
      done("Matching links " + verb + "."));
  };

  const actions = [
    {label: "Confirm", onSelected: statusSelected("CONFIRMED", "confirmed"), onMatching: statusMatching("CONFIRMED", "confirmed")},
    {label: "Dismiss", onSelected: statusSelected("DISMISSED", "dismissed"), onMatching: statusMatching("DISMISSED", "dismissed")},
    {label: "Reopen", onSelected: statusSelected("OPEN", "reopened")},
    {label: "Delete", onSelected: deleteSelected, onMatching: deleteMatching}
  ];

  if (!links) {
    return <LoadingSpinner />
  }

  const total = links.total;

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
      {canManage ?
        <div className="mb-2">
          <FindingsBulkActions selectedCount={selection.selected.length} matchingCount={total} noun="link" actions={actions} />
        </div> : null}

      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          {canManage ? <th><input className="form-check-input" type="checkbox" checked={selection.allSelected}
                                  onChange={selection.toggleAll} /></th> : null}
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
              {canManage ? <td><input className="form-check-input" type="checkbox" checked={selection.selected.includes(l.uuid)}
                                      onChange={() => selection.toggle(l.uuid)} /></td> : null}
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
