import React, {useState} from "react";
import {selectorLabel} from "../SegmentationFormat";
import {networkProblem} from "../NetworkProblems";
import {servicesText} from "./PolicyDefinition";
import PolicySelectorsEditor from "./PolicySelectorsEditor";
import PolicyServicesEditor from "./PolicyServicesEditor";

const TEMPLATES = [
  {
    key: "ONLY_ACCEPTS",
    title: "Only accepts ...",
    summary: "Every attempted or successful connection to the destination you pick will be reported as a " +
      "finding, except for the connections you allow. Example: a group of servers."
  },
  {
    key: "NEVER_ACCEPTS",
    title: "Never accepts ...",
    summary: "Every attempted or successful connection to the service you pick on the destination you pick " +
      "will be reported as a finding. Example: SSH on a group of servers."
  },
  {
    key: "ISOLATE",
    title: "Isolate a segment",
    summary: "Every attempted or successful connection from the segment you pick to an internal address will " +
      "be reported as a finding. Example: a guest network."
  },
  {
    key: "CLIENT_ISOLATION",
    title: "Client isolation",
    summary: "Every attempted or successful connection between two addresses of the segment you pick will be " +
      "reported as a finding. Example: a guest WiFi."
  },
  {
    key: "RESTRICT_OUTBOUND",
    title: "Restrict outbound",
    summary: "Every attempted or successful connection from the source you pick to an external address will " +
      "be reported as a finding, except for the connections you allow. Example: servers that have no reason " +
      "to connect to the Internet."
  },
  {
    key: "RESTRICT_INBOUND",
    title: "Restrict inbound",
    summary: "Every attempted or successful connection from an external address to an internal address will " +
      "be reported as a finding, except for the connections you allow. Example: a network that publishes only " +
      "a few services to the Internet."
  },
  {
    key: "NEVER_ANNOUNCES",
    title: "Never announces ...",
    summary: "Everything the source you pick sends to a broadcast or multicast address will be reported as a " +
      "finding, except for what you allow. Example: servers that have no reason to announce themselves over " +
      "mDNS, LLMNR, NetBIOS or SSDP."
  }
];

const NAME_MAX_LENGTH = 64;

