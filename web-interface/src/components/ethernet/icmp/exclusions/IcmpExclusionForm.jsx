import React, {useState} from "react";

const DETECTION_TYPES = [
  {value: "REDIRECT", name: "Redirect"},
  {value: "TRACEROUTE", name: "Traceroute"},
  {value: "TUNNEL", name: "Tunnel"}
];

const EMPTY = {
  name: "",
  description: "",
  enabled: true,
  detection_types: [],
  source_cidr: "",
  source_mac: "",
  gateway_cidr: ""
};

const hasCriteria = (v) => {
  return v.detection_types.length > 0 || v.source_cidr.trim() !== "" || v.source_mac.trim() !== "" || v.gateway_cidr.trim() !== "";
}

export default function IcmpExclusionForm({initialValues = undefined, submitText, onSubmit}) {

  const [values, setValues] = useState({...EMPTY, ...(initialValues ? initialValues : {})});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const update = (field) => (e) => {
    const value = e.target.type === "checkbox" ? e.target.checked : e.target.value;
    setValues({...values, [field]: value});
  }

  const toggleType = (type) => (e) => {
    const types = e.target.checked
      ? [...values.detection_types, type]
      : values.detection_types.filter((t) => t !== type);
    setValues({...values, detection_types: DETECTION_TYPES.map((t) => t.value).filter((t) => types.includes(t))});
  }

  const ready = () => values.name.trim().length > 0 && hasCriteria(values) && !isSubmitting;

  const submit = (e) => {
    e.preventDefault();
    if (!ready()) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    onSubmit({
      name: values.name.trim(),
      description: values.description,
      enabled: values.enabled,
      detection_types: values.detection_types,
      source_cidr: values.source_cidr,
      source_mac: values.source_mac,
      gateway_cidr: values.gateway_cidr
    }, (error) => {
      setIsSubmitting(false);
      if (error && error.response && error.response.status === 422 && error.response.data) {
        setSubmitError(error.response.data.message);
      } else {
        setSubmitError("Could not save the exclusion.");
      }
    });
  }

  return (
    <form onSubmit={submit}>
      <div className="mb-3">
        <label htmlFor="exclusion-name" className="form-label">Name</label>
        <input type="text" className="form-control" id="exclusion-name" maxLength={128}
               value={values.name} onChange={update("name")} />
      </div>

      <div className="mb-3">
        <label htmlFor="exclusion-description" className="form-label">Description <small>Optional</small></label>
        <textarea className="form-control" id="exclusion-description" rows={2}
                  value={values.description} onChange={update("description")} />
      </div>

      <div className="form-check mb-4">
        <input className="form-check-input" type="checkbox" id="exclusion-enabled"
               checked={values.enabled} onChange={update("enabled")} />
        <label className="form-check-label" htmlFor="exclusion-enabled">Enabled</label>
      </div>

      <h5>Criteria</h5>
      <p className="text-muted">A detection is suppressed when every set criterion matches. Leave a field empty to not match on it.</p>

      <div className="mb-3">
        <label className="form-label">Detection Types</label>
        <div>
          {DETECTION_TYPES.map((t) => {
            return (
              <div className="form-check form-check-inline" key={t.value}>
                <input className="form-check-input" type="checkbox" id={"exclusion-type-" + t.value}
                       checked={values.detection_types.includes(t.value)} onChange={toggleType(t.value)} />
                <label className="form-check-label" htmlFor={"exclusion-type-" + t.value}>{t.name}</label>
              </div>
            )
          })}
        </div>
        <div className="form-text">Leave both unchecked to match redirects and traceroutes.</div>
      </div>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="exclusion-source-cidr" className="form-label">Source Address or Network</label>
          <input type="text" className="form-control" id="exclusion-source-cidr" placeholder="192.168.0.10"
                 value={values.source_cidr} onChange={update("source_cidr")} />
          <div className="form-text">The redirect sender or the tracing host. A single address or a CIDR network.</div>
        </div>

        <div className="col-md-6 mb-3">
          <label htmlFor="exclusion-source-mac" className="form-label">Source MAC</label>
          <input type="text" className="form-control" id="exclusion-source-mac" placeholder="74:56:3C:43:77:AF"
                 value={values.source_mac} onChange={update("source_mac")} />
          <div className="form-text">Use the MAC for a device whose address changes.</div>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="exclusion-gateway-cidr" className="form-label">Advertised Gateway <small>Redirects only</small></label>
          <input type="text" className="form-control" id="exclusion-gateway-cidr" placeholder="192.168.0.254"
                 value={values.gateway_cidr} onChange={update("gateway_cidr")} />
          <div className="form-text">
            Only exclude redirects that point at this gateway, for a known two-router setup. A gateway criterion
            never matches a traceroute.
          </div>
        </div>
      </div>

      {submitError ? <div className="alert alert-danger">{submitError}</div> : null}

      <button className="btn btn-primary" type="submit" disabled={!ready()}>
        {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span> : submitText}
      </button>
    </form>
  )

}
