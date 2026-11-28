import React, {useState} from "react";
import {PORTAL_EXPECTATIONS, portalExpectationToName} from "./PortalIntegrityResult";

const toLines = (values) => (values ? values : []).join("\n");
const toValues = (lines) => lines.split("\n").map((l) => l.trim()).filter((l) => l.length > 0);

// The form state of an expectation. The page holds it, because the observed state is imported into it.
export const expectationDraft = (expectation, urls, addresses, certificates, imported = false) => {
  return {
    expectation: expectation,
    urls: toLines(urls),
    addresses: toLines(addresses),
    certificates: toLines(certificates),
    imported: imported
  }
}

export const draftValues = (draft) => {
  return {
    expectation: draft.expectation,
    urls: toValues(draft.urls),
    addresses: toValues(draft.addresses),
    certificates: toValues(draft.certificates)
  }
}

export default function PortalIntegrityExpectationForm({draft, setDraft, saved, limit, onSubmit, onCancel}) {

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const update = (field) => (e) => {
    setDraft({...draft, [field]: e.target.value});
  }

  const formIsReady = () => {
    return !isSubmitting && (draft.expectation !== "PORTAL" || toValues(draft.urls).length > 0);
  }

  const submit = (e) => {
    e.preventDefault();

    if (!formIsReady()) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    // The lists are only shown for an expected portal. Any other expectation keeps the saved ones as they are.
    const values = draft.expectation === "PORTAL" ? draftValues(draft) : saved;
    onSubmit({
      expectation: draft.expectation,
      expected_final_urls: values.urls,
      expected_addresses: values.addresses,
      expected_certificates: values.certificates
    }, (error) => {
      setIsSubmitting(false);
      if (error && error.response && error.response.status === 422 && error.response.data) {
        setSubmitError(error.response.data.message);
      } else {
        setSubmitError("Could not save the expectation.");
      }
    });
  }

  return (
    <form onSubmit={submit}>
      {draft.imported ?
        <div className="alert alert-info">
          The observed state was added to the form. It is not saved yet.
        </div> : null}

      <div className="mb-3">
        <label htmlFor="portal-expectation" className="form-label">Expectation</label>
        <select className="form-select" id="portal-expectation" style={{maxWidth: 320}}
                value={draft.expectation} onChange={update("expectation")}>
          {PORTAL_EXPECTATIONS.map((e) => <option key={e} value={e}>{portalExpectationToName(e)}</option>)}
        </select>
        <div className="form-text">
          With an expectation, an alert is raised when the last report of the captive portal probe shows something
          else, when its request failed or when it stops reporting.
        </div>
      </div>

      {draft.expectation === "PORTAL" ?
        <>
          <div className="mb-3">
            <label htmlFor="portal-expected-urls" className="form-label">Final URLs</label>
            <textarea className="form-control" id="portal-expected-urls" rows={3}
                      value={draft.urls} onChange={update("urls")} />
            <div className="form-text">
              The URLs the captive portal probe may end at, one per line, up to {limit}. The query string is ignored.
            </div>
          </div>

          <div className="row">
            <div className="col-md-6 mb-3">
              <label htmlFor="portal-expected-addresses" className="form-label">Addresses <small>Optional</small></label>
              <textarea className="form-control" id="portal-expected-addresses" rows={3}
                        value={draft.addresses} onChange={update("addresses")} />
              <div className="form-text">
                The IP addresses the host of the final URL may resolve to, one per line, up to {limit}. Leave empty
                to not check the address.
              </div>
            </div>

            <div className="col-md-6 mb-3">
              <label htmlFor="portal-expected-certificates" className="form-label">
                Certificate Fingerprints <small>Optional</small>
              </label>
              <textarea className="form-control" id="portal-expected-certificates" rows={3}
                        value={draft.certificates} onChange={update("certificates")} />
              <div className="form-text">
                The SHA-256 fingerprints of the certificates the final URL may present, one per line, up
                to {limit}. Leave empty to not check the certificate.
              </div>
            </div>
          </div>
        </> : null}

      {submitError ? <div className="alert alert-danger">{submitError}</div> : null}

      <button type="submit" className="btn btn-primary" disabled={!formIsReady()}>
        {isSubmitting ? "Saving ..." : "Save Expectation"}
      </button>

      <button type="button" className="btn btn-outline-secondary ms-2" disabled={isSubmitting} onClick={onCancel}>
        Cancel
      </button>
    </form>
  )

}
