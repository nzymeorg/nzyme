import React from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import IPAddressLink from "../../../ethernet/shared/IPAddressLink";
import TLSCertificateFingerprint from "../../../ethernet/tls/TLSCertificateFingerprint";
import {portalReasonsToText} from "../../../ethernet/portalintegrity/PortalIntegrityResult";

const DESCRIPTIONS = {
  PORTAL_INTEGRITY_PORTAL_MISSING: "The control URL is expected to be intercepted by a captive portal, but the " +
    "captive portal probe got the answer of the control URL itself. A new device on this network currently reaches the " +
    "internet without passing the portal.",
  PORTAL_INTEGRITY_PORTAL_UNEXPECTED: "The control URL is expected to answer without a captive portal, but the " +
    "request of the captive portal probe was intercepted: it was redirected away from the site of the control URL, or the " +
    "answer was not the one this connectivity check URL gives. Something on the network is modifying or " +
    "redirecting the traffic of new devices.",
  PORTAL_INTEGRITY_PORTAL_WRONG: "The captive portal probe was presented a captive portal, but not the expected one. Its final " +
    "URL, the address its host resolved to or its certificate is not among the expected values of the control " +
    "URL. A portal that was replaced or redirected can collect the credentials of everyone who signs in.",
  PORTAL_INTEGRITY_REQUEST_FAILED: "The captive portal probe could not complete the request for a monitored control URL, so " +
    "nothing is known about what a new device on this network is presented at the moment.",
  PORTAL_INTEGRITY_PROBE_SILENT: "The captive portal probe has not sent a report for several of its cycles, although its tap is " +
    "configured to run it. Nothing is known about what a new device on this network is presented until it " +
    "reports again. A captive portal probe that cannot get an address on the network sends no reports at all."
};

const FALSE_POSITIVES = {
  PORTAL_INTEGRITY_PORTAL_MISSING: [
    "The MAC address of the captive portal probe was authorized on the portal, for example by an administrator or by a " +
      "remembered sign-in. The captive portal probe is then treated like a guest who already passed the portal.",
    "The captive portal was turned off or is being maintained.",
    "The gateway does not intercept this control URL. Some portals only intercept the connectivity check URLs " +
      "of certain operating systems."
  ],
  PORTAL_INTEGRITY_PORTAL_UNEXPECTED: [
    "A captive portal was introduced on the network on purpose. Change the expectation of the control URL.",
    "The operator of a connectivity check URL changed its answer.",
    "A control URL that is not a known connectivity check URL started to redirect to another site on its own."
  ],
  PORTAL_INTEGRITY_PORTAL_WRONG: [
    "The portal was moved to another URL or address, or its vendor changed the sign-in flow.",
    "The certificate of the portal was renewed. A renewed certificate has a new fingerprint.",
    "The portal is hosted on several addresses and the captive portal probe reached one that is not expected yet."
  ],
  PORTAL_INTEGRITY_REQUEST_FAILED: [
    "A short outage of the network, its DNS servers or the portal.",
    "The control URL only works over one IP version and the captive portal probe requests it over both.",
    "The control URL itself is unreachable. Compare with the other control URLs of the captive portal probe."
  ],
  PORTAL_INTEGRITY_PROBE_SILENT: [
    "The tap is offline, restarting or cannot reach the node.",
    "The interface of the captive portal probe is down or was unplugged.",
    "The tap was taken out of service without being deleted. A captive portal probe that is removed from the configuration " +
      "of a running tap, or set to inactive, does not raise this alert."
  ]
};

export default function PortalIntegrityAlertDetails({alert}) {

  const a = alert.attributes;
  const type = alert.detection_type;

  if (type === "PORTAL_INTEGRITY_PROBE_SILENT") {
    return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Captive Portal Probe</dt>
                  <dd className="text-break">{a.probe_name}</dd>
                  <dt>Tap</dt>
                  <dd>{a.tap_name ? a.tap_name : <span className="text-muted">n/a</span>}</dd>
                  <dt>IP Version</dt>
                  <dd>IPv{a.ip_version}</dd>
                  <dt>Last Report</dt>
                  <dd title={moment(a.last_report_at).format()}>{moment(a.last_report_at).fromNow()}</dd>
                  <dt>Monitored Control URLs</dt>
                  <dd>{numeral(a.control_url_count).format("0,0")}</dd>

                  <p className="mb-0 mt-3">{DESCRIPTIONS[type]}</p>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Possible false-positives</h3>

                <p>
                  These circumstances can lead to false-positive alerts of this type:
                </p>

                <ul className="mb-0">
                  {FALSE_POSITIVES[type].map((text, i) => <li key={i}>{text}</li>)}
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
    )
  }

  const optional = (title, value, element = undefined) => {
    if (!value) {
      return null;
    }

    return (
      <>
        <dt>{title}</dt>
        <dd>{element ? element : <span className="machine-data text-break">{value}</span>}</dd>
      </>
    )
  }

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>Captive Portal Probe</dt>
                <dd className="text-break">{a.probe_name}</dd>
                <dt>Tap</dt>
                <dd>{a.tap_name ? a.tap_name : <span className="text-muted">n/a</span>}</dd>
                <dt>Control URL</dt>
                <dd><span className="machine-data text-break">{a.control_url}</span></dd>
                <dt>IP Version</dt>
                <dd>IPv{a.ip_version}</dd>
                {type === "PORTAL_INTEGRITY_REQUEST_FAILED" ?
                  <>
                    <dt>Error</dt>
                    <dd className="text-break">{a.error ? a.error : <span className="text-muted">n/a</span>}</dd>
                  </>
                  :
                  <>
                    {optional(type === "PORTAL_INTEGRITY_PORTAL_MISSING" ? "Final URL" : "Presented Portal", a.final_url)}
                    {optional("Address", a.final_address, <IPAddressLink ip={a.final_address} full />)}
                    {optional("Certificate", a.final_certificate,
                        <TLSCertificateFingerprint sha256={a.final_certificate} />)}
                  </>}
                {type === "PORTAL_INTEGRITY_PORTAL_WRONG" && a.reasons ?
                  <>
                    <dt>Differs In</dt>
                    <dd>{portalReasonsToText(a.reasons.split(","))}</dd>
                  </> : null}
                {a.report_uuid ?
                  <>
                    <dt>Last Report</dt>
                    <dd>
                      <Link to={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.REPORT_DETAILS(a.report_uuid)}>Open Report</Link>
                    </dd>
                  </> : null}

                <p className="mb-0 mt-3">{DESCRIPTIONS[type]}</p>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Possible false-positives</h3>

              <p>
                These circumstances can lead to false-positive alerts of this type:
              </p>

              <ul className="mb-0">
                {(FALSE_POSITIVES[type] ? FALSE_POSITIVES[type] : []).map((text, i) => <li key={i}>{text}</li>)}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
