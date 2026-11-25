import React from "react";

import numeral from "numeral";
import moment from "moment";
import L4Address from "../../shared/L4Address";
import {formatDurationMs} from "../../../../util/Tools";
import GenericConnectionStatus from "../../shared/GenericConnectionStatus";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import Ipv6TransitionTunnelId from "./Ipv6TransitionTunnelId";
import {IPV6_TRANSITION_FILTER_FIELDS} from "./Ipv6TransitionFilterFields";
import FullCopy from "../../../shared/FullCopy";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import Ipv6TransitionTunnelType from "./Ipv6TransitionTunnelType";

export default function Ipv6TransitionTunnelsTableRow(props) {

  const tunnel = props.tunnel;
  const setFilters = props.setFilters;

  const macFilter = (address, fieldName) => {
    if (!address || !setFilters) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={IPV6_TRANSITION_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  const filter = (field, value) => {
    if (!setFilters) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={IPV6_TRANSITION_FILTER_FIELDS}
                            field={field}
                            value={value} />
  }

  return (
      <tr>
        <td>
          <Ipv6TransitionTunnelId tunnelId={tunnel.session_key} />
          {filter("session_key", tunnel.session_key)}
        </td>
        <td>
          <L4Address address={tunnel.client}
                     filterElement={tunnel.client ? filter("client_address", tunnel.client.address) : null} />
        </td>
        <td>
          <InternalAddressOnlyWrapper
            address={tunnel.client}
            inner={tunnel.client ? <EthernetMacAddress addressWithContext={tunnel.client.mac}
                                                       filterElement={macFilter(tunnel.client.mac, "client_mac")}
                                                       withAssetLink withAssetName /> : null} />
        </td>
        <td>
          <L4Address address={tunnel.server}
                     filterElement={tunnel.server ? filter("server_address", tunnel.server.address) : null} />
        </td>
        <td>
          <InternalAddressOnlyWrapper
            address={tunnel.server}
            inner={tunnel.server ? <EthernetMacAddress addressWithContext={tunnel.server.mac}
                                                       filterElement={macFilter(tunnel.server.mac, "server_mac")}
                                                       withAssetLink withAssetName /> : null} />
        </td>
        <td>
          <Ipv6TransitionTunnelType type={tunnel.tunnel_type} />
          {filter("type", tunnel.tunnel_type)}
        </td>
        <td>
          <GenericConnectionStatus status={tunnel.connection_status} />
          {filter("status", tunnel.connection_status)}
        </td>
        <td>
          <FullCopy shortValue={numeral(tunnel.tunneled_bytes).format("0,0b")} fullValue={tunnel.tunneled_bytes} />
          {filter("tunneled_bytes", tunnel.tunneled_bytes)}
        </td>
        <td>
          <FullCopy shortValue={formatDurationMs(tunnel.duration_ms)} fullValue={tunnel.duration_ms} />
          {filter("duration", tunnel.duration_ms)}
        </td>
        <td>{moment(tunnel.established_at).format()}</td>
      </tr>
  )

}
