import React from "react";
import {addressWithPort} from "../../../util/Tools";

export default function BeaconEnrichment({beacon}) {

  const enrichment = beacon.enrichment;

  if (!enrichment || Object.keys(enrichment).length === 0) {
    return <div className="alert alert-info mb-0">No additional protocol details available for this beacon.</div>
  }

  const bool = (value) => {
    if (value === true) return "Yes";
    if (value === false) return "No";
    return <span className="text-muted">n/a</span>;
  }

  // Counts the rows that actually render. The enrichment can exist with all of its values unset.
  let renderedRows = 0;

  const row = (label, value, formatter = undefined) => {
    if (value === undefined || value === null) {
      return null;
    }

    renderedRows++;

    return (
      <React.Fragment>
        <dt>{label}</dt>
        <dd className="machine-data">{formatter ? formatter(value) : String(value)}</dd>
      </React.Fragment>
    )
  }

  const list = (values) => {
    if (!values || values.length === 0) {
      return <span className="text-muted">n/a</span>;
    }

    return (
      <ul className="mb-0 ps-3">
        {values.map((v, i) => <li key={i}>{v}</li>)}
      </ul>
    )
  }

  const rows = () => {
    switch (beacon.type) {
      case "TLS":
        return (
          <React.Fragment>
            {row("Server Name (SNI)", enrichment.server_name)}
            {row("TLS Version", enrichment.version)}
            {row("ALPN", enrichment.alpn_selected)}
            {row("JA4 Fingerprint", enrichment.ja4)}
            {row("Server Certificate CN", enrichment.server_leaf_common_name)}
            {row("Server Certificate Issuer", enrichment.server_leaf_issuer)}
            {row("Server Certificate SHA-256", enrichment.server_leaf_sha256)}
            {row("Self-Signed Certificate", enrichment.server_leaf_self_signed, bool)}
            {row("Chain Trusted", enrichment.chain_trusted, bool)}
            {row("SNI Matches Certificate", enrichment.sni_matches_certificate, bool)}
          </React.Fragment>
        )
      case "SOCKS":
        return (
          <React.Fragment>
            {row("SOCKS Type", enrichment.socks_type)}
            {row("Proxy", enrichment.proxy_address ? addressWithPort(enrichment.proxy_address, enrichment.proxy_port) : null)}
            {row("Tunneled Destination Host", enrichment.tunneled_destination_host)}
            {row("Username", enrichment.username)}
          </React.Fragment>
        )
      case "DNS":
        return (
          <React.Fragment>
            {row("Query Types", enrichment.dns_types, list)}
            {row("DNS Servers", enrichment.dns_servers, list)}
            {row("Distinct Names Queried", enrichment.distinct_name_count)}
            {row("Recently Queried Names", enrichment.names, list)}
          </React.Fragment>
        )
      default:
        return (
          <React.Fragment>
            {row("L4 Type", enrichment.l4_type)}
            {row("TCP Fingerprint", enrichment.fingerprint)}
          </React.Fragment>
        )
    }
  }

  const content = rows();

  if (renderedRows === 0) {
    return <div className="alert alert-info mb-0">No additional protocol details available for this beacon.</div>
  }

  return (
    <dl className="mb-0">
      {content}
    </dl>
  )

}
