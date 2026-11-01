import React from "react";
import Dot11MacAddress from "../../../shared/context/macs/Dot11MacAddress";
import ApiRoutes from "../../../../util/ApiRoutes";
import moment from "moment/moment";
import {toast} from "react-toastify";
import Dot11Service from "../../../../services/Dot11Service";

const dot11Service = new Dot11Service();

export default function MonitoredClientsTable(props) {

  const clients = props.clients;
  const monitoredNetwork = props.monitoredNetwork;
  const onChange = props.onChange;

  if (clients.length === 0) {
    return <div className="alert alert-warning mt-3 mb-0">
      No connected clients discovered yet.
    </div>
  }

  const status = (client) => {
    if (client.is_approved) {
      return (
        <span>
          <i className="fa fa-check text-success"/> Approved
        </span>
      )
    } else {
      if (client.is_ignored) {
        return (
          <span>
            <i className="fa fa-info-circle text-primary" /> Ignored
          </span>
        )
      } else {
        return (
          <span>
            <i className="fa fa-warning text-warning"/> Not Approved
          </span>
        )
      }
    }
  }

  const changeStatusLink = (client) => {
    if (client.is_approved) {
      return <a href="#" onClick={() => onRevoke(client)}>Revoke Approval</a>
    } else {
      if (client.is_ignored) {
        return <span className="text-muted" title="Ignored clients cannot be approved.">Approve</span>
      } else {
        return <a href="#" onClick={() => onApprove(client)}>Approve</a>
      }
    }
  }

  const onApprove = (client) => {
    if (!confirm("Really approve client? You can revoke the approval at any time.")) {
      return;
    }

    dot11Service.approveMonitoredClient(monitoredNetwork.uuid, client.uuid, () => {
      toast.success('Known client approved.');
      onChange();
    });
  }

  const onRevoke = (client) => {
    if (!confirm("Really revoke approval? You can re-approve it at any time.")) {
      return;
    }

    dot11Service.revokedMonitoredClient(monitoredNetwork.uuid, client.uuid, () => {
      toast.success('Known client approval revoked.');
      onChange();
    });
  }

  const ignoreLink = (client) => {
    if (client.is_approved) {
      return <span className="text-muted" title="Approved clients cannot be ignored.">Ignore</span>
    } else {
      if (client.is_ignored) {
        return <a href="#" onClick={() => onUnignore(client)}>Unignore</a>
      } else {
        return <a href="#" onClick={() => onIgnore(client)}>Ignore</a>
      }
    }
  }

  const onIgnore = (client) => {
    if (!confirm("Really ignore client? You can un-ignore it at any time.")) {
      return;
    }

    dot11Service.ignoreMonitoredClient(monitoredNetwork.uuid, client.uuid, () => {
      toast.success('Known client ignored.');
      onChange();
    });
  }

  const onUnignore = (client) => {
    if (!confirm("Really un-ignore client? You can ignore it again at any time.")) {
      return;
    }

    dot11Service.unignoreMonitoredClient(monitoredNetwork.uuid, client.uuid, () => {
      toast.success('Known client un-ignored.');
      onChange();
    });
  }

  const onDelete = (e, client) => {
    e.preventDefault();

    if (!confirm("Really delete client? It will reappear as unapproved client " +
      "next time Nzyme records it.")) {
      return;
    }

    dot11Service.deleteMonitoredClient(monitoredNetwork.uuid, client.uuid, () => {
      toast.success('Known client deleted.');
      onChange();
    });
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>MAC Address</th>
          <th>OUI</th>
          <th>Status</th>
          <th>Last Seen</th>
          <th>&nbsp;</th>
          <th>&nbsp;</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {clients.map((client, i) => {
          return (
            <tr key={i}>
              <td>
                <Dot11MacAddress addressWithContext={client.mac}
                                 href={ApiRoutes.DOT11.CLIENTS.DETAILS(client.mac.address)}/>
              </td>
              <td>{client.mac.oui ? client.mac.oui : <span className="text-muted">Unknown</span>}</td>
              <td>{status(client)}</td>
              <td title={moment(client.last_seen).format()}>{moment(client.last_seen).fromNow()}</td>
              <td>{changeStatusLink(client)}</td>
              <td>{ignoreLink(client)}</td>
              <td>
              <a href="#" onClick={(e) => {onDelete(e, client)}}>
                  Delete
                </a>
              </td>
            </tr>
          )
        })}
        </tbody>
      </table>
    </React.Fragment>
  )

}