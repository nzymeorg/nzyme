import React, {useState} from "react";
import {cidrToRange, formatContextName, isValidCIDR} from "../../../util/Tools";
import FormSubmitErrorMessage from "../../misc/FormSubmitErrorMessage";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";

export default function NetworkContextForm(props) {

  const [organizationId, tenantId] = useSelectedTenant();

  const submitText = props.submitText;
  const onSubmit = props.onSubmit;
  const errorMessage = props.errorMessage;
  const cidrDisabled = props.cidrDisabled;

  // Optional.
  const onDelete = props.onDelete;

  const [cidr, setCidr] = useState(
      (props.cidr && isValidCIDR(props.cidr)) ? props.cidr : ""
  );
  const [name, setName] = useState(props.name ? formatContextName(props.name) : "");
  const [description, setDescription] = useState(props.description ? props.description : "");
  const [notes, setNotes] = useState(props.notes ? props.notes : "");

  const [formSubmitting, setFormSubmitting] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const formIsReady = () => {
    return isValidCIDR(cidr)
  }

  const submit = () => {
    setFormSubmitting(true);

    onSubmit(cidr, name, description, notes, organizationId, tenantId, () => {
      setFormSubmitting(false);
    });
  }

  const cidrCalculator = () => {
    const result = cidrToRange(cidr);

    let from, to;
    if (result) {
      from = result.from;
      to = result.to;
    } else {
      from = <span className="text-muted">n/a</span>
      to = <span className="text-muted">n/a</span>
    }

    return (
      <dl className="cidr-calculator">
        <dt>From</dt>
        <dd className="machine-data">{from}</dd>
        <dt>To</dt>
        <dd className="machine-data">{to}</dd>
      </dl>
    )
  }

  return (
    <React.Fragment>
      <div className="mb-3">
        <label htmlFor="cidr" className="form-label">CIDR <small>Required</small></label>
        <input type="text" className="form-control" id="cidr"
               disabled={cidrDisabled}
               value={cidr}
               onChange={(e) => { setCidr(e.target.value.replace(/[^0-9a-fA-F.:/]/g, "")) }}/>
        <div className="form-text">
          The network an IP address must fall within for this context to be applied. Can be a public or
          private IPv4 or IPv6 network, for example <code>192.168.0.0/24</code> or <code>2001:db8::/32</code>.
          The address must be the start of the range, so use <code>192.168.0.0/24</code> rather
          than <code>192.168.0.5/24</code>. To match a single address, leave out the prefix length
          (e.g. <code>192.168.0.1</code>).
        </div>
      </div>

      <div className="mb-3">
        {cidrCalculator()}

        <br />
      </div>

      <hr />

      <div className="mb-3">
        <label htmlFor="name" className="form-label">Name</label>
        <input type="text" className="form-control" id="name" maxLength={12}
               value={name} onChange={(e) => { setName(formatContextName(e.target.value)) }} />
        <div className="form-text">
          A short name describing the network. This name will appear next to the matched IP addresses. It cannot be
          longer than 12 characters, cannot include special characters except underscores and must be uppercase.
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="description" className="form-label">Description <small>Optional</small></label>
        <input type="text" className="form-control" id="description" maxLength={32}
               value={description} onChange={(e) => { setDescription(e.target.value) }} />
        <div className="form-text">
          A short description of the network, not longer than 32 characters. Longer descriptions should be added
          to the <i>notes</i> field below.
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="description" className="form-label">Notes <small>Optional</small></label>
        <textarea type="text" className="form-control" id="description"
                  style={{height: 200}}
                  value={notes} onChange={(e) => { setNotes(e.target.value) }} />
        <div className="form-text">
          Notes about this network. <strong>Markdown is supported.</strong>
        </div>
      </div>

      <button className="btn btn-primary" onClick={submit} disabled={!formIsReady() || formSubmitting || deleting}>
        {formSubmitting ? "Please wait ..." : submitText}
      </button>

      { onDelete && props.name && <button className="btn btn-danger float-end"
                            disabled={formSubmitting || deleting}
                            onClick={() => { setDeleting(true); onDelete();}}>
        {deleting ? "Please wait ..." : "Delete Context"}</button>
      }

      <FormSubmitErrorMessage message={errorMessage} />
    </React.Fragment>
  )

}