import React from "react";
import numeral from "numeral";

const signatureName = (signature) => {
  switch (signature) {
    case "ECHO_NONZERO_CODE": return "Echo request with non-zero code";
    case "ECHO_TOS_4": return "Echo request with type of service 4";
    case "INFORMATION_REQUEST": return "Information request";
    case "TIMESTAMP_REQUEST": return "Timestamp request";
    case "ADDRESS_MASK_REQUEST": return "Address mask request";
    case "TCP_SEQ_PROBES": return "TCP SYN sequence probes (window 1, 63, 4, 4, 16, 512)";
    case "TCP_ECN_PROBE": return "TCP SYN with ECN flags and window 3";
    case "TCP_T2_PROBE": return "TCP segment without flags, window 128 (T2)";
    case "TCP_T3_PROBE": return "TCP SYN/FIN/PSH/URG, window 256, to an open port (T3)";
    case "TCP_T4_PROBE": return "TCP ACK with SYN-only options, window 1024, to an open port (T4)";
    case "TCP_T5_PROBE": return "TCP SYN, window 31337, to a closed port (T5)";
    case "TCP_T6_PROBE": return "TCP ACK with SYN-only options, window 32768, to a closed port (T6)";
    case "TCP_T7_PROBE": return "TCP FIN/PSH/URG, window 65535, to a closed port (T7)";
    case "UDP_U1_PROBE": return "UDP datagram with 300 byte payload to a closed port";
    default: return signature;
  }
}

export default function ReconScanEnrichment({scan}) {

  const e = scan.enrichment;

  const NO_DATA = <div className="alert alert-info mb-0">No probe details were recorded for this scan.</div>;

  if (!e || Object.keys(e).length === 0) {
    return NO_DATA;
  }

  const row = (label, value, hint = undefined) => {
    if (value === undefined || value === null || value === "") {
      return null;
    }

    return (
      <React.Fragment>
        <dt title={hint}>{label}</dt>
        <dd>{String(value)}</dd>
      </React.Fragment>
    )
  }

  if (scan.type === "OS_FINGERPRINTING" || scan.type === "ICMP_SWEEP") {
    return (
      <React.Fragment>
        <dl className="mb-0">
          {e.signatures && e.signatures.length > 0 ?
            <React.Fragment>
              <dt>Probe Signatures</dt>
              <dd>
                <ul className="mb-0 ps-3">
                  {e.signatures.map((s) => <li key={s}>{signatureName(s)}</li>)}
                </ul>
              </dd>
            </React.Fragment> : null}
          {e.protocols && e.protocols.length > 0 ? row("Protocols", e.protocols.join(", ")) : null}
          {e.tcp_seq_probes !== undefined ?
            <React.Fragment>
              <dt>TCP Sequence Probes</dt>
              <dd>{e.tcp_seq_probes} of 6 distinct probe shapes seen</dd>
            </React.Fragment> : null}
          {e.tcp_ports && e.tcp_ports.length > 0 ? row("TCP Ports Probed", e.tcp_ports.join(", ")) : null}
          {e.request_count !== undefined ? <React.Fragment><dt>Requests</dt><dd>{numeral(e.request_count).format("0,0")}</dd></React.Fragment> : null}
          {row("IP TTL", e.ip_ttl)}
          {e.payload_bytes_min !== undefined && e.payload_bytes_max !== undefined ?
            <React.Fragment>
              <dt>Payload Size</dt>
              <dd className="machine-data">{e.payload_bytes_min === e.payload_bytes_max ? e.payload_bytes_min + " bytes" : e.payload_bytes_min + " to " + e.payload_bytes_max + " bytes"}</dd>
            </React.Fragment>
            : row("Payload Size", e.payload_bytes_max !== undefined ? e.payload_bytes_max + " bytes" : undefined)}
        </dl>
      </React.Fragment>
    )
  }

  if (scan.type === "SLOW_SCAN") {
    return (
      <React.Fragment>
        <p className="text-muted">
          This device probed hosts and ports too sparsely to cross the port scan or host sweep thresholds inside
          any detection window. Its distinct unanswered or refused targets accumulated over the slow scan memory
          period instead. Targets that were part of a regular scan of this device are not counted here.
        </p>

        <dl className="mb-0">
          {row("Protocols", e.l4_type)}
          {e.active_days !== undefined ? row("Active Over", e.active_days + (e.active_days === 1 ? " day" : " days")) : null}
          {e.memory_days !== undefined ? row("Memory", e.memory_days + " days") : null}
        </dl>
      </React.Fragment>
    )
  }

  if (scan.type === "DNS_REVERSE_SWEEP") {
    return (
      <React.Fragment>
        <p className="text-muted">
          The device asked a DNS resolver for the names of many addresses instead of directly probing the hosts. The
          targets never saw a packet.
        </p>

        <dl className="mb-0">
          {e.query_count !== undefined ? <React.Fragment><dt>Queries</dt><dd>{numeral(e.query_count).format("0,0")}</dd></React.Fragment> : null}
          {row("Resolver", e.resolver)}
          {e.resolver_count !== undefined && e.resolver_count > 1 ? row("Resolvers Used", e.resolver_count) : null}
          {e.network_count !== undefined ? row("Networks (/24)", e.network_count) : null}
          {e.range_coverage_percent !== undefined ? row("Range Coverage", e.range_coverage_percent + "%") : null}
        </dl>
      </React.Fragment>
    )
  }

  if (scan.type === "ARP_SWEEP") {
    if (e.request_count === undefined || e.request_count === null) {
      return NO_DATA;
    }

    return (
      <dl className="mb-0">
        <dt>ARP Requests</dt>
        <dd className="mb-0">{numeral(e.request_count || 0).format("0,0")}</dd>
      </dl>
    )
  }

  // The type is always present. Without any packet attribute there is nothing to show.
  const hasAttributes = ["ip_ttl", "ip_df", "tcp_window_size", "tcp_mss", "tcp_options", "fingerprint", "stealth_probe_count"]
    .some((key) => e[key] !== undefined && e[key] !== null && e[key] !== "");

  if (!hasAttributes) {
    return NO_DATA;
  }

  return (
    <React.Fragment>
      <p className="text-muted">
        Header fields of the probe packets, showing for each field the value the scanner used most often.
      </p>

      <dl className="mb-0">
        {row("IP TTL", e.ip_ttl)}
        {row("IP Don't Fragment", e.ip_df === undefined ? undefined : (e.ip_df ? "Set" : "Not set"))}
        {row("TCP Window Size", e.tcp_window_size)}
        {row("TCP MSS", e.tcp_mss)}
        {row("TCP Options", e.tcp_options)}
        {row("Fingerprint", e.fingerprint)}
        {e.stealth_probe_count !== undefined && e.stealth_probe_count > 0 ?
          <React.Fragment>
            <dt title="Segments that belong to no session and have a shape no real stack sends: NULL, FIN, Xmas or crafted ACK probes">
              Stealth Probes
            </dt>
            <dd className="machine-data">
              {numeral(e.stealth_probe_count).format("0,0")}
              {e.stealth_flags ? <span> (mostly {e.stealth_flags})</span> : null}
            </dd>
          </React.Fragment> : null}
        {e.fingerprint_count !== undefined && e.fingerprint_count > 1 ?
          <React.Fragment>
            <dt>Distinct Fingerprints</dt>
            <dd className="mb-0">
              {numeral(e.fingerprint_count).format("0,0")}{' '}
              <span className="text-muted">(the device used more than one packet shape)</span>
            </dd>
          </React.Fragment> : null}
      </dl>
    </React.Fragment>
  )

}
