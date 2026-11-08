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
import TcpFingerprint from "./TcpFingerprint";
import FindingActiveIndicator from "./FindingActiveIndicator";

const assetsService = new AssetsService();

export default function DuplicateMacFindingsTable({findings, page, setPage, perPage, orderColumn, setOrderColumn,
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
    assetsService.deleteDuplicateMacFindings(selection.selected, bulk.organizationId, bulk.tenantId, done("Selected findings deleted."));
  };

  const deleteMatching = (e) => {
    e.preventDefault();
    if (!confirm("Really delete all findings that match the current filters and time range? This also affects "
        + "findings on other pages of the list and cannot be undone.")) {
      return;
    }
    assetsService.deleteFilteredDuplicateMacFindings(bulk.organizationId, bulk.tenantId, bulk.filters, bulk.timeRange,
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
      No duplicate MAC addresses match the current filters and time range. A finding appears here once two
      different devices use the same MAC address at the same time.
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
          <th>Stacks {columnSorting("stack_count")}</th>
          <th>Fingerprints</th>
          <th>Sessions {columnSorting("session_count")}</th>
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
                <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATE_DETAILS(f.uuid)}>
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
              <td>{numeral(f.stack_count).format("0,0")} {filterIcon("stack_count", f.stack_count)}</td>
              <td>
                {f.stacks.slice(0, 3).map((s, i) => <React.Fragment key={s.fingerprint}>{i > 0 ? <br /> : null}<TcpFingerprint value={s.fingerprint} id={s.fingerprint_id} compact /></React.Fragment>)}
                {f.stack_count > 3 ?
                  <React.Fragment>
                    <br />
                    <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATE_DETAILS(f.uuid)} className="text-muted small">
                      +{numeral(f.stack_count - 3).format("0,0")} more
                    </Link>
                  </React.Fragment> : null}
              </td>
              <td>{numeral(f.session_count).format("0,0")} {filterIcon("session_count", f.session_count)}</td>
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
