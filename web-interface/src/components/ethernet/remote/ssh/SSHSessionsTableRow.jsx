import React from "react";
import L4Address from "../../shared/L4Address";
import GenericConnectionStatus from "../../shared/GenericConnectionStatus";
import numeral from "numeral";
import {formatDurationMs} from "../../../../util/Tools";
import moment from "moment/moment";
import SSHVersion from "./SSHVersion";
import SSHSessionKey from "../../shared/SSHSessionKey";
import {SSH_FILTER_FIELDS} from "./SSHFilterFields";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import FullCopy from "../../../shared/FullCopy";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";

export default function SSHSessionsTableRow(props) {

  const session = props.session;
  const setFilters = props.setFilters;

  const macFilter = (address, fieldName) => {
    if (!address) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={SSH_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  return (
      <tr>
        <td>
          <SSHSessionKey sessionKey={session.tcp_session_key} />

          <FilterValueIcon setFilters={setFilters}
                           fields={SSH_FILTER_FIELDS}
                           field="session_key"
                           value={session.tcp_session_key} />
        </td>
        <td>
          <L4Address address={session.client}
                     hidePort={true}
                     filterElement={session.client ? <FilterValueIcon setFilters={setFilters}
                                                                      fields={SSH_FILTER_FIELDS}
                                                                      field="client_address"
                                                                      value={session.client.address} /> : null } />
        </td>
        <td>
          <InternalAddressOnlyWrapper
            address={session.client}
            inner={session.client ? <EthernetMacAddress addressWithContext={session.client.mac}
                                       filterElement={macFilter(session.client.mac, "client_mac")}
                                       withAssetLink withAssetName /> : null} />
        </td>
        <td className="hide-narrow">
          <SSHVersion version={session.client_version} />

          <FilterValueIcon setFilters={setFilters}
                           fields={SSH_FILTER_FIELDS}
                           field="client_version_software"
                           value={session.client_version.software} />
        </td>
        <td>
          <L4Address address={session.server}
                     filterElement={session.server ? <FilterValueIcon setFilters={setFilters}
                                                                      fields={SSH_FILTER_FIELDS}
                                                                      field="server_address"
                                                                      value={session.server.address} /> : null}/>
        </td>
        <td>
          <InternalAddressOnlyWrapper
            address={session.server}
            inner={session.server ? <EthernetMacAddress addressWithContext={session.server.mac}
                                                        filterElement={macFilter(session.server.mac, "server_mac")}
                                                        withAssetLink withAssetName /> : null} />
        </td>
        <td className="hide-narrow">
          <SSHVersion version={session.server_version} />

          <FilterValueIcon setFilters={setFilters}
                           fields={SSH_FILTER_FIELDS}
                           field="server_version_software"
                           value={session.server_version.software} />
        </td>
        <td>
          <GenericConnectionStatus status={session.connection_status}/>
          <FilterValueIcon setFilters={setFilters}
                           fields={SSH_FILTER_FIELDS}
                           field="connection_status"
                           value={session.connection_status} />
        </td>
        <td>
          <FullCopy shortValue={numeral(session.tunneled_bytes).format("0,0b")} fullValue={session.tunneled_bytes} />

          <FilterValueIcon setFilters={setFilters}
                           fields={SSH_FILTER_FIELDS}
                           field="tunneled_bytes"
                           value={session.tunneled_bytes} />
        </td>
        <td>
          <FullCopy shortValue={formatDurationMs(session.duration_ms)} fullValue={session.duration_ms} />

          <FilterValueIcon setFilters={setFilters}
                           fields={SSH_FILTER_FIELDS}
                           field="duration"
                           value={session.duration_ms} />
        </td>
        <td>{moment(session.established_at).format()}</td>
      </tr>
  )
}