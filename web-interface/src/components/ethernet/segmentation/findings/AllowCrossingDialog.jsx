import React, {useEffect, useState} from "react";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import {apiErrorMessage} from "../../../../util/Tools";
import {FOLDED_PORT, flowServiceLabel} from "../SegmentationFormat";
import PolicyRuleText from "../policies/PolicyRuleText";

const segmentationService = new SegmentationService();

const segmentSelector = (name, options) => {
  if (!name) {
    return null;
  }

  const segment = options.segments.find((s) => s.name === name);
  return segment ? {type: "SEGMENT", value: segment.uuid} : null;
}

// How broadly the source of a finding can be allowed, narrowest first.
const sourceChoices = (finding, options) => {
  const choices = [];

  if (finding.source_address) {
    choices.push({
      title: "This address",
      detail: finding.source_address.address,
      machineData: true,
      selectors: [{type: "ADDRESS", value: finding.source_address.address}]
    });
  }

  const segment = segmentSelector(finding.source_segment_name, options);
  if (segment) {
    choices.push({
      title: finding.source_internal ? "Its segment" : "Its external network",
      detail: finding.source_segment_name,
      selectors: [segment]
    });
  }

  if (!finding.source_address) {
    if (finding.source_asn) {
      choices.push({
        title: "Its autonomous system",
        detail: "AS" + finding.source_asn,
        selectors: [{type: "ASN", value: String(finding.source_asn)}]
      });
    }

    if (finding.source_country_code) {
      choices.push({
        title: "Its country",
        detail: finding.source_country_code,
        selectors: [{type: "COUNTRY", value: finding.source_country_code}]
      });
    }

    if (!finding.source_internal) {
      choices.push({title: "Internet", detail: "every external address", selectors: [{type: "EXTERNAL", value: null}]});
    }
  }

  choices.push({title: "Any source", detail: null, selectors: []});

  return choices;
}

const destinationChoices = (target, options) => {
  const choices = [{
    title: "This address",
    detail: target.address_text,
    machineData: true,
    selectors: [{type: "ADDRESS", value: target.address_text}]
  }];

  if (target.outcome === "SENT") {
    choices.push({
      title: "Broadcast or multicast",
      detail: "every broadcast and multicast address",
      selectors: [{type: "BROADCAST", value: null}]
    });

    return choices;
  }

  const segment = segmentSelector(target.segment_name, options);
  if (segment) {
    choices.push({
      title: target.internal ? "Its segment" : "Its external network",
      detail: target.segment_name,
      selectors: [segment]
    });
  }

  return choices;
}

const serviceChoices = (target) => {
  return [
    {
      title: "This port and protocol",
      detail: flowServiceLabel({l4_type: target.l4_type, port: target.port, protocol: null}),
      services: [target.port === FOLDED_PORT ? {l4_type: target.l4_type}
        : {l4_type: target.l4_type, port_from: target.port, port_to: null, protocol: null}]
    },
    {title: "Any service", detail: null, services: []}
  ];
}

export default function AllowCrossingDialog({organizationId, tenantId, finding, target, onClose, onComplete}) {

  const [options, setOptions] = useState(null);

  const [source, setSource] = useState(0);
  const [destination, setDestination] = useState(0);
  const [service, setService] = useState(0);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    segmentationService.findSelectorOptions(organizationId, tenantId, setOptions);
  }, [organizationId, tenantId]);

  const frame = (body, footer) => {
    return (
      <React.Fragment>
        <div className="modal-backdrop fade show"></div>
        <div className="modal fade show" style={{display: "block"}}>
          <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
            <div className="modal-content">
              <div className="modal-header">
                <h1 className="modal-title fs-5">Allow Crossing</h1>
                <button type="button" className="btn-close" onClick={onClose}></button>
              </div>
              <div className="modal-body">
                {body}
              </div>
              <div className="modal-footer">
                {footer}
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
    )
  }

  if (!options) {
    return frame(<LoadingSpinner />, <button type="button" className="btn btn-secondary" onClick={onClose}>Close</button>);
  }

  const sources = sourceChoices(finding, options);
  const destinations = destinationChoices(target, options);
  const services = serviceChoices(target);

  const rule = {
    sources: sources[source].selectors,
    destinations: destinations[destination].selectors,
    services: services[service].services
  };

  const onSubmit = (e) => {
    e.preventDefault();

    setIsSubmitting(true);
    setErrorMessage(null);

    segmentationService.allowCrossing(finding.policy_uuid, organizationId, tenantId, rule, () => {
      setIsSubmitting(false);
      onComplete();
    }, (error) => {
      setIsSubmitting(false);
      setErrorMessage(apiErrorMessage(error, "Could not add the allowed crossing."));
    });
  }

  const choice = (name, title, choices, selected, setSelected) => {
    return (
      <div className="mb-3">
        <label className="form-label">{title}</label>
        {choices.map((c, i) => {
          return (
            <div className="form-check" key={i}>
              <input className="form-check-input" type="radio" name={"allow-" + name} id={"allow-" + name + "-" + i}
                     checked={selected === i} onChange={() => setSelected(i)} />
              <label className="form-check-label" htmlFor={"allow-" + name + "-" + i}>
                {c.title}
                {c.detail ?
                  <span className="text-muted"> (<span className={c.machineData ? "machine-data" : undefined}>{c.detail}</span>)</span>
                  : null}
              </label>
            </div>
          )
        })}
      </div>
    )
  }

  return frame(
    <React.Fragment>
      <p>
        Adds an allowed crossing to policy <em>{finding.policy_name}</em>. Its findings are re-evaluated.
      </p>

      {choice("source", "Source", sources, source, setSource)}
      {choice("destination", "Destination", destinations, destination, setDestination)}
      {choice("service", "Service", services, service, setService)}

      <div className="alert alert-info mb-0">
        <strong>Will be allowed:</strong> <PolicyRuleText rule={rule} options={options} plain={true} />
      </div>

      {errorMessage ? <div className="alert alert-danger mt-3 mb-0">{errorMessage}</div> : null}
    </React.Fragment>,
    <React.Fragment>
      <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSubmitting}>Close</button>
      <button type="button" className="btn btn-primary" onClick={onSubmit} disabled={isSubmitting}>
        {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> &nbsp;Allowing ...</span> : "Allow"}
      </button>
    </React.Fragment>
  );

}
