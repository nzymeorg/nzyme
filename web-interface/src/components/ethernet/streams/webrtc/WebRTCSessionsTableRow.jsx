import React from "react";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import WebRTCSessionActiveIndicator from "./WebRTCSessionActiveIndicator";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import {WEBRTC_FILTER_FIELDS} from "./WebRTCFilterFields";
import L4Address from "../../shared/L4Address";
import moment from "moment";
import numeral from "numeral";
import {formatDurationMs} from "../../../../util/Tools";
import FullCopy from "../../../shared/FullCopy";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function WebRTCSessionsTableRow({session, setFilters}) {

  // IMPORTANT: This component is also used on the STUN connection details page.

  const yes = () => {
    return <span className="text-success">Yes</span>
  }

  const no = () => {
    return <span className="text-muted">No</span>
  }

  const macFilter = (address, fieldName) => {
    if (!address) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={WEBRTC_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  return (
    <tr>
      <td style={{width: 25}}>
        <WebRTCSessionActiveIndicator session={session} />
      </td>
      <td>
        <a href={ApiRoutes.ETHERNET.STREAMS.WEBRTC.DETAILS(session.negotiation_key_sha256)}>
          <FullCopyShortenedId value={session.negotiation_key_sha256} />
        </a>
      </td>
      <td>
        <InternalAddressOnlyWrapper
          address={session.source}
          inner={session.source ? <EthernetMacAddress addressWithContext={session.source.mac}
                                                filterElement={macFilter(session.source.mac, "source_mac")}
                                                withAssetLink withAssetName /> : null} />
      </td>
      <td>
        <L4Address address={session.source}
                   hidePort={true}
                   filterElement={session.source ? <FilterValueIcon setFilters={setFilters}
                                                              fields={WEBRTC_FILTER_FIELDS}
                                                              field="source_address"
                                                              value={session.source.address} /> : null } />
      </td>
      <td>
        <InternalAddressOnlyWrapper
          address={session.destination}
          inner={session.destination ? <EthernetMacAddress addressWithContext={session.destination.mac}
                                                     filterElement={macFilter(session.destination.mac, "destination_mac")}
                                                     withAssetLink withAssetName /> : null} />
      </td>
      <td>
        <L4Address address={session.destination}
                   hidePort={true}
                   filterElement={session.destination ? <FilterValueIcon setFilters={setFilters}
                                                                   fields={WEBRTC_FILTER_FIELDS}
                                                                   field="destination_address"
                                                                   value={session.destination.address} /> : null } />
      </td>
      <td className="hide-narrow">
        {numeral(session.stream_count).format("0,00")}

        <FilterValueIcon setFilters={setFilters}
                         fields={WEBRTC_FILTER_FIELDS}
                         field="stream_count"
                         value={session.stream_count} />
      </td>
      <td className="hide-narrow">{session.has_rtp ? yes() : no()}</td>
      <td className="hide-narrow">{session.has_dtls ? yes() : no()}</td>
      <td className="hide-narrow">{session.has_audio ? yes() : no()}</td>
      <td className="hide-narrow">{session.has_video ? yes() : no()}</td>
      <td>
        {numeral(session.bytes_exchanged).format("0b")}

        <FilterValueIcon setFilters={setFilters}
                         fields={WEBRTC_FILTER_FIELDS}
                         field="bytes_exchanged"
                         value={session.bytes_exchanged} />
      </td>
      <td>
        <FullCopy shortValue={formatDurationMs(session.duration_ms)} fullValue={session.duration_ms} />

        <FilterValueIcon setFilters={setFilters}
                         fields={WEBRTC_FILTER_FIELDS}
                         field="duration_ms"
                         value={session.duration_ms} />
      </td>
      <td title={moment(session.first_seen).fromNow()}>
        {moment(session.first_seen).format()}
      </td>
      <td title={moment(session.last_activity).format()}>
        {moment(session.last_activity).fromNow()}
      </td>
    </tr>
  )

}