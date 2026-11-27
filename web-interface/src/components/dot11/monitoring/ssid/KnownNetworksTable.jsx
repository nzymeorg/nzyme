import React  from "react";
import Paginator from "../../../misc/Paginator";
import moment from "moment";
import Dot11Service from "../../../../services/Dot11Service";
import {toast} from "react-toastify";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import ColumnSorting from "../../../shared/ColumnSorting";
import numeral from "numeral";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ApprovalStatus from "./ApprovalStatus";
import accessPointsOfSsidLink from "./accessPointsOfSsidLink";
import FindingsBulkActions from "../../../ethernet/assets/identity/FindingsBulkActions";
import useRowSelection from "../../../ethernet/assets/identity/useRowSelection";
import PivotLink from "../../../shared/PivotLink";

const dot11Service = new Dot11Service();

const counted = (count) => numeral(count).format("0,0") + (count === 1 ? " network" : " networks");

const BULK_ACTIONS = [
    {
        action: "approve",
        label: "Approve",
        question: "Really approve the selected networks? Ignored networks are skipped.",
        done: (what) => what + " approved."
    },
    {
        action: "revoke",
        label: "Revoke approval of",
        question: "Really revoke the approval of the selected networks?",
        done: (what) => "Approval of " + what + " revoked."
    },
    {
        action: "ignore",
        label: "Ignore",
        question: "Really ignore the selected networks? Approved networks are skipped.",
        done: (what) => what + " ignored."
    },
    {
        action: "unignore",
        label: "Unignore",
        question: "Really unignore the selected networks?",
        done: (what) => what + " unignored."
    },
    {
        action: "delete",
        label: "Delete",
        question: "Really delete the selected networks? Each will reappear as unapproved network next time Nzyme " +
            "records it.",
        done: (what) => what + " deleted."
    }
];

