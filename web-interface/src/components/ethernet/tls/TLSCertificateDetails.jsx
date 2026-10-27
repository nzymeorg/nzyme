import {Link} from "react-router-dom";
import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../App";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import LoadingSpinner from "../../misc/LoadingSpinner";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import FullCopy from "../../shared/FullCopy";
import {Presets} from "../../shared/timerange/TimeRange";
import ApiRoutes from "../../../util/ApiRoutes";
import TLSService from "../../../services/ethernet/TLSService";
import TLSCertificateFingerprint from "./TLSCertificateFingerprint";
import TLSCertificateSessions from "./TLSCertificateSessions";
import {issuerCommonName} from "./TLSChainTable";
import numeral from "numeral";
import moment from "moment/moment";

const tlsService = new TLSService();

export default function TLSCertificateDetails({ sha256 }) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [data, setData] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setData(null);
    tlsService.findCertificate(sha256, organizationId, tenantId, timeRange, selectedTaps, setData);
  }, [sha256, organizationId, tenantId, timeRange, selectedTaps])

  if (data == null) {
    return <LoadingSpinner />
  }

  const c = data.certificate;

  const na = <span className="text-muted">n/a</span>;

  const expired = c.not_after && moment(c.not_after).isBefore(moment());
  const notYetValid = c.not_before && moment(c.not_before).isAfter(moment());
  const expiringSoon = c.not_after && !expired && moment(c.not_after).isBefore(moment().add(30, "days"));

  const title = c.common_name ? c.common_name : (c.subject ? c.subject : sha256);

  const type = () => {
    if (!c.parsed) {
      return <span className="text-danger" title={data.parse_error}>Unparsed{data.parse_error ? ": " + data.parse_error : ""}</span>
    }

    const parts = [c.is_ca ? "Certificate Authority" : "Leaf (End Entity)"];
    if (c.self_signed) {
      parts.push("Self-signed");
    }

    return parts.join(", ");
  }

  const validity = () => {
    if (!c.not_after) {
      return na;
    }

    if (expired) {
      return <span className="text-danger">Expired {moment(c.not_after).fromNow()}</span>
    }

    if (notYetValid) {
      return <span className="text-danger">Not valid before {moment(c.not_before).format()}</span>
    }

    return <span className={expiringSoon ? "text-warning" : "text-success"}>Valid, expires {moment(c.not_after).fromNow()}</span>
  }

  const issuer = () => {
    if (!c.issuer) {
      return na;
    }

    if (c.self_signed) {
      return <span title={c.issuer}>{c.issuer} <span className="text-muted">(self-signed)</span></span>
    }

    if (data.issuer_certificate) {
      return (
        <span title={c.issuer}>
          <Link to={ApiRoutes.ETHERNET.TLS.CERTIFICATES.DETAILS(data.issuer_certificate.sha256)}>
            {issuerCommonName(c.issuer)}
          </Link>
          <span className="text-muted"> ({c.issuer})</span>
        </span>
      )
    }

    return <span title="The issuing certificate was not observed on the network.">{c.issuer}</span>
  }

  const sources = () => {
    if (!c.sources || c.sources.length === 0) {
      return <span className="text-muted">Not referenced in selected time range</span>
    }

    return c.sources.map((s) => sourceName(s)).join(", ");
  }

  const sans = () => {
    if (!c.subject_alternative_names || c.subject_alternative_names.length === 0) {
      return <div className="mb-0 alert alert-info">This certificate has no subject alternative names.</div>
    }

    return (
      <ul className="mb-0 ps-3" style={{columns: c.subject_alternative_names.length > 12 ? 3 : 1}}>
        {c.subject_alternative_names.map((san, i) => <li key={i}>{san}</li>)}
      </ul>
    )
  }

  const hex = (value) => {
    if (!value) {
      return na;
    }

    return value.match(/.{1,2}/g).join(":")
  }

  // Leaf certificates are matched by their fingerprint, CA certificates by the issuer of the leaf.
  const sessionsTitle = c.is_ca ? "Sessions With Certificates Issued by This CA" : "Sessions Using This Certificate";
  const sessionsFilters = c.is_ca && c.subject ? {
    "leaf_issuer": [{ field: "leaf_issuer", operator: "equals", value: c.subject }]
  } : {
    "leaf_fingerprint": [{ field: "leaf_fingerprint", operator: "equals", value: c.sha256 }]
  };

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-12">
            <h1>
              Certificate &quot;{title}&quot;
            </h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Identity" />

                <dl className="mb-0">
                  <dt>Common Name</dt>
                  <dd>{c.common_name ? c.common_name : na}</dd>
                  <dt>Subject</dt>
                  <dd>{c.subject ? c.subject : na}</dd>
                  <dt>Issuer</dt>
                  <dd>{issuer()}</dd>
                  <dt>Type</dt>
                  <dd>{type()}</dd>
                  <dt>Serial Number</dt>
                  <dd>{data.serial ? data.serial : na}</dd>
                  <dt>X.509 Version</dt>
                  <dd>{data.version ? "v" + data.version : na}</dd>
                  <dt>Extended Key Usage</dt>
                  <dd>{c.extended_key_usage && c.extended_key_usage.length > 0 ? c.extended_key_usage.join(", ") : <span className="text-muted">None</span>}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Validity &amp; Cryptography" />

                <dl className="mb-0">
                  <dt>Status</dt>
                  <dd>{validity()}</dd>
                  <dt>Not Before</dt>
                  <dd>{c.not_before ? <span className={notYetValid ? "text-danger" : ""}>{moment(c.not_before).format()}</span> : na}</dd>
                  <dt>Not After</dt>
                  <dd>{c.not_after ? <span className={expired ? "text-danger" : ""}>{moment(c.not_after).format()}</span> : na}</dd>
                  <dt>Public Key</dt>
                  <dd>{c.public_key_algorithm ? c.public_key_algorithm + (c.public_key_bits ? " " + c.public_key_bits + " bit" : "") : na}</dd>
                  <dt>Signature Algorithm</dt>
                  <dd>{c.signature_algorithm ? c.signature_algorithm : na}</dd>
                  <dt>Subject Key Identifier</dt>
                  <dd>{hex(data.subject_key_identifier)}</dd>
                  <dt>Authority Key Identifier</dt>
                  <dd>{hex(data.authority_key_identifier)}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Fingerprints &amp; Sightings"
                                       doNotPersistTimeRange={true}
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange} />

                <dl className="mb-0">
                  <dt>SHA-256</dt>
                  <dd><TLSCertificateFingerprint sha256={c.sha256} /></dd>
                  <dt>SHA-1</dt>
                  <dd>{c.sha1 ? <FullCopy shortValue={c.sha1.slice(0, 16) + "..."} fullValue={c.sha1} /> : na}</dd>
                  <dt>DER Size</dt>
                  <dd>{numeral(data.der_length).format("0,0")} bytes</dd>
                  <dt>First Seen</dt>
                  <dd>{moment(c.first_seen).format()}</dd>
                  <dt>Last Seen</dt>
                  <dd>{moment(c.last_seen).format()}</dd>
                  <dt>Sessions in Selected Time Range</dt>
                  <dd>{numeral(c.session_count).format("0,0")}</dd>
                  <dt>Sources in Selected Time Range</dt>
                  <dd>{sources()}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title={"Subject Alternative Names" + (c.subject_alternative_names ? " (" + c.subject_alternative_names.length + ")" : "")} />

                {sans()}
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <TLSCertificateSessions title={sessionsTitle} filters={sessionsFilters} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="PEM" />

                <pre className="mb-0 machine-data">{data.pem}</pre>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

function sourceName(source) {
  switch (source) {
    case "TLS": return "TLS Sessions";
    case "PORTAL_INTEGRITY": return "Portal Integrity";
    default: return source;
  }
}
