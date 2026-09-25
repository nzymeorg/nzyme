import React from "react";
import STUNConnectionSuccessIndicator from "./STUNConnectionSuccessIndicator";
import ApiRoutes from "../../../../../util/ApiRoutes";
import FullCopyShortenedId from "../../../../shared/FullCopyShortenedId";
import InternalAddressOnlyWrapper from "../../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../../shared/context/macs/EthernetMacAddress";
import L4Address from "../../../shared/L4Address";
import FilterValueIcon from "../../../../shared/filtering/FilterValueIcon";
import {STUN_CONNECTIONS_FILTER_FIELDS} from "./STUNConnectionsFilterFields";
import STUNConnectionL4Tags from "./STUNConnectionL4Tags";
import numeral from "numeral";
import moment from "moment";
import STUNConnectionActiveIndicator from "./STUNConnectionActiveIndicator";

export default function STUNConnectionsTableRow({connection, setFilters}) {

  // IMPORTANT: This component is also used on the WebRTC session details page.

  const macFilter = (address, fieldName) => {
    if (!address) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={STUN_CONNECTIONS_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  return (
    <tr>
      <td><STUNConnectionSuccessIndicator successful={connection.successful} /></td>
      <td>
        <a href={ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.DETAILS(connection.negotiation_key_sha256)}>
          <FullCopyShortenedId value={connection.negotiation_key_sha256} />
        </a>
      </td>
      <td>
        <InternalAddressOnlyWrapper
          address={connection.source}
          inner={connection.source ? <EthernetMacAddress addressWithContext={connection.source.mac}
                                                filterElement={macFilter(connection.source.mac, "source_mac")}
                                                withAssetLink withAssetName /> : null} />
      </td>
      <td>
        <L4Address address={connection.source}
                   hidePort={true}
                   filterElement={connection.source ? <FilterValueIcon setFilters={setFilters}
                                                              fields={STUN_CONNECTIONS_FILTER_FIELDS}
                                                              field="source_address"
                                                              value={connection.source.address} /> : null } />
      </td>
      <td>
        <InternalAddressOnlyWrapper
          address={connection.destination}
          inner={connection.destination ? <EthernetMacAddress addressWithContext={connection.destination.mac}
                                                     filterElement={macFilter(connection.destination.mac, "destination_mac")}
                                                     withAssetLink withAssetName /> : null} />
      </td>
      <td>
        <L4Address address={connection.destination}
                   hidePort={true}
                   filterElement={connection.destination ? <FilterValueIcon setFilters={setFilters}
                                                                   fields={STUN_CONNECTIONS_FILTER_FIELDS}
                                                                   field="destination_address"
                                                                   value={connection.destination.address} /> : null } />
      </td>
      <td>
        {connection.is_turn ? "True" : "False"}

        <FilterValueIcon setFilters={setFilters}
                         fields={STUN_CONNECTIONS_FILTER_FIELDS}
                         field="is_turn"
                         value={connection.is_turn ? "true" : "false"} />
      </td>
      <td><STUNConnectionL4Tags tags={connection.l4_tags} setFilters={setFilters} /></td>
      <td className="hide-narrow">{numeral(connection.mapped_addresses.length).format("0,0")}</td>
      <td className="hide-narrow">{numeral(connection.peer_addresses.length).format("0,0")}</td>
      <td className="hide-narrow">{numeral(connection.relayed_addresses.length).format("0,0")}</td>
      <td>{connection.bytes_exchanged === null ? <span className="text-muted">n/a</span> :
        (
          <>
            {numeral(connection.bytes_exchanged).format("0b")}
            <FilterValueIcon setFilters={setFilters}
                             fields={STUN_CONNECTIONS_FILTER_FIELDS}
                             field="bytes_exchanged"
                             value={connection.bytes_exchanged} />
          </>
        )}
      </td>
      <td title={moment(connection.first_seen).fromNow()}>
        {moment(connection.first_seen).format()}
      </td>
      <td title={moment(connection.last_activity).format()}>
        {moment(connection.last_activity).fromNow()}
      </td>
      <td><STUNConnectionActiveIndicator active={connection.is_active} /></td>
    </tr>
  )

}