export default function KnownNetworksTable(props) {

    const networks = props.networks;
    const onChange = props.onChange;
    const page = props.page;
    const setPage = props.setPage;
    const perPage = props.perPage;

    const setOrderColumn = props.setOrderColumn;
    const orderColumn = props.orderColumn;
    const setOrderDirection = props.setOrderDirection;
    const orderDirection = props.orderDirection;

    const [organizationId, tenantId] = useSelectedTenant();

    const selection = useRowSelection(networks ? networks.networks : []);

    const columnSorting = (columnName) => {
        return <ColumnSorting thisColumn={columnName}
                              orderColumn={orderColumn}
                              setOrderColumn={setOrderColumn}
                              orderDirection={orderDirection}
                              setOrderDirection={setOrderDirection} />
    }

    const changeStatusLink = (network) => {
        if (network.is_approved) {
            return <a href="#" onClick={(e) => onRevoke(e, network)}>Revoke Approval</a>
        } else {
            if (network.is_ignored) {
                return <span className="text-muted" title="Ignored networks cannot be approved.">Approve</span>
            } else {
                return <a href="#" onClick={(e) => onApprove(e, network)}>Approve</a>
            }
        }
    }

    const ignoreLink = (network) => {
        if (network.is_approved) {
            return <span className="text-muted" title="Approved networks cannot be ignored.">Ignore</span>
        } else {
            if (network.is_ignored) {
                return <a href="#" onClick={(e) => onUnignore(e, network)}>Unignore</a>
            } else {
                return <a href="#" onClick={(e) => onIgnore(e, network)}>Ignore</a>
            }
        }
    }

    const onApprove = (e, network) => {
        e.preventDefault();

        if (!confirm("Really approve network? You can revoke the approval at any time.")) {
            return;
        }

        dot11Service.approveKnownNetwork(network.uuid, network.organization_id, network.tenant_id, () => {
            toast.success('Known network approved.');
            onChange();
        });
    }

    const onRevoke = (e, network) => {
        e.preventDefault();

        if (!confirm("Really revoke approval? You can re-approve it at any time.")) {
            return;
        }

        dot11Service.revokeKnownNetwork(network.uuid, network.organization_id, network.tenant_id, () => {
            toast.success('Known network approval revoked.');
            onChange();
        });
    }


    const onIgnore = (e, network) => {
        e.preventDefault();

        if (!confirm("Really ignore network? You can un-ignore it at any time.")) {
            return;
        }

        dot11Service.ignoreKnownNetwork(network.uuid, network.organization_id, network.tenant_id, () => {
            toast.success('Known network ignored.');
            onChange();
        });
    }

    const onUnignore = (e, network) => {
        e.preventDefault();

        if (!confirm("Really unignore network? You can ignore it again at any time.")) {
            return;
        }

        dot11Service.unignoreKnownNetwork(network.uuid, network.organization_id, network.tenant_id, () => {
            toast.success('Known network unignored.');
            onChange();
        });
    }

    const onDelete = (e, network) => {
        e.preventDefault();

        if (!confirm("Really delete known network? It will reappear as unapproved network " +
            "next time Nzyme records it.")) {
            return;
        }

        dot11Service.deleteKnownNetwork(network.uuid, network.organization_id, network.tenant_id, () => {
            toast.success('Known network deleted.');
            onChange();
        });
    }

    const bulkActions = BULK_ACTIONS.map((bulk) => {
        return {
            label: bulk.label,
            onSelected: (e) => {
                e.preventDefault();

                if (!confirm(bulk.question)) {
                    return;
                }

                const selected = selection.selected;

                dot11Service.applyToKnownNetworks(bulk.action, selected, organizationId, tenantId, (result) => {
                    const unchanged = selected.length - result.affected;
                    const rest = unchanged > 0 ? " " + counted(unchanged) + " not changed." : "";

                    if (result.affected > 0) {
                        toast.success(bulk.done(counted(result.affected)) + rest);
                    } else {
                        toast.info("Nothing was changed.");
                    }

                    selection.clear();
                    onChange();
                });
            }
        }
    });

    if (!networks) {
        return <GenericWidgetLoadingSpinner height={700} />
    }

    if (networks.networks.length === 0) {
        return <div className="alert alert-warning mt-3">
            No known networks discovered yet. Make sure that SSID monitoring is enabled and that networks are in range.
            Note that it will take at least <em>[24-hour minimum dwell time]</em> minutes to discover known networks
            after SSID monitoring is enabled.
        </div>
    }

    return (
        <React.Fragment>
            <p className="mt-1">
                <strong>Total:</strong> {numeral(networks.total).format("0,0")}
            </p>

            { onChange ?
                <div className="d-flex align-items-center mb-2">
                    <FindingsBulkActions selectedCount={selection.selected.length}
                                         actions={bulkActions}
                                         noun="network" />

                    {selection.selected.length > 0 ?
                        <span className="text-muted ms-2">{counted(selection.selected.length)} selected</span> : null}
                </div> : null }

            <table className="table table-sm table-hover table-striped">
                <thead>
                <tr>
                    { onChange ?
                        <th>
                            <input className="form-check-input" type="checkbox" title="Select all rows of this page"
                                   checked={selection.allSelected} onChange={selection.toggleAll} />
                        </th> : null }
                    <th>SSID {columnSorting("ssid")}</th>
                    <th>Status {columnSorting("status")}</th>
                    <th>Last Seen {columnSorting("last_seen")}</th>
                    { onChange ? <>
                        <th>&nbsp;</th>
                        <th>&nbsp;</th>
                        <th>&nbsp;</th>
                    </> : null }
                </tr>
                </thead>
                <tbody>
                {networks.networks.map((net) => {
                    return (
                        <tr key={net.uuid}>
                            { onChange ?
                                <td>
                                    <input className="form-check-input" type="checkbox"
                                           checked={selection.selected.includes(net.uuid)}
                                           onChange={() => selection.toggle(net.uuid)} />
                                </td> : null }
                            <td>
                                {net.ssid}
                                { onChange ?
                                    <PivotLink to={accessPointsOfSsidLink(net.ssid, net.last_seen)}
                                               title="Access points that advertise this SSID"
                                               className="ms-2 small"
                                               icon={true} /> : null }
                            </td>
                            <td><ApprovalStatus approved={net.is_approved} ignored={net.is_ignored} /></td>
                            <td title={moment(net.last_seen).format()}>{moment(net.last_seen).fromNow()}</td>
                            { onChange ? <>
                            <td>{changeStatusLink(net)}</td>
                            <td>{ignoreLink(net)}</td>
                            <td>
                                <a href="#" onClick={(e) => onDelete(e, net)}>
                                    Delete
                                </a>
                            </td></> : null }
                        </tr>
                    )
                })}
                </tbody>
            </table>

            <Paginator page={page} setPage={setPage} perPage={perPage} itemCount={networks.total}/>
        </React.Fragment>
)

}