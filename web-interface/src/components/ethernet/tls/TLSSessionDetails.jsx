import {Link} from "react-router-dom";
import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../App";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import LoadingSpinner from "../../misc/LoadingSpinner";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import GenericConnectionStatus from "../shared/GenericConnectionStatus";
import L4Address from "../shared/L4Address";
import InternalAddressOnlyWrapper from "../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import TCPSessionLink from "../shared/TCPSessionLink";
import TLSService from "../../../services/ethernet/TLSService";
import TLSCipherSuite, {cipherSuiteName} from "./TLSCipherSuite";
import TLSChainTrust from "./TLSChainTrust";
import TLSChainTable from "./TLSChainTable";
import ApiRoutes from "../../../util/ApiRoutes";
import {
  alertLevelName,
  ecPointFormatName,
  extensionName,
  groupName,
  isWeakGroup,
  isWeakSignatureAlgorithm,
  signatureAlgorithmName
} from "./TLSRegistry";
import numeral from "numeral";
import moment from "moment/moment";
import {formatDurationMs} from "../../../util/Tools";

const tlsService = new TLSService();

export default function TLSSessionDetails({ sessionKey }) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [data, setData] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setData(null);
    tlsService.findSession(sessionKey, organizationId, tenantId, selectedTaps, setData);
  }, [sessionKey, organizationId, tenantId, selectedTaps])

  if (data == null) {
    return <LoadingSpinner />
  }

  const session = data.session;
  const clientHello = data.client_hello;
  const serverHello = data.server_hello;

  // TLS 1.3 encrypts everything after the ServerHello, including the certificates.
  const encrypted = session.version === "TLSv1.3";

  const na = (title) => <span className="text-muted" title={title}>n/a</span>;

  const yesNo = (value) => {
    if (value === null || value === undefined) {
      return na();
    }

    return value ? "Yes" : "No";
  }

  const list = (values, nameFn, weakFn) => {
    if (!values || values.length === 0) {
      return <span className="text-muted">None</span>
    }

    return (
      <ul className="mb-0 ps-3">
        {values.map((v, i) => {
          const weak = weakFn && weakFn(v);
          return (
            <li key={i} className={weak ? "text-danger" : ""} title={weak ? "Considered weak." : (typeof v === "number" ? v.toString() : v)}>
              {nameFn ? nameFn(v) : v}
            </li>
          )
        })}
      </ul>
    )
  }

  const serverChain = () => {
    if (data.server_chain.length === 0) {
      if (encrypted) {
        return <div className="mb-0 alert alert-info">TLS 1.3 encrypts the certificate chain. No certificates were visible on the wire.</div>
      }

      return <div className="mb-0 alert alert-info">No server certificate was observed in this handshake.</div>
    }

    return (
      <React.Fragment>
        <dl className="mb-3">
          <dt>Chain Trust</dt>
          <dd>
            <TLSChainTrust trusted={session.chain_trusted} error={session.chain_trust_error} />
            {session.chain_trusted === false && session.chain_trust_error ?
                <span className="text-muted"> ({session.chain_trust_error})</span> : null}
          </dd>
          <dt>Server Name Matches Certificate</dt>
          <dd>
            {session.sni_matches_certificate === null || session.sni_matches_certificate === undefined ?
                na("No server name was sent.") :
                (session.sni_matches_certificate ? <span className="text-success">Yes</span> : <span className="text-danger">No</span>)}
          </dd>
          <dt>Certificate Valid at Handshake</dt>
          <dd>
            {session.leaf_valid_at_handshake === null || session.leaf_valid_at_handshake === undefined ?
                na() :
                (session.leaf_valid_at_handshake ? <span className="text-success">Yes</span> : <span className="text-danger">No</span>)}
          </dd>
        </dl>

        <TLSChainTable chain={data.server_chain} />
      </React.Fragment>
    )
  }

  const clientChain = () => {
    if (data.client_chain.length === 0) {
      if (session.client_certificate_requested) {
        return <div className="mb-0 alert alert-info">The server requested a client certificate but none was observed. The client either sent an empty certificate list or the handshake was not completed.</div>
      }

      return <div className="mb-0 alert alert-info">No client certificate was requested.</div>
    }

    return <TLSChainTable chain={data.client_chain} />
  }

  const alerts = () => {
    if (data.alerts.length === 0) {
      return <div className="mb-0 alert alert-info">No alerts were observed in this session.</div>
    }

    return (
        <table className="table table-sm table-hover table-striped mb-0">
          <thead>
          <tr>
            <th>#</th>
            <th>Direction</th>
            <th>Level</th>
            <th>Description</th>
          </tr>
          </thead>
          <tbody>
          {data.alerts.map((alert, i) => (
            <tr key={i}>
              <td>{i + 1}</td>
              <td>{direction(alert.direction)}</td>
              {alert.encrypted ?
                <td colSpan={2}>
                  <span className="text-muted" title="The alert was sent after the ChangeCipherSpec and is encrypted. Only its presence is visible.">Encrypted</span>
                </td>
                :
                <React.Fragment>
                  <td className={alert.level === 2 ? "text-danger" : ""}>{alertLevelName(alert.level)}</td>
                  <td>
                    {alert.description_name ? alert.description_name : "unknown"}
                    <span className="text-muted"> ({alert.description})</span>
                  </td>
                </React.Fragment>}
            </tr>
          ))}
          </tbody>
        </table>
    )
  }

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Details" />

                <dl className="mb-0">
                  <dt>Connection Status</dt>
                  <dd><GenericConnectionStatus status={session.connection_status}/></dd>
                  <dt>Negotiated Version</dt>
                  <dd>{session.version ? session.version : na("No ServerHello observed.")}</dd>
                  <dt>Highest Version Offered by Client</dt>
                  <dd>{session.client_max_version ? session.client_max_version : na("No ClientHello observed.")}</dd>
                  <dt>Cipher Suite</dt>
                  <dd>
                    {session.cipher_suite !== null && session.cipher_suite !== undefined ?
                        <span title={"0x" + session.cipher_suite.toString(16).toUpperCase().padStart(4, "0")}>{cipherSuiteName(session.cipher_suite)}</span>
                        : na("No ServerHello observed.")}
                  </dd>
                  <dt>Server Name (SNI)</dt>
                  <dd>{session.server_name ? session.server_name : <span className="text-muted">None</span>}</dd>
                  <dt>ALPN Selected by Server</dt>
                  <dd>
                    {session.alpn_selected ? session.alpn_selected :
                        (encrypted ? <span className="text-muted" title="TLS 1.3 encrypts the server's ALPN selection.">Encrypted</span> : <span className="text-muted">None</span>)}
                  </dd>
                  <dt>ALPN Offered by Client</dt>
                  <dd>
                    {session.alpn_offered && session.alpn_offered.length > 0 ?
                        session.alpn_offered.join(", ") : <span className="text-muted">None</span>}
                  </dd>
                  <dt>JA4 Client Fingerprint</dt>
                  <dd>
                    {session.ja4 ?
                        <React.Fragment>
                          <span className="machine-data">{session.ja4}</span>
                          <Link to={ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX + "?filters=" + encodeURIComponent(JSON.stringify({ja4: [{field: "ja4", operator: "equals", value: session.ja4}]}))}
                             title="Show all sessions with this fingerprint">
                            <i className="fa-brands fa-searchengin filter-value hide-narrow"></i>
                          </Link>
                        </React.Fragment>
                        : na("No ClientHello observed.")}
                  </dd>
                  <dt>Session Resumed</dt>
                  <dd>{yesNo(session.session_resumed)}</dd>
                  <dt>Hello Retry Request</dt>
                  <dd>{yesNo(session.hello_retry_request)}</dd>
                  <dt>Encrypted Client Hello</dt>
                  <dd>{yesNo(session.encrypted_client_hello)}</dd>
                  <dt>Client Certificate Requested</dt>
                  <dd>{yesNo(session.client_certificate_requested)}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Session Source &amp; Destination" />

                <dl className="mb-0">
                  <dt>Client Address</dt>
                  <dd><L4Address address={session.client} hidePort={true}/></dd>
                  <dt>Client Asset</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={session.client}
                        inner={session.client ?
                            <EthernetMacAddress addressWithContext={session.client.mac} withAssetLink withAssetName />
                            : null } />
                  </dd>
                  <dt>Server Address</dt>
                  <dd><L4Address address={session.server} /></dd>
                  <dt>Server Asset</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={session.server}
                        inner={session.server ?
                            <EthernetMacAddress addressWithContext={session.server.mac} withAssetLink withAssetName />
                            : null} />
                  </dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Metadata" />

                <dl className="mb-0">
                  <dt>Tunneled Bytes</dt>
                  <dd>{numeral(session.bytes_count).format("0,0b")}</dd>
                  <dt>Duration</dt>
                  <dd>{formatDurationMs(session.duration_ms)}</dd>
                  <dt>Established at</dt>
                  <dd>{moment(session.established_at).format()}</dd>
                  <dt>Terminated at</dt>
                  <dd>
                    {session.terminated_at ? moment(session.terminated_at).format() :
                        <span className="text-muted">n/a</span>}
                  </dd>
                  <dt>Underlying TCP Session</dt>
                  <dd><TCPSessionLink sessionId={session.tcp_session_key} startTime={session.established_at} /></dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Server Certificate Chain" />

                {serverChain()}
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Client Certificate Chain" />

                {clientChain()}
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Client Hello" />

                {clientHello ?
                  <dl className="mb-0">
                    <dt>Record / Legacy Version</dt>
                    <dd>
                      {clientHello.record_version ? clientHello.record_version : na()} / {clientHello.legacy_version ? clientHello.legacy_version : na()}
                    </dd>
                    <dt>Supported Versions</dt>
                    <dd>{clientHello.supported_versions.length > 0 ? clientHello.supported_versions.join(", ") : <span className="text-muted">Not sent (legacy version applies)</span>}</dd>
                    <dt>Cipher Suites Offered ({clientHello.cipher_suites.length})</dt>
                    <dd>{list(clientHello.cipher_suites, cipherSuiteName)}</dd>
                    <dt>Extensions ({clientHello.extensions.length})</dt>
                    <dd>{list(clientHello.extensions, extensionName)}</dd>
                    <dt>Supported Groups</dt>
                    <dd>{list(clientHello.supported_groups, groupName, isWeakGroup)}</dd>
                    <dt>Signature Algorithms</dt>
                    <dd>{list(clientHello.signature_algorithms, signatureAlgorithmName, isWeakSignatureAlgorithm)}</dd>
                    <dt>EC Point Formats</dt>
                    <dd>{list(clientHello.ec_point_formats, ecPointFormatName)}</dd>
                    <dt>Session ID Present</dt>
                    <dd>{yesNo(clientHello.session_id_present)}</dd>
                    <dt>Offers Session Ticket</dt>
                    <dd>{yesNo(clientHello.offers_session_ticket)}</dd>
                    <dt>Offers Pre-Shared Key</dt>
                    <dd>{yesNo(clientHello.offers_pre_shared_key)}</dd>
                  </dl>
                  : <div className="mb-0 alert alert-info">No ClientHello was observed. The tap likely started recording after the handshake began.</div>}
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Server Hello" />

                {serverHello ?
                  <dl className="mb-0">
                    <dt>Record / Legacy Version</dt>
                    <dd>
                      {serverHello.record_version ? serverHello.record_version : na()} / {serverHello.legacy_version ? serverHello.legacy_version : na()}
                    </dd>
                    <dt>Negotiated Version</dt>
                    <dd>{session.version ? session.version : na()}</dd>
                    <dt>Cipher Suite</dt>
                    <dd><TLSCipherSuite suite={session.cipher_suite} /></dd>
                    <dt>Compression Method</dt>
                    <dd>
                      {serverHello.compression_method === 0 ? "null (none)" : <span className="text-danger" title="TLS compression enables the CRIME attack.">{serverHello.compression_method}</span>}
                    </dd>
                    <dt>Extensions ({serverHello.extensions.length})</dt>
                    <dd>{list(serverHello.extensions, extensionName)}</dd>
                  </dl>
                  : <div className="mb-0 alert alert-info">No ServerHello was observed. The server did not respond, or the tap did not see its side of the handshake.</div>}
              </div>
            </div>

            <div className="card mt-3">
              <div className="card-body">
                <CardTitleWithControls title="Alerts" />

                {alerts()}
              </div>
            </div>

            <div className="card mt-3">
              <div className="card-body">
                <CardTitleWithControls title="Application Data" />

                <dl className="mb-0">
                  <dt>Change Cipher Spec Seen</dt>
                  <dd>Client: {yesNo(data.client_change_cipher_spec)}, Server: {yesNo(data.server_change_cipher_spec)}</dd>
                  <dt>Client to Server</dt>
                  <dd>{numeral(data.client_app_data_records).format("0,0")} records, {numeral(data.client_app_data_bytes).format("0,0b")}</dd>
                  <dt>Server to Client</dt>
                  <dd>{numeral(data.server_app_data_records).format("0,0")} records, {numeral(data.server_app_data_bytes).format("0,0b")}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

function direction(value) {
  switch (value) {
    case "ClientToServer": return "Client to Server";
    case "ServerToClient": return "Server to Client";
    default: return value;
  }
}
