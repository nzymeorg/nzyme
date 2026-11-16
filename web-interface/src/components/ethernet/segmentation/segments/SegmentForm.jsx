import React, {useEffect, useState} from "react";
import TapsService from "../../../../services/TapsService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import FormSubmitErrorMessage from "../../../misc/FormSubmitErrorMessage";
import {apiErrorMessage} from "../../../../util/Tools";
import {asnProblem, networkProblem} from "../NetworkProblems";

const tapsService = new TapsService();

const EMPTY = {
  kind: "INTERNAL",
  name: "",
  description: "",
  notes: "",
  members: [{type: "CIDR", value: ""}],
  tap_uuids: []
};

export const membersToRows = (members) => {
  if (!members || members.length === 0) {
    return [{type: "CIDR", value: ""}];
  }

  return members.map((m) => {
    return m.type === "ASN" ? {type: "ASN", value: String(m.asn)} : {type: "CIDR", value: m.network ? m.network : ""};
  });
}

const memberProblem = (row, external) => {
  if (row.type === "ASN") {
    return asnProblem(row.value);
  }

  const problem = networkProblem(row.value, external ? "203.0.113.0/24" : "10.1.0.0/16");
  if (problem) {
    return problem;
  }

  if (/\/0+$/.test(row.value.trim())) {
    return "A network that covers every address cannot be " + (external ? "an external network." : "a segment.");
  }

  return null;
}

const rowsToMembers = (rows) => {
  return rows
    .filter((row) => row.value.trim() !== "")
    .map((row) => {
      return row.type === "ASN" ? {type: "ASN", asn: parseInt(row.value, 10)} : {type: "CIDR", network: row.value.trim()};
    });
}

