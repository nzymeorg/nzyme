import React from "react";
import moment from "moment";
import numeral from "numeral";
import {toast} from "react-toastify";
import Dot11Service from "../../../../services/Dot11Service";
import ApiRoutes from "../../../../util/ApiRoutes";
import Paginator from "../../../misc/Paginator";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import ColumnSorting from "../../../shared/ColumnSorting";
import PivotLink from "../../../shared/PivotLink";
import Dot11MacAddress from "../../../shared/context/macs/Dot11MacAddress";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import FindingsBulkActions from "../../../ethernet/assets/identity/FindingsBulkActions";
import useRowSelection from "../../../ethernet/assets/identity/useRowSelection";
import ApprovalStatus from "./ApprovalStatus";
import accessPointsOfSsidLink from "./accessPointsOfSsidLink";

const dot11Service = new Dot11Service();

const counted = (count) => numeral(count).format("0,0") + (count === 1 ? " access point" : " access points");

const ACTIONS = {
  approve: {
    label: "Approve",
    question: "Really approve access point? You can revoke the approval at any time.",
    bulkQuestion: "Really approve the selected access points? Ignored access points are skipped.",
    done: (what) => what + " approved."
  },
  revoke: {
    label: "Revoke Approval",
    bulkLabel: "Revoke approval of",
    question: "Really revoke approval? You can re-approve it at any time.",
    bulkQuestion: "Really revoke the approval of the selected access points?",
    done: (what) => "Approval of " + what + " revoked."
  },
  ignore: {
    label: "Ignore",
    question: "Really ignore access point? You can un-ignore it at any time.",
    bulkQuestion: "Really ignore the selected access points? Approved access points are skipped.",
    done: (what) => what + " ignored."
  },
  unignore: {
    label: "Unignore",
    question: "Really unignore access point? You can ignore it again at any time.",
    bulkQuestion: "Really unignore the selected access points?",
    done: (what) => what + " unignored."
  },
  delete: {
    label: "Delete",
    question: "Really delete access point? It will reappear as unapproved access point next time Nzyme records it.",
    bulkQuestion: "Really delete the selected access points? Each will reappear as unapproved access point next " +
        "time Nzyme records it.",
    done: (what) => what + " deleted."
  }
};

