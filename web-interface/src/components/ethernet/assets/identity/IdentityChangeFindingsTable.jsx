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
import FindingActiveIndicator from "./FindingActiveIndicator";
import {changeSignalTitle} from "./AssetIdentityHelpers";

const assetsService = new AssetsService();

export default function IdentityChangeFindingsTable({findings, page, setPage, perPage, orderColumn, setOrderColumn,
                                                     orderDirection, setOrderDirection, filterFields, setFilters, bulk}) {

  const user = useContext(UserContext);
  const rows = findings ? findings.findings : [];
  const selection = useRowSelection(rows);
  const canManage = bulk && userHasPermission(user, "ethernet_assets_manage");

  const done = (message) => () => {
    selection.clear();
    toast.success(message);
    bulk.onChanged();
  };

  const deleteSelected = (e) => {
    e.preventDefault();
    if (!confirm("Really delete the selected findings?")) {
      return;
    }
    assetsService.deleteIdentityChangeFindings(selection.selected, bulk.organizationId, bulk.tenantId, done("Selected findings deleted."));
  };

  const deleteMatching = (e) => {
    e.preventDefault();
    if (!confirm("Really delete all findings that match the current filters and time range? This also affects "
        + "findings on other pages of the list and cannot be undone.")) {
      return;
    }
    assetsService.deleteFilteredIdentityChangeFindings(bulk.organizationId, bulk.tenantId, bulk.filters, bulk.timeRange,
      done("Matching findings deleted."));
  };

  if (!findings) {
    return <LoadingSpinner />
  }

  const total = findings.total;

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
      No identity changes match the current filters and time range. A finding appears here once an established
      asset presents enough changed identity attributes inside the detection window to reach the score threshold.
    </div>
  }

  return (
    <React.Fragment>
      {canManage ?
        <div className="mb-2">
          <FindingsBulkActions selectedCount={selection.selected.length} matchingCount={total} noun="finding" actions={[{label: "Delete", onSelected: deleteSelected, onMatching: deleteMatching}]} />
        </div> : null}

      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          {canManage ? <th><input className="form-check-input" type="checkbox" checked={selection.allSelected}
                                  onChange={selection.toggleAll} /></th> : null}
          <th>ID</th>
          <th>MAC Address {columnSorting("mac")}</th>
          <th>Score {columnSorting("score")}</th>
          <th>Changes</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {findings.findings.map((f) => {
          return (
            <tr key={f.uuid}>
              {canManage ? <td><input className="form-check-input" type="checkbox" checked={selection.selected.includes(f.uuid)}
                                      onChange={() => selection.toggle(f.uuid)} /></td> : null}
              <td>
                <FindingActiveIndicator active={f.recent} />{' '}
                <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.CHANGE_DETAILS(f.uuid)}>
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
                {f.mac_data ?
                  <EthernetMacAddress addressWithContext={f.mac_data} withAssetLink withAssetName
                                      filterElement={filterIcon("mac", f.mac)} />
                  : <span className="machine-data">{f.mac}</span>}
              </td>
              <td>
                <span title={"Threshold " + f.threshold}>{numeral(f.score).format("0,0")}</span>{' '}
                {filterIcon("score", f.score)}
              </td>
              <td>{f.changes.map((c) => changeSignalTitle(c.signal)).join(", ")}</td>
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
