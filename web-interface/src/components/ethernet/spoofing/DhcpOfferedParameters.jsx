import React from "react";
import numeral from "numeral";

const list = (values) => {
  if (!values || values.length === 0) {
    return <span className="text-muted">None</span>
  }

  return values.map((v, i) => <span key={i}>{i > 0 ? ", " : ""}<span className="machine-data">{v}</span></span>);
}

const sameList = (a, b) => JSON.stringify([...(a || [])].sort()) === JSON.stringify([...(b || [])].sort());

// A server only includes the options a client asked for, so an option absent from a response is not a change.
const differsFrom = (field, offered, expected) => {
  if (field === "routers" || field === "dns_servers") {
    return offered && offered.length > 0 && !sameList(offered, expected);
  }

  return !!offered && offered !== (expected || null);
}

const text = (value) => value ? <span className="machine-data">{value}</span> : <span className="text-muted">None</span>;

export function DhcpOfferedValue({parameters, field, compareTo = undefined}) {
  const isList = field === "routers" || field === "dns_servers";

  const lines = (values) => {
    if (!values || values.length === 0) {
      return <span className="text-muted">None</span>
    }

    return values.map((v, i) => <div key={i} className="text-nowrap"><span className="machine-data">{v}</span></div>);
  }

  const render = (v) => isList ? lines(v) : text(v);

  if (!parameters) {
    return <span className="text-muted">Unknown</span>
  }

  const latest = parameters[field];
  const expected = compareTo ? compareTo[field] : null;
  const differs = compareTo && differsFrom(field, latest, expected);

  if (!differs) {
    return render(latest);
  }

  return (
    <React.Fragment>
      <div className="text-danger fw-bold">{render(latest)}</div>
      <div className="text-muted" title="The configuration this server is expected to hand out">
        <div>Expected:</div>
        {render(expected)}
      </div>
    </React.Fragment>
  )
}

export default function DhcpOfferedParameters({parameters, compareTo = undefined}) {

  if (!parameters) {
    return <span className="text-muted">Unknown</span>
  }

  const differs = (field) => {
    if (!compareTo) {
      return false;
    }

    return differsFrom(field, parameters[field], compareTo[field]);
  }

  const mark = (field, content) => {
    return differs(field) ? <span className="text-danger fw-bold">{content}</span> : content;
  }

  return (
    <dl className="mb-0">
      <dt>Gateway</dt>
      <dd>{mark("routers", list(parameters.routers))}</dd>
      <dt>DNS Servers</dt>
      <dd>{mark("dns_servers", list(parameters.dns_servers))}</dd>
      <dt>Domain Name</dt>
      <dd>{mark("domain_name", parameters.domain_name ? <span className="machine-data">{parameters.domain_name}</span> : <span className="text-muted">None</span>)}</dd>
      <dt>Subnet Mask</dt>
      <dd>{mark("subnet_mask", parameters.subnet_mask ? <span className="machine-data">{parameters.subnet_mask}</span> : <span className="text-muted">None</span>)}</dd>
      <dt>Lease Time</dt>
      <dd className="mb-0">
        {parameters.lease_time_seconds ? numeral(parameters.lease_time_seconds).format("0,0") + " seconds"
          : <span className="text-muted">None</span>}
      </dd>
    </dl>
  )

}
