import React, {useState} from "react";
import {Link} from "react-router-dom";
import WithPermission from "../../../misc/WithPermission";
import ApiRoutes from "../../../../util/ApiRoutes";
import {SEGMENT_KINDS, SELECTOR_TYPES} from "../SegmentationFormat";
import {draftProblem, sameSelector, selectorOfDraft} from "./PolicyDefinition";
import {PolicySelector} from "./PolicyRuleText";
import PolicyAssetSearch from "./PolicyAssetSearch";
import PolicyEntries from "./PolicyEntries";

// What can be added, in the order it is offered. Leaving the list empty is how anything is selected.
const TYPES = ["SEGMENT", "TAG", "INTERNAL", "EXTERNAL", "UNASSIGNED", "ASSET", "ADDRESS", "ASN", "COUNTRY"];

const DESTINATION_TYPES = ["SEGMENT", "TAG", "INTERNAL", "EXTERNAL", "UNASSIGNED", "BROADCAST", "ASSET", "ADDRESS", "ASN",
  "COUNTRY"];

const HELP = {
  ASSET: "Search by IP address, MAC address, hostname or asset ID.",
  ADDRESS: "One address like 10.1.0.5 or a network in CIDR notation like 10.1.0.0/16.",
  ASN: "Every external address that is announced by the autonomous system.",
  COUNTRY: "Every external address that is located in the country, as a two-letter code like DE.",
  BROADCAST: "Traffic to broadcast or multicast addresses, like mDNS, SSDP, NetBIOS or DHCP. Only evaluated if " +
    "the scope lists it."
};

const detailOf = (selector, options) => {
  switch (selector.type) {
    case "SEGMENT": {
      const segment = options.segments.find((s) => s.uuid === selector.value);
      if (!segment) {
        return "Segment";
      }

      const kind = SEGMENT_KINDS[segment.kind].title;
      return segment.description ? kind + " \u00b7 " + segment.description : kind;
    }
    case "TAG": {
      const tag = options.tags.find((t) => t.uuid === selector.value);
      return tag ? "Tag \u00b7 " + tag.asset_count + (tag.asset_count === 1 ? " asset" : " assets") : "Tag";
    }
    case "ASSET":
      return "Asset";
    case "ADDRESS":
      return selector.value && selector.value.includes("/") ? "Network" : "Address";
    case "ASN":
      return "Autonomous system";
    case "COUNTRY":
      return "Country";
    default:
      return SELECTOR_TYPES[selector.type] ? SELECTOR_TYPES[selector.type].description : null;
  }
}