export default function SegmentForm({organizationId, tenantId, initialValues = undefined, submitText = undefined,
                                      onSubmit, kindFixed = false, inaccessibleTaps = 0}) {

  const [values, setValues] = useState({...EMPTY, ...(initialValues ? initialValues : {})});
  const [taps, setTaps] = useState(null);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const external = values.kind === "EXTERNAL";

  useEffect(() => {
    tapsService.findAllTapsHighLevel(organizationId, tenantId, (r) => setTaps(r.data.taps));
  }, [organizationId, tenantId]);

  const update = (field) => (e) => {
    setValues({...values, [field]: e.target.value});
  }

  const updateKind = (kind) => {
    const members = kind === "EXTERNAL" ? values.members
      : values.members.map((row) => row.type === "ASN" ? {type: "CIDR", value: ""} : row);

    setValues({...values, kind: kind, members: members});
  }

  // Leaving a row and clicking a button are two events in a row. Each works on what the one before left.
  const updateMember = (index, patch) => {
    setValues((current) => {
      return {...current, members: current.members.map((row, i) => i === index ? {...row, ...patch} : row)};
    });
  }

  const updateMemberValue = (index, row, value) => {
    const cleaned = row.type === "ASN" ? value.replace(/[^0-9]/g, "") : value.replace(/[^0-9a-fA-F.:/]/g, "");
    updateMember(index, {value: cleaned});
  }

  const addMember = (e) => {
    e.preventDefault();
    setValues((current) => {
      return {...current, members: [...current.members, {type: "CIDR", value: ""}]};
    });
  }

  const removeMember = (e, index) => {
    e.preventDefault();

    setValues((current) => {
      const members = current.members.filter((row, i) => i !== index);
      return {...current, members: members.length > 0 ? members : [{type: "CIDR", value: ""}]};
    });
  }

  const toggleTap = (uuid) => {
    const selected = values.tap_uuids.includes(uuid)
      ? values.tap_uuids.filter((t) => t !== uuid) : [...values.tap_uuids, uuid];

    setValues({...values, tap_uuids: selected});
  }

  const formIsReady = () => {
    return values.name.trim().length > 0 && rowsToMembers(values.members).length > 0
      && !values.members.some((row) => memberProblem(row, external)) && !isSubmitting;
  }

  const submit = (e) => {
    e.preventDefault();

    if (!formIsReady()) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    const payload = {
      kind: values.kind,
      name: values.name.trim(),
      description: values.description.trim() === "" ? null : values.description.trim(),
      notes: values.notes.trim() === "" ? null : values.notes,
      tap_uuids: values.tap_uuids.length > 0 ? values.tap_uuids : null,
      members: rowsToMembers(values.members)
    };

    onSubmit(payload, (error) => {
      setIsSubmitting(false);
      setSubmitError(apiErrorMessage(error, external ? "Could not save the external network." : "Could not save the segment."));
    });
  }

  const tapSelection = () => {
    if (!taps) {
      return <LoadingSpinner />
    }

    const deleted = values.tap_uuids.filter((uuid) => !taps.some((tap) => tap.uuid === uuid))
      .map((uuid) => { return {uuid: uuid, name: "Deleted tap", is_online: true} });
    const choices = taps.concat(deleted);

    if (choices.length === 0) {
      return <div className="text-muted">No taps.</div>
    }

    return choices.map((tap) => {
      return (
        <div className="form-check" key={tap.uuid}>
          <input className="form-check-input" type="checkbox" id={"segment-tap-" + tap.uuid}
                 checked={values.tap_uuids.includes(tap.uuid)} onChange={() => toggleTap(tap.uuid)} />
          <label className="form-check-label" htmlFor={"segment-tap-" + tap.uuid}>
            {tap.name}{' '}
            {tap.is_online ? null : <span className="text-muted">(offline)</span>}
          </label>
        </div>
      )
    });
  }

  return (
    <form onSubmit={submit}>
      <div className="mb-3">
        <label className="form-label">Type</label>
        <div className="form-check">
          <input className="form-check-input" type="radio" name="segment-kind" id="segment-kind-internal"
                 checked={!external} disabled={kindFixed} onChange={() => updateKind("INTERNAL")} />
          <label className="form-check-label" htmlFor="segment-kind-internal">
            Segment <span className="text-muted">A part of your own network.</span>
          </label>
        </div>
        <div className="form-check">
          <input className="form-check-input" type="radio" name="segment-kind" id="segment-kind-external"
                 checked={external} disabled={kindFixed} onChange={() => updateKind("EXTERNAL")} />
          <label className="form-check-label" htmlFor="segment-kind-external">
            External Network <span className="text-muted">Addresses outside of your network, like a partner or a
            cloud provider.</span>
          </label>
        </div>
        {kindFixed ? <div className="form-text">The type cannot be changed.</div> : null}
      </div>

      <div className="mb-3">
        <label htmlFor="segment-name" className="form-label">Name</label>
        <input type="text" className="form-control" id="segment-name" maxLength={12}
               value={values.name} onChange={update("name")} />
        <div className="form-text">
          A short name, not longer than 12 characters. It appears in the overview, in policies and in findings.
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="segment-description" className="form-label">Description <small>Optional</small></label>
        <input type="text" className="form-control" id="segment-description" maxLength={32}
               value={values.description} onChange={update("description")} />
        <div className="form-text">
          A short description, not longer than 32 characters. Longer descriptions should be added to
          the <i>notes</i> field below.
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="segment-notes" className="form-label">Notes <small>Optional</small></label>
        <textarea className="form-control" id="segment-notes" style={{height: 200}}
                  value={values.notes} onChange={update("notes")} />
        <div className="form-text">
          Notes about this {external ? "external network" : "segment"}. <strong>Markdown is supported.</strong>
        </div>
      </div>

      <h5 className="mt-4">Networks</h5>
      <p className="text-muted">
        {external ?
          "The addresses that make up this external network: networks in CIDR notation, autonomous systems, or both."
          : "The networks in CIDR notation that make up this segment."}{' '}
        An address can only be part of one {external ? "external network" : "segment"}.
      </p>

      {values.members.map((row, i) => {
        const problem = row.touched ? memberProblem(row, external) : null;

        return (
          <div className="mb-2" key={i}>
            <div className="input-group">
              {external ?
                <select className="form-select" style={{maxWidth: 220}} aria-label="Defined by"
                        value={row.type}
                        onChange={(e) => updateMember(i, {type: e.target.value, value: "", touched: false})}>
                  <option value="CIDR">Network</option>
                  <option value="ASN">Autonomous system</option>
                </select> : null}
              {row.type === "ASN" ? <span className="input-group-text">AS</span> : null}
              <input type="text" className={"form-control" + (problem ? " is-invalid" : "")}
                     aria-label={row.type === "ASN" ? "Autonomous system number" : "Network"}
                     placeholder={row.type === "ASN" ? "64512" : (external ? "203.0.113.0/24" : "10.1.0.0/16")}
                     value={row.value}
                     onChange={(e) => updateMemberValue(i, row, e.target.value)}
                     onBlur={(e) => updateMember(i, {touched: e.target.value.trim() !== ""})} />
              <button className="btn btn-outline-secondary" type="button" title="Remove"
                      onClick={(e) => removeMember(e, i)}>
                <i className="fa-solid fa-xmark"></i>
              </button>
            </div>
            {problem ? <div className="text-danger small mt-1">{problem}</div> : null}
          </div>
        )
      })}

      <div className="mt-3 mb-3">
        <button className="btn btn-sm btn-outline-secondary" type="button" onClick={addMember}>
          Add {external ? "Network or Autonomous System" : "Network"}
        </button>
      </div>

      <h5 className="mt-4">Limit to Taps <small className="text-muted">Optional</small></h5>
      <p className="text-muted">Applies only to traffic recorded by the checked taps. None checked: all taps.</p>

      <div className="mb-3">
        {tapSelection()}
        {inaccessibleTaps > 0 ?
          <div className="form-text">
            Also limited to {inaccessibleTaps} {inaccessibleTaps === 1 ? "tap" : "taps"} you have no access to.
            This is not changed.
          </div> : null}
      </div>

      <button className="btn btn-primary" type="submit" disabled={!formIsReady()}>
        {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span>
          : (submitText ? submitText : (external ? "Create External Network" : "Create Segment"))}
      </button>

      <FormSubmitErrorMessage message={submitError} />
    </form>
  )

}