export default function PolicyTemplatesDialog({options, organizationId, tenantId, onApply, onClose}) {

  const [selected, setSelected] = useState(null);

  const [sources, setSources] = useState([]);
  const [destinations, setDestinations] = useState([]);
  const [services, setServices] = useState([]);
  const [segment, setSegment] = useState("");
  const [gateway, setGateway] = useState("");

  const [gatewayLeft, setGatewayLeft] = useState(false);
  const gatewayProblem = networkProblem(gateway, "10.1.0.1", true);

  const internalSegments = options.segments.filter((s) => s.kind === "INTERNAL");

  const names = (selectors) => selectors.map((s) => selectorLabel(s, options)).join(", ");

  const ready = () => {
    switch (selected) {
      case "ONLY_ACCEPTS":
        return destinations.length > 0;
      case "NEVER_ACCEPTS":
        return destinations.length > 0 && services.length > 0;
      case "ISOLATE":
        return segment !== "";
      case "CLIENT_ISOLATION":
        return segment !== "" && !gatewayProblem;
      case "RESTRICT_OUTBOUND":
      case "NEVER_ANNOUNCES":
        return sources.length > 0;
      case "RESTRICT_INBOUND":
        return true;
      default:
        return false;
    }
  }

  const definition = () => {
    const segmentSelector = {type: "SEGMENT", value: segment};

    switch (selected) {
      case "ONLY_ACCEPTS":
        return {
          name: "Access to " + names(destinations),
          scope: {sources: [], destinations: destinations, services: []},
          allowed: []
        };
      case "NEVER_ACCEPTS":
        return {
          name: "No " + servicesText(services) + " to " + names(destinations),
          scope: {sources: [], destinations: destinations, services: services},
          allowed: []
        };
      case "ISOLATE":
        return {
          name: "Isolate " + selectorLabel(segmentSelector, options),
          scope: {sources: [segmentSelector], destinations: [{type: "INTERNAL", value: null}], services: []},
          allowed: []
        };
      case "CLIENT_ISOLATION":
        return {
          name: "Client isolation in " + selectorLabel(segmentSelector, options),
          scope: {sources: [segmentSelector], destinations: [segmentSelector], services: []},
          allowed: gateway.trim() === "" ? []
            : [{sources: [], destinations: [{type: "ADDRESS", value: gateway.trim()}], services: []}]
        };
      case "RESTRICT_INBOUND":
        return {
          name: "Inbound from the Internet",
          scope: {
            sources: [{type: "EXTERNAL", value: null}],
            destinations: [{type: "INTERNAL", value: null}],
            services: []
          },
          allowed: []
        };
      case "NEVER_ANNOUNCES":
        return {
          name: "No broadcast from " + names(sources),
          scope: {sources: sources, destinations: [{type: "BROADCAST", value: null}], services: []},
          allowed: []
        };
      default:
        return {
          name: "Outbound from " + names(sources),
          scope: {sources: sources, destinations: [{type: "EXTERNAL", value: null}], services: []},
          allowed: []
        };
    }
  }

  const apply = (e) => {
    e.preventDefault();

    if (!ready()) {
      return;
    }

    const result = definition();
    if (onApply({...result, name: result.name.substring(0, NAME_MAX_LENGTH)})) {
      onClose();
    }
  }

  const segmentSelect = () => {
    if (internalSegments.length === 0) {
      return <div className="alert alert-warning mb-0">No segments defined yet. Create a segment first.</div>
    }

    return (
      <React.Fragment>
        <label className="form-label" htmlFor="policy-template-segment">Segment</label>
        <select className="form-select form-select-sm" id="policy-template-segment"
                value={segment} onChange={(e) => setSegment(e.target.value)}>
          <option value="">Select a segment ...</option>
          {internalSegments.map((s) => {
            return <option key={s.uuid} value={s.uuid}>{s.name}{s.description ? " (" + s.description + ")" : ""}</option>
          })}
        </select>
      </React.Fragment>
    )
  }

  const fields = () => {
    switch (selected) {
      case "ONLY_ACCEPTS":
      case "NEVER_ACCEPTS":
        return (
          <React.Fragment>
            <div className="col-12 mt-3">
              <PolicySelectorsEditor title="Destination"
                                     anyLabel="No destination picked"
                                     anyTitle="Add at least one destination."
                                     destinations={true}
                                     selectors={destinations}
                                     onChange={setDestinations}
                                     options={options}
                                     organizationId={organizationId}
                                     tenantId={tenantId}
                                     idPrefix="policy-template-destinations" />
            </div>
            {selected === "NEVER_ACCEPTS" ?
              <div className="col-12 mt-3">
                <PolicyServicesEditor services={services}
                                      onChange={setServices}
                                      options={options}
                                      anyLabel="No service picked"
                                      anyTitle="Add at least one service."
                                      idPrefix="policy-template-services" />
              </div> : null}
          </React.Fragment>
        )
      case "ISOLATE":
        return (
          <div className="col-md-6 mt-3">
            {segmentSelect()}
            {internalSegments.length > 0 ?
              <div className="form-text">
                Internal addresses include the ones of the segment itself, like its gateway.
              </div> : null}
          </div>
        )
      case "CLIENT_ISOLATION":
        return (
          <React.Fragment>
            <div className="col-md-6 mt-3">{segmentSelect()}</div>
            <div className="col-md-6 mt-3">
              <label className="form-label" htmlFor="policy-template-gateway">Gateway Address <small>Optional</small></label>
              <input type="text" id="policy-template-gateway"
                     className={"form-control form-control-sm" + (gatewayLeft && gatewayProblem ? " is-invalid" : "")}
                     placeholder="10.1.0.1"
                     value={gateway}
                     onChange={(e) => setGateway(e.target.value.replace(/[^0-9a-fA-F.:/]/g, ""))}
                     onBlur={(e) => setGatewayLeft(e.target.value.trim() !== "")}
                     onKeyDown={(e) => {
                       if (e.key === "Enter") {
                         setGatewayLeft(true);
                         apply(e);
                       }
                     }} />
              {gatewayLeft && gatewayProblem ?
                <div className="text-danger small mt-1">{gatewayProblem}</div> : null}
              <div className="form-text">
                Connections to this address will not be reported as a finding.
              </div>
            </div>
          </React.Fragment>
        )
      case "RESTRICT_OUTBOUND":
      case "NEVER_ANNOUNCES":
        return (
          <div className="col-12 mt-3">
            <PolicySelectorsEditor title="Source"
                                   anyLabel="No source picked"
                                   anyTitle="Add at least one source."
                                   selectors={sources}
                                   onChange={setSources}
                                   options={options}
                                   organizationId={organizationId}
                                   tenantId={tenantId}
                                   idPrefix="policy-template-sources" />
          </div>
        )
      default:
        return null;
    }
  }

  return (
    <React.Fragment>
      <div className="modal-backdrop fade show"></div>
      <div className="modal fade show" style={{display: "block"}}>
        <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
          <div className="modal-content">
            <div className="modal-header">
              <h1 className="modal-title fs-5">Policy Templates</h1>
              <button type="button" className="btn-close" onClick={onClose}></button>
            </div>
            <div className="modal-body">
              <p className="text-muted">
                Fills in the scope and the name of the policy form. Nothing is saved until the policy is created.
              </p>

              <div className="list-group">
                {TEMPLATES.map((template) => {
                  const id = "policy-template-" + template.key;
                  const templateFields = selected === template.key ? fields() : null;

                  return (
                    <div key={template.key} className="list-group-item">
                      <div className="form-check mb-0">
                        <input className="form-check-input" type="radio" name="policy-template" id={id}
                               checked={selected === template.key}
                               onChange={() => setSelected(template.key)} />
                        <label className="form-check-label d-block" htmlFor={id}>
                          <strong>{template.title}</strong>
                          <span className="d-block text-muted small">{template.summary}</span>
                        </label>
                      </div>

                      {templateFields ?
                        <div className="ps-4 pb-2">
                          <div className="row">{templateFields}</div>
                        </div> : null}
                    </div>
                  )
                })}
              </div>
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={onClose}>Close</button>
              <button type="button" className="btn btn-primary" disabled={!ready()} onClick={apply}>
                Use Template
              </button>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