export default function PolicySelectorsEditor({title, anyLabel, anyTitle = undefined, selectors, onChange, options,
                                                organizationId, tenantId, idPrefix, destinations = false}) {

  const types = destinations ? DESTINATION_TYPES : TYPES;

  const [draft, setDraft] = useState({type: "SEGMENT", value: ""});

  const [left, setLeft] = useState(false);

  const problem = left ? draftProblem(draft) : null;

  const leave = (e) => setLeft(e.target.value.trim() !== "");

  const add = (selector) => {
    if (!selector) {
      setLeft(String(draft.value ? draft.value : "").trim() !== "");
      return;
    }

    if (!selectors.some((s) => sameSelector(s, selector))) {
      onChange([...selectors, selector]);
    }

    setDraft({type: draft.type, value: ""});
    setLeft(false);
  }

  const remove = (e, index) => {
    e.preventDefault();
    onChange(selectors.filter((s, i) => i !== index));
  }

  const onEnter = (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      add(selectorOfDraft(draft));
    }
  }

  const segmentGroup = (label, kind) => {
    const segments = options.segments.filter((s) => s.kind === kind);
    if (segments.length === 0) {
      return null;
    }

    return (
      <optgroup label={label}>
        {segments.map((s) => {
          return <option key={s.uuid} value={s.uuid}>{s.name}{s.description ? " (" + s.description + ")" : ""}</option>
        })}
      </optgroup>
    )
  }

  const valueInput = () => {
    switch (draft.type) {
      case "SEGMENT":
        return (
          <select className="form-select" aria-label="Segment" value={draft.value}
                  onChange={(e) => setDraft({type: "SEGMENT", value: e.target.value})}>
            <option value="">Select a segment ...</option>
            {segmentGroup("Segments", "INTERNAL")}
            {segmentGroup("External Networks", "EXTERNAL")}
          </select>
        )
      case "TAG":
        return (
          <select className="form-select" aria-label="Tag" value={draft.value}
                  onChange={(e) => setDraft({type: "TAG", value: e.target.value})}>
            <option value="">Select a tag ...</option>
            {options.tags.map((t) => {
              return <option key={t.uuid} value={t.uuid}>{t.name} ({t.asset_count} {t.asset_count === 1 ? "asset" : "assets"})</option>
            })}
          </select>
        )
      case "ADDRESS":
        return <input type="text" className={"form-control" + (problem ? " is-invalid" : "")}
                      aria-label="Address or network" placeholder="10.1.0.0/16"
                      value={draft.value} onKeyDown={onEnter} onBlur={leave}
                      onChange={(e) => setDraft({type: "ADDRESS", value: e.target.value.replace(/[^0-9a-fA-F.:/]/g, "")})} />
      case "ASN":
        return (
          <React.Fragment>
            <span className="input-group-text">AS</span>
            <input type="text" className={"form-control" + (problem ? " is-invalid" : "")}
                   aria-label="Autonomous system number" placeholder="64512"
                   value={draft.value} onKeyDown={onEnter} onBlur={leave}
                   onChange={(e) => setDraft({type: "ASN", value: e.target.value.replace(/[^0-9]/g, "")})} />
          </React.Fragment>
        )
      case "COUNTRY":
        return <input type="text" className="form-control" aria-label="Country code" placeholder="DE" maxLength={2}
                      value={draft.value} onKeyDown={onEnter}
                      onChange={(e) => setDraft({type: "COUNTRY", value: e.target.value.replace(/[^a-zA-Z]/g, "").toUpperCase()})} />
      default:
        return null;
    }
  }

  const help = () => {
    if (draft.type === "SEGMENT" && options.segments.length === 0) {
      return <span>No segments defined yet. <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX} target="_blank">Create one</Link> first.</span>
    }

    if (draft.type === "TAG" && options.tags.length === 0) {
      return (
        <span>
          No tags defined yet.
          <WithPermission permission="ethernet_assets_manage">
            {' '}<Link to={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX} target="_blank">Create one</Link> first.
          </WithPermission>
        </span>
      )
    }

    if (HELP[draft.type]) {
      return HELP[draft.type];
    }

    return SELECTOR_TYPES[draft.type].description ? SELECTOR_TYPES[draft.type].description : null;
  }

  // Four parts, always: label, inputs, help text and list. The rule editor lines them up with its other sides.
  return (
    <React.Fragment>
      <label className="form-label policy-rule-label" htmlFor={idPrefix + "-type"}>{title}</label>

      <div>
        <div className="input-group input-group-sm">
          <select className="form-select" id={idPrefix + "-type"}
                  style={{maxWidth: SELECTOR_TYPES[draft.type].hasValue ? 170 : 240}}
                  value={draft.type} onChange={(e) => { setDraft({type: e.target.value, value: ""}); setLeft(false); }}>
            {types.map((type) => <option key={type} value={type}>{SELECTOR_TYPES[type].title}</option>)}
          </select>
          {valueInput()}
          {draft.type === "ASSET" ? null :
            <button className="btn btn-outline-secondary" type="button" disabled={!selectorOfDraft(draft)}
                    onClick={() => add(selectorOfDraft(draft))}>
              Add
            </button>}
        </div>

        {draft.type === "ASSET" ?
          <div className="mt-2">
            <PolicyAssetSearch organizationId={organizationId}
                               tenantId={tenantId}
                               idPrefix={idPrefix}
                               onSelect={(uuid) => add({type: "ASSET", value: uuid})} />
          </div> : null}

        {problem ? <div className="text-danger small mt-1">{problem}</div> : null}
      </div>

      <div className="form-text">{help()}</div>

      <div>
        <PolicyEntries entries={selectors.map((selector) => {
                         return {
                           name: selector.type === "ADDRESS" ? <span className="machine-data">{selector.value}</span>
                             : <PolicySelector selector={selector} options={options} plain={true} />,
                           detail: detailOf(selector, options)
                         };
                       })}
                       emptyLabel={anyLabel}
                       emptyTitle={anyTitle ? anyTitle : "Nothing is listed, so the rule applies to " + anyLabel.toLowerCase() + "."}
                       onRemove={remove} />
      </div>
    </React.Fragment>
  )

}