export default function HiddenAccessPointsTable(props) {

  const accessPoints = props.accessPoints;
  const onChange = props.onChange;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;

  const setOrderColumn = props.setOrderColumn;
  const orderColumn = props.orderColumn;
  const setOrderDirection = props.setOrderDirection;
  const orderDirection = props.orderDirection;

  const [organizationId, tenantId] = useSelectedTenant();

  const selection = useRowSelection(accessPoints ? accessPoints.access_points : []);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const apply = (name, uuids, question) => {
    if (!confirm(question)) {
      return;
    }

    dot11Service.applyToHiddenAccessPoints(name, uuids, organizationId, tenantId, (result) => {
      const unchanged = uuids.length - result.affected;
      const rest = unchanged > 0 ? " " + counted(unchanged) + " not changed." : "";

      if (result.affected > 0) {
        toast.success(ACTIONS[name].done(counted(result.affected)) + rest);
      } else {
        toast.info("Nothing was changed.");
      }

      selection.clear();
      onChange();
    });
  }

  const actionLink = (name, accessPoint) => {
    return (
        <a href="#" onClick={(e) => { e.preventDefault(); apply(name, [accessPoint.uuid], ACTIONS[name].question); }}>
          {ACTIONS[name].label}
        </a>
    )
  }

  const approvalLink = (accessPoint) => {
    if (accessPoint.is_approved) {
      return actionLink("revoke", accessPoint);
    }

    if (accessPoint.is_ignored) {
      return <span className="text-muted" title="Ignored access points cannot be approved.">Approve</span>
    }

    return actionLink("approve", accessPoint);
  }

  const ignoreLink = (accessPoint) => {
    if (accessPoint.is_approved) {
      return <span className="text-muted" title="Approved access points cannot be ignored.">Ignore</span>
    }

    return actionLink(accessPoint.is_ignored ? "unignore" : "ignore", accessPoint);
  }

  const ssids = (names, accessPoint) => {
    if (names.length === 0) {
      return <span className="text-muted">None</span>
    }

    return names.map((ssid, i) => (
        <span key={ssid}>
          {i > 0 ? ", " : null}
          {ssid}
          <PivotLink to={accessPointsOfSsidLink(ssid, accessPoint.last_seen)}
                     title="Access points that advertise this SSID"
                     className="ms-1 small"
                     icon={true} />
        </span>
    ));
  }

  const bulkActions = Object.keys(ACTIONS).map((name) => {
    return {
      label: ACTIONS[name].bulkLabel ? ACTIONS[name].bulkLabel : ACTIONS[name].label,
      onSelected: (e) => {
        e.preventDefault();
        apply(name, selection.selected, ACTIONS[name].bulkQuestion);
      }
    }
  });

  if (!accessPoints) {
    return <GenericWidgetLoadingSpinner height={500} />
  }

  if (accessPoints.access_points.length === 0) {
    return <div className="alert alert-info mt-3 mb-0">No hidden access points found.</div>
  }

  return (
      <React.Fragment>
        <div className="d-flex align-items-center mt-3 mb-2">
          <FindingsBulkActions selectedCount={selection.selected.length}
                               actions={bulkActions}
                               noun="access point" />

          <span className="text-muted ms-2">
            {selection.selected.length > 0 ? counted(selection.selected.length) + " selected of " : null}
            {numeral(accessPoints.total).format("0,0")} total
          </span>
        </div>

        <div className="table-responsive">
          <table className="table table-sm table-hover table-striped">
            <thead>
            <tr>
              <th>
                <input className="form-check-input" type="checkbox" title="Select all rows of this page"
                       checked={selection.allSelected} onChange={selection.toggleAll} />
              </th>
              <th className="text-nowrap">BSSID {columnSorting("bssid")}</th>
              <th title="Up to 5 SSIDs of BSSIDs with a neighboring address, from the last 24 hours">Likely Same Device As</th>
              <th title="Up to 5 SSIDs this BSSID advertised itself, from the last 24 hours">Revealed SSIDs</th>
              <th className="text-nowrap">Status {columnSorting("status")}</th>
              <th className="text-nowrap">Last Seen {columnSorting("last_seen")}</th>
              <th>&nbsp;</th>
              <th>&nbsp;</th>
              <th>&nbsp;</th>
            </tr>
            </thead>
            <tbody>
            {accessPoints.access_points.map((ap) => {
              return (
                  <tr key={ap.uuid}>
                    <td>
                      <input className="form-check-input" type="checkbox"
                             checked={selection.selected.includes(ap.uuid)}
                             onChange={() => selection.toggle(ap.uuid)} />
                    </td>
                    <td className="text-nowrap">
                      <Dot11MacAddress addressWithContext={ap.bssid}
                                       href={ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(ap.bssid.address)} />
                      <PivotLink to={ApiRoutes.DOT11.NETWORKS.BSSIDS.TIMELIME(ap.bssid.address)}
                                 title="Timeline of this BSSID"
                                 className="ms-1 small"
                                 icon={true} />
                    </td>
                    <td className="text-break">{ssids(ap.sibling_ssids, ap)}</td>
                    <td className="text-break">{ssids(ap.revealed_ssids, ap)}</td>
                    <td className="text-nowrap">
                      <ApprovalStatus approved={ap.is_approved} ignored={ap.is_ignored} />
                    </td>
                    <td className="text-nowrap" title={moment(ap.last_seen).format()}>
                      {moment(ap.last_seen).fromNow()}
                    </td>
                    <td className="text-nowrap">{approvalLink(ap)}</td>
                    <td className="text-nowrap">{ignoreLink(ap)}</td>
                    <td className="text-nowrap">{actionLink("delete", ap)}</td>
                  </tr>
              )
            })}
            </tbody>
          </table>
        </div>

        <Paginator page={page} setPage={setPage} perPage={perPage} itemCount={accessPoints.total}/>
      </React.Fragment>
  )

}
