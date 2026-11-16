import React, {useState} from "react";
import {ruleServiceLabel} from "../SegmentationFormat";
import {sameService, serviceOfDraft} from "./PolicyDefinition";
import PolicyEntries from "./PolicyEntries";

const EMPTY = {l4_type: "", ports: "", protocol: ""};

export default function PolicyServicesEditor({services, onChange, options, idPrefix, anyLabel = "Any service",
                                               anyTitle = "Nothing is listed, so the rule applies to any service."}) {

  const [draft, setDraft] = useState(EMPTY);

  const [left, setLeft] = useState(false);

  const service = serviceOfDraft(draft);
  const portsInvalid = left && draft.ports.trim() !== "" && !service;

  const add = () => {
    if (!service) {
      setLeft(draft.ports.trim() !== "");
      return;
    }

    if (!services.some((s) => sameService(s, service))) {
      onChange([...services, service]);
    }

    setDraft(EMPTY);
    setLeft(false);
  }

  const remove = (e, index) => {
    e.preventDefault();
    onChange(services.filter((s, i) => i !== index));
  }

  // Four parts, always: label, inputs, help text and list. The rule editor lines them up with its other sides.
  return (
    <React.Fragment>
      <label className="form-label policy-rule-label" htmlFor={idPrefix + "-ports"}>Services</label>

      <div>
        <div className="input-group input-group-sm">
          <select className="form-select" aria-label="Transport protocol" style={{flex: "0 0 140px"}}
                  value={draft.l4_type} onChange={(e) => setDraft({...draft, l4_type: e.target.value})}>
            <option value="">TCP or UDP</option>
            <option value="TCP">TCP</option>
            <option value="UDP">UDP</option>
          </select>
          <input type="text" className={"form-control" + (portsInvalid ? " is-invalid" : "")} id={idPrefix + "-ports"}
                 aria-label="Port or port range" placeholder="Any port" style={{flex: "1 1 80px"}}
                 value={draft.ports}
                 onChange={(e) => setDraft({...draft, ports: e.target.value.replace(/[^0-9\- ]/g, "")})}
                 onBlur={(e) => setLeft(e.target.value.trim() !== "")}
                 onKeyDown={(e) => {
                   if (e.key === "Enter") {
                     e.preventDefault();
                     add();
                   }
                 }} />
          <select className="form-select" aria-label="Detected protocol" style={{flex: "2 1 130px"}}
                  value={draft.protocol} onChange={(e) => setDraft({...draft, protocol: e.target.value})}>
            <option value="">Any protocol</option>
            {options.protocols.map((p) => <option key={p} value={p}>{p}</option>)}
          </select>
          <button className="btn btn-outline-secondary" type="button" disabled={!service} onClick={add}>
            Add
          </button>
        </div>

        {portsInvalid ?
          <div className="text-danger small mt-1">
            Not a port like <code>22</code> or a port range like <code>8000-8099</code>.
          </div> : null}
      </div>

      <div className="form-text">
        The protocol is what the taps detected in the traffic, whatever the port.
      </div>

      <div>
        <PolicyEntries entries={services.map((s) => {
                         return {name: ruleServiceLabel(s), detail: null};
                       })}
                       emptyLabel={anyLabel}
                       emptyTitle={anyTitle}
                       onRemove={remove} />
      </div>
    </React.Fragment>
  )

}
