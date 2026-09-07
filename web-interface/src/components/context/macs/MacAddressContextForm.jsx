import React, {useState} from "react";
import {formatAssetName, isValidMACAddress} from "../../../util/Tools";
import FormSubmitErrorMessage from "../../misc/FormSubmitErrorMessage";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";

function MacAddressContextForm(props) {

  const [organizationId, tenantId] = useSelectedTenant();

  const submitText = props.submitText;
  const onSubmit = props.onSubmit;
  const errorMessage = props.errorMessage;
  const macAddressDisabled = props.macAddressDisabled;

  // Optional.
  const onDelete = props.onDelete;

  const [macAddress, setMacAddress] = useState(
      (props.macAddress && isValidMACAddress(props.macAddress)) ? props.macAddress.toUpperCase() : ""
  );
  const [name, setName] = useState(props.name ? formatAssetName(props.name) : "");
  const [description, setDescription] = useState(props.description ? props.description : "");
  const [notes, setNotes] = useState(props.notes ? props.notes : "");

  const [formSubmitting, setFormSubmitting] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const formIsReady = () => {
    return isValidMACAddress(macAddress)
  }

  const submit = () => {
    setFormSubmitting(true);

    onSubmit(macAddress.trim(), name, description, notes, organizationId, tenantId, () => {
      setFormSubmitting(false);
    });
  }

  return (
    <React.Fragment>
      <div className="mb-3">
        <label htmlFor="macAddress" className="form-label">MAC Address <small>Required</small></label>
        <input type="text" className="form-control" id="macAddress"
               disabled={macAddressDisabled}
               value={macAddress} onChange={(e) => { setMacAddress(e.target.value) }} />
        <div className="form-text">The MAC address you want to add context to.</div>
      </div>

      <hr />

      <div className="mb-3">
        <label htmlFor="name" className="form-label">Name</label>
        <input type="text" className="form-control" id="name" maxLength={12}
               value={name} onChange={(e) => { setName(formatAssetName(e.target.value)) }} />
        <div className="form-text">
          A short name describing the MAC address. This name will appear next to the address. It cannot be longer than
          12 characters, cannot include special characters except underscores and must be uppercase.
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="description" className="form-label">Description <small>Optional</small></label>
        <input type="text" className="form-control" id="description" maxLength={32}
               value={description} onChange={(e) => { setDescription(e.target.value) }} />
        <div className="form-text">
          A short description of the MAC address, not longer than 32 characters. Longer descriptions should be added
          to the <i>notes</i> field below.
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="description" className="form-label">Notes <small>Optional</small></label>
        <textarea type="text" className="form-control" id="description"
                  style={{height: 200}}
                  value={notes} onChange={(e) => { setNotes(e.target.value) }} />
        <div className="form-text">
          Notes about this MAC address. <strong>Markdown is supported.</strong>
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

export default MacAddressContextForm;