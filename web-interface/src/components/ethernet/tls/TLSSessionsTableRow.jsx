import {Link} from "react-router-dom";
import React from "react";
import L4Address from "../shared/L4Address";
import GenericConnectionStatus from "../shared/GenericConnectionStatus";
import numeral from "numeral";
import {formatDurationMs, truncate} from "../../../util/Tools";
import moment from "moment/moment";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import FullCopy from "../../shared/FullCopy";
import TLSSessionKey from "./TLSSessionKey";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import InternalAddressOnlyWrapper from "../shared/InternalAddressOnlyWrapper";
import {TLS_SESSION_FILTER_FIELDS} from "./TLSSessionFilterFields";
import TLSChainTrust from "./TLSChainTrust";
import ApiRoutes from "../../../util/ApiRoutes";

export default function TLSSessionsTableRow(props) {

  const session = props.session;
  const setFilters = props.setFilters;

  const filterIcon = (field, value) => {
    if (value === null || value === undefined || value === "") {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={TLS_SESSION_FILTER_FIELDS}
                            field={field}
                            value={value} />
  }

  const macFilter = (address, fieldName) => {
    if (!address) {
      return null;
    }

    return filterIcon(fieldName, address.address);
  }

  // TLS 1.3 encrypts the certificate chain.
  const encrypted = session.version === "TLSv1.3";

  const certificate = () => {
    if (!session.server_leaf_sha256) {
      if (encrypted) {
        return <span className="text-muted" title="TLS 1.3 encrypts the certificate chain.">Encrypted</span>
      }

      return <span className="text-muted" title="No certificate observed in this handshake.">n/a</span>
    }

    const name = session.server_leaf_common_name ? truncate(session.server_leaf_common_name, 24, false) : session.server_leaf_sha256.slice(0, 12);

    return (
      <React.Fragment>
        <Link to={ApiRoutes.ETHERNET.TLS.CERTIFICATES.DETAILS(session.server_leaf_sha256)}
           title={(session.server_leaf_common_name ? session.server_leaf_common_name + "\n" : "") + "SHA-256: " + session.server_leaf_sha256 + (session.server_leaf_issuer ? "\nIssuer: " + session.server_leaf_issuer : "")}>
          <FullCopy shortValue={name} fullValue={session.server_leaf_sha256} />
        </Link>
        {filterIcon("leaf_fingerprint", session.server_leaf_sha256)}
      </React.Fragment>
    )
  }

  const trust = () => {
    if (session.chain_trusted === null || session.chain_trusted === undefined) {
      if (encrypted) {
        return <span className="text-muted" title="TLS 1.3 encrypts the certificate chain.">Encrypted</span>
      }

      return <span className="text-muted" title="No certificate chain observed in this handshake.">n/a</span>
    }

    return (
      <React.Fragment>
        <TLSChainTrust trusted={session.chain_trusted} error={session.chain_trust_error} />
        {filterIcon("chain_trusted", session.chain_trusted)}
      </React.Fragment>
    )
  }

  return (
      <tr>
        <td>
          <TLSSessionKey sessionKey={session.tcp_session_key} />
          {filterIcon("session_key", session.tcp_session_key)}
        </td>
        <td>
          <L4Address address={session.client}
                     hidePort={true}
                     filterElement={session.client ? filterIcon("client_address", session.client.address) : null} />
        </td>
        <td className="hide-narrow">
          <InternalAddressOnlyWrapper
            address={session.client}
            inner={session.client ? <EthernetMacAddress addressWithContext={session.client.mac}
                                                        filterElement={macFilter(session.client.mac, "client_mac")}
                                                        withAssetLink withAssetName /> : null} />
        </td>
        <td>
          <L4Address address={session.server}
                     filterElement={session.server ? filterIcon("server_address", session.server.address) : null}/>
        </td>
        <td className="hide-narrow">
          <InternalAddressOnlyWrapper
            address={session.server}
            inner={session.server ? <EthernetMacAddress addressWithContext={session.server.mac}
                                                        filterElement={macFilter(session.server.mac, "server_mac")}
                                                        withAssetLink withAssetName /> : null} />
        </td>
        <td>
          {session.server_name ? <span title={session.server_name}>{truncate(session.server_name, 30, false)}</span> : <span className="text-muted">None</span>}
          {filterIcon("server_name", session.server_name)}
        </td>
        <td>
          {session.version ? session.version : <span className="text-muted" title="No ServerHello observed.">n/a</span>}
          {filterIcon("version", session.version)}
        </td>
        <td>{certificate()}</td>
        <td>{trust()}</td>
        <td>
          <GenericConnectionStatus status={session.connection_status}/>
          {filterIcon("connection_status", session.connection_status)}
        </td>
        <td className="hide-narrow">
          <FullCopy shortValue={numeral(session.bytes_count).format("0,0b")} fullValue={session.bytes_count} />
          {filterIcon("bytes", session.bytes_count)}
        </td>
        <td className="hide-narrow">
          <FullCopy shortValue={formatDurationMs(session.duration_ms)} fullValue={session.duration_ms} />
          {filterIcon("duration", session.duration_ms)}
        </td>
        <td>{moment(session.established_at).format()}</td>
      </tr>
  )
}
