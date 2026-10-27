import React from "react";
import numeral from "numeral";
import moment from "moment";
import {toast} from "react-toastify";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import SpoofingService from "../../../services/ethernet/SpoofingService";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {DhcpOfferedValue} from "./DhcpOfferedParameters";

const spoofingService = new SpoofingService();

export default function DhcpSpoofingServersTable({servers, page, setPage, perPage, orderColumn, setOrderColumn,
                                                  orderDirection, setOrderDirection, canManage, onChange}) {

  const [organizationId, tenantId] = useSelectedTenant();

  if (!servers) {
    return <LoadingSpinner />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const setTrusted = (server, trusted) => (e) => {
    e.preventDefault();

    const question = trusted
      ? "Trust this DHCP server? It will no longer raise rogue server findings, and the configuration it hands " +
        "out right now becomes its expected configuration."
      : "Untrust this DHCP server? Its next answer to a client raises a rogue server finding.";

    if (!confirm(question)) {
      return;
    }

    spoofingService.setDhcpServerTrusted(server.uuid, organizationId, tenantId, trusted, () => {
      toast.success(trusted ? "Server is now trusted." : "Server is no longer trusted.");
      onChange();
    });
  }

  const acceptConfiguration = (server) => (e) => {
    e.preventDefault();

    if (!confirm("Accept the configuration this server hands out right now as its expected configuration?")) {
      return;
    }

    spoofingService.acceptDhcpServerConfiguration(server.uuid, organizationId, tenantId, () => {
      toast.success("Configuration accepted.");
      onChange();
    });
  }

  const forget = (server) => (e) => {
    e.preventDefault();

    if (!confirm("Forget this DHCP server? It is learned again if it answers clients, and trusted only if that " +
        "still falls inside the learning period. Existing findings are kept.")) {
      return;
    }

    spoofingService.deleteDhcpServer(server.uuid, organizationId, tenantId, () => {
      toast.success("Server forgotten.");
      onChange();
    });
  }

  if (servers.servers.length === 0) {
    return <div className="alert alert-info mb-0">
      No DHCP server has been seen yet. Servers appear here once a tap records a DHCP transaction that a server
      answered.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Server {columnSorting("address")}</th>
          <th>MAC Address {columnSorting("mac")}</th>
          <th>Status {columnSorting("trusted")}</th>
          <th>Gateway</th>
          <th>DNS Servers</th>
          <th>Domain</th>
          <th>Responses {columnSorting("response_count")}</th>
          <th>Transactions {columnSorting("transaction_count")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
          {canManage ? <th>&nbsp;</th> : null}
        </tr>
        </thead>
        <tbody>
        {servers.servers.map((s) => {
          return (
            <tr key={s.uuid}>
              <td>
                {s.address_data ?
                  <L4Address address={s.address_data} hidePort={true} withAssetName />
                  : <span className="machine-data">{s.address}</span>}
                {s.server_identifier && s.server_identifier !== s.address ?
                  <span className="text-muted" title="Server identifier (DHCP option 54). Differs from the address when the server answers through a relay.">
                    {' '}(ID <span className="machine-data">{s.server_identifier}</span>)
                  </span> : null}
              </td>
              <td>
                {s.mac_data ?
                  <EthernetMacAddress addressWithContext={s.mac_data} withAssetLink withAssetName />
                  : <span className="machine-data">{s.mac}</span>}
              </td>
              <td>
                {s.trusted ?
                  <span className="classification classification-benign"
                        title={s.trust_source === "USER" ? "Trusted by a user" : "Trusted because it was seen during the learning period"}>
                    Trusted
                  </span>
                  : <span className="classification classification-hostile" title="Answers from this server are rogue server findings">Untrusted</span>}
                {s.trusted && !s.latest_matches_baseline ?
                  <span className="classification classification-suspicious ms-1"
                        title="The configuration it hands out now differs from the expected configuration">
                    Config Change
                  </span> : null}
              </td>
              <td><DhcpOfferedValue parameters={s.latest} field="routers" compareTo={s.trusted ? s.baseline : undefined} /></td>
              <td><DhcpOfferedValue parameters={s.latest} field="dns_servers" compareTo={s.trusted ? s.baseline : undefined} /></td>
              <td><DhcpOfferedValue parameters={s.latest} field="domain_name" compareTo={s.trusted ? s.baseline : undefined} /></td>
              <td title={numeral(s.offer_count).format("0,0") + " offers, " + numeral(s.ack_count).format("0,0") + " acknowledgements"}>
                {numeral(s.response_count).format("0,0")}
              </td>
              <td>{numeral(s.transaction_count).format("0,0")}</td>
              <td title={moment(s.first_seen).format()}>{moment(s.first_seen).fromNow()}</td>
              <td title={moment(s.last_seen).format()}>{moment(s.last_seen).fromNow()}</td>
              {canManage ?
                <td className="text-end text-nowrap">
                  {s.trusted ?
                    <a href="#" onClick={setTrusted(s, false)}>Untrust</a>
                    : <a href="#" onClick={setTrusted(s, true)}>Trust</a>}
                  {s.trusted && !s.latest_matches_baseline ?
                    <React.Fragment>
                      {' '}&middot;{' '}
                      <a href="#" onClick={acceptConfiguration(s)}
                         title="Take the current configuration as the expected configuration">Accept Config</a>
                    </React.Fragment> : null}
                  {' '}&middot;{' '}
                  <a href="#" onClick={forget(s)} title="Forget this server">Forget</a>
                </td> : null}
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={servers.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
