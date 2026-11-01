import React from "react";

function TLSCertificateTestFailure(props) {

  if (props.result) {
    return (
        <div className="alert alert-danger mt-4">
          <strong>The uploaded TLS certificate or key is invalid.</strong>

          <dl className="tls-cert-failures mt-3">
            <dt>Certificate</dt>
            <dd>
              {props.result.cert_success ? "OK / Success" : "Could not be read / parsed."}
            </dd>
            <dt>Key</dt>
            <dd>
              {props.result.key_success ? "OK / Success" : "Could not be read / parsed."}
            </dd>
          </dl>

          <p className="mt-3 mb-0">
            Please double check the files you uploaded. The Nzyme log file will have more details about why the
            certificate and/or key is invalid.
          </p>
        </div>
    )
  }

  return null

}

export default TLSCertificateTestFailure;