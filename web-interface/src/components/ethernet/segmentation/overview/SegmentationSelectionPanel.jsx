import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import PivotLink from "../../../shared/PivotLink";
import VizMenu from "../../../shared/viz/VizMenu";
import {formatCount} from "../../assets/details/AssetProfileHelpers";
import {formatWindowTime} from "../../topology/TopologyData";
import {createPolicyLink, flowServiceLabel, groupSelector, policyFindingsLink} from "../SegmentationFormat";
import {broadcastAddressesOf, crossingSessionsLink, groupSessionsLink} from "../SegmentationPivots";
import {cellId, GROUP_TYPE_TITLES, isBroadcast, locationsOf} from "./SegmentationOverviewData";
import {
  COVERAGE_TEXT,
  LONG_LIVED_DESCRIPTION,
  outcomeColor,
  outcomeFieldsOf,
  outcomeTitle,
  statusColor,
  STATUSES
} from "./SegmentationOverviewTheme";

const LIST_LENGTH = 8;

const EMPTY_TEXT = {
  matrix: "Click a cell or a label in the matrix to see its details here.",
  map: "Click a link or a box on the map to see its details here."
};

const KIND_TITLES = {
  INTERNAL: "Selected Segment",
  UNASSIGNED: "Selected Group",
  EXTERNAL: "Selected External Network",
  INTERNET: "Selected Group",
  BROADCAST: "Selected Group"
};

const seenText = (first, last) => formatWindowTime(first) + " to " + formatWindowTime(last);

const swatch = (color) => (
    <span style={{display: "inline-block", width: "8px", height: "8px", marginRight: "6px", background: color}} />
);

function OutcomeSplit({counts, fields, colors}) {
  return (
      <div style={{display: "flex", height: "5px", overflow: "hidden"}}>
        {fields.map(([outcome, field]) => (counts[field] > 0
            ? <div key={outcome} title={outcomeTitle(outcome) + ": " + formatCount(counts[field])}
                   style={{flexGrow: counts[field], flexBasis: "2px", background: outcomeColor(colors, outcome)}} />
            : null))}
      </div>
  )
}

function CrossingList({title, cells, other, prefix, colors, onSelect}) {
  if (cells.length === 0) return null;

  return (
      <React.Fragment>
        <h6 className="mb-1">{title} ({formatCount(cells.length)})</h6>
        <ul className="list-unstyled mb-2">
          {cells.slice(0, LIST_LENGTH).map((cell) => (
              <li key={cell.id} className="d-flex justify-content-between small"
                  title={STATUSES[cell.status].title + ". " + COVERAGE_TEXT[cell.coverage] + "."}>
                <span className="text-truncate me-2">
                  {swatch(statusColor(colors, cell.status))}
                  <a href="#" onClick={(e) => { e.preventDefault(); onSelect({kind: "cell", source: cell.source.id, destination: cell.destination.id}); }}>
                    {prefix} {other(cell).name}
                  </a>
                </span>
                <span className="text-nowrap">{formatCount(cell.total)}</span>
              </li>
          ))}
        </ul>
        {cells.length > LIST_LENGTH
            ? <div className="text-muted small mb-2">{formatCount(cells.length - LIST_LENGTH)} more</div> : null}
      </React.Fragment>
  )
}

export default function SegmentationSelectionPanel({selection, view, model, policies, timeRange, flows, canManage,
                                                     colors, onSelect, height}) {

  let pivots = [];

  const empty = () => (
      <div className="d-flex align-items-center justify-content-center text-center text-muted h-100">
        {EMPTY_TEXT[view]}
      </div>
  );

  const groupLink = (group) => (
      <a href="#" onClick={(e) => { e.preventDefault(); onSelect({kind: "group", id: group.id}); }}>{group.name}</a>
  );

  const tapNames = (taps) => {
    if (taps.length === 0) return <span className="text-muted">None</span>;
    const locations = locationsOf(taps);
    return (
        <React.Fragment>
          {taps.map((tap) => tap.name).join(", ")}
          {locations.length > 0 ? <span className="text-muted"> ({locations.join(", ")})</span> : null}
        </React.Fragment>
    );
  }

  const policyLinks = (cell) => {
    if (cell.policy_uuids.length === 0) return <span className="text-muted">None</span>;

    return cell.policy_uuids.map((uuid, i) => {
      const policy = policies ? policies.find((p) => p.uuid === uuid) : null;
      return (
          <React.Fragment key={uuid}>
            {i > 0 ? ", " : null}
            <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(uuid)}>
              {policy ? policy.name : (policies ? "Unknown policy" : "Loading")}
            </Link>
          </React.Fragment>
      );
    });
  }

  const selectedCell = selection && selection.kind === "cell"
      ? model.cellsById.get(cellId(selection.source, selection.destination)) : null;

  const action = selectedCell && canManage
      ? {
        label: "Create policy for this crossing",
        to: createPolicyLink(groupSelector(selectedCell.source), groupSelector(selectedCell.destination))
      }
      : null;

  const cellPanel = () => {
    const cell = selectedCell;
    if (!cell) return empty();

    const findings = cell.crossed_findings + cell.attempted_findings;
    const more = cell.service_count - cell.services.length;
    const outcomes = outcomeFieldsOf(cell);

    const firstPolicy = policies && cell.policy_uuids.length > 0
        ? policies.find((p) => p.uuid === cell.policy_uuids[0]) : null;
    const findingsLink = findings > 0 && firstPolicy ? policyFindingsLink(firstPolicy.name) : null;

    // The flows come a page at a time, so only the broadcast addresses of the page that is shown are known.
    const broadcastAddresses = broadcastAddressesOf(flows);
    const sessions = crossingSessionsLink(cell, timeRange, broadcastAddresses);
    const serviceSessions = (service) => crossingSessionsLink(cell, timeRange, broadcastAddresses, service);

    pivots = [
      sessions ? {label: "Sessions of Crossing", to: sessions} : null,
      findingsLink ? {label: "Findings", to: findingsLink} : null
    ].filter((p) => p !== null);

    return (
        <React.Fragment>
          <h6 className="mb-1">
            {cell.inside
                ? <React.Fragment>Inside {groupLink(cell.source)}</React.Fragment>
                : <React.Fragment>{groupLink(cell.source)} to {groupLink(cell.destination)}</React.Fragment>}
          </h6>
          <div className="small mb-2" title={STATUSES[cell.status].description}>
            {swatch(statusColor(colors, cell.status))}{STATUSES[cell.status].title}
          </div>

          <dl className="mb-2">
            <dt>Sessions</dt>
            <dd>
              {formatCount(cell.total)}
              {cell.continued > 0
                  ? <span className="text-muted" title={LONG_LIVED_DESCRIPTION}>
                      {' '}({formatCount(cell.continued)} long-lived)
                    </span>
                  : null}
              <div className="mt-1 mb-1"><OutcomeSplit counts={cell} fields={outcomes} colors={colors} /></div>
              {outcomes.map(([outcome, field]) => (
                  <div key={outcome} className="d-flex justify-content-between small">
                    <span>{swatch(outcomeColor(colors, outcome))}{outcomeTitle(outcome)}</span>
                    <span>{formatCount(cell[field])}</span>
                  </div>
              ))}
            </dd>
            <dt>Seen</dt>
            <dd>{seenText(cell.first_seen, cell.last_seen)}</dd>
            <dt>{cell.taps.length === 1 ? "Recorded by Tap" : "Recorded by Taps"}</dt>
            <dd>{tapNames(cell.taps)}</dd>
            <dt>Coverage</dt>
            <dd>{COVERAGE_TEXT[cell.coverage]}</dd>
            <dt>{cell.policy_uuids.length === 1 ? "Policy in Scope" : "Policies in Scope"}</dt>
            <dd>{policyLinks(cell)}</dd>
            <dt>Findings</dt>
            <dd className="mb-0">
              {findings === 0
                  ? <span className="text-muted">None</span>
                  : <React.Fragment>
                      {formatCount(cell.crossed_findings)} crossed, {formatCount(cell.attempted_findings)} attempted
                      {findingsLink ? <Link to={findingsLink} className="ms-2">Show findings</Link> : null}
                    </React.Fragment>}
            </dd>
          </dl>

          <h6 className="mb-1">{cell.service_count === 1 ? "Service" : "Services (" + formatCount(cell.service_count) + ")"}</h6>
          <table className="table table-sm small mb-1">
            <thead>
            <tr>
              <th></th>
              {outcomes.map(([outcome]) => (
                  <th key={outcome} className="text-end fw-normal text-muted text-nowrap">{outcomeTitle(outcome)}</th>
              ))}
              {sessions ? <th></th> : null}
            </tr>
            </thead>
            <tbody>
            {cell.services.map((service) => (
                <tr key={service.l4_type + "/" + service.port + "/" + service.protocol}>
                  <td className="machine-data">{flowServiceLabel(service)}</td>
                  {outcomes.map(([outcome, field]) => (
                      <td key={outcome} className={"text-end" + (service[field] === 0 ? " text-muted" : "")}>
                        {formatCount(service[field])}
                      </td>
                  ))}
                  {sessions ?
                      <td className="text-end">
                        <PivotLink to={serviceSessions(service)} title="Sessions of this service" icon={true} />
                      </td> : null}
                </tr>
            ))}
            </tbody>
          </table>
          {more > 0 ? <div className="text-muted small mb-2">and {formatCount(more)} more</div> : null}
        </React.Fragment>
    );
  }

  const groupPanel = () => {
    const group = model.groupsById.get(selection.id);
    if (!group) return empty();

    const members = (group.networks || []).concat((group.asns || []).map((asn) => "AS" + asn));
    const sessionsFrom = groupSessionsLink(group, "source", timeRange);
    const sessionsTo = groupSessionsLink(group, "destination", timeRange);

    pivots = [
      group.segment_uuid
          ? {label: "Open Segment", to: ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.DETAILS(group.segment_uuid)} : null,
      sessionsFrom && !isBroadcast(group) ? {label: "Sessions From", to: sessionsFrom} : null,
      // What goes to broadcast addresses is linked from its crossings, where the addresses in use are known.
      sessionsTo && !isBroadcast(group) ? {label: "Sessions To", to: sessionsTo} : null
    ].filter((p) => p !== null);

    return (
        <React.Fragment>
          <h6 className="mb-1">{group.name}</h6>
          <dl className="mb-2">
            <dt>Type</dt>
            <dd>{GROUP_TYPE_TITLES[group.type]}</dd>
            {group.description && group.id > 0
                ? <React.Fragment><dt>Description</dt><dd>{group.description}</dd></React.Fragment> : null}
            {members.length > 0 ?
                <React.Fragment>
                  <dt>{members.length === 1 ? "Network" : "Networks"}</dt>
                  <dd className="machine-data">
                    {members.slice(0, LIST_LENGTH).join(", ")}
                    {members.length > LIST_LENGTH
                        ? <span className="text-muted"> and {formatCount(members.length - LIST_LENGTH)} more</span> : null}
                  </dd>
                </React.Fragment> : null}
            <dt>{group.taps.length === 1 ? "Recorded by Tap" : "Recorded by Taps"}</dt>
            <dd>
              {group.taps.length === 0
                  ? <span className="text-muted">No tap recorded traffic of it in the time range.</span>
                  : tapNames(group.taps)}
            </dd>
            {isBroadcast(group) ? null : <dt>Traffic Inside</dt>}
            {isBroadcast(group) ? null :
            <dd className="mb-0">
              {group.inside
                  ? <React.Fragment>
                      {swatch(statusColor(colors, group.inside.status))}
                      <a href="#" onClick={(e) => { e.preventDefault(); onSelect({kind: "cell", source: group.id, destination: group.id}); }}>
                        {formatCount(group.inside.total)} {group.inside.total === 1 ? "session" : "sessions"}
                      </a>
                    </React.Fragment>
                  : <span className="text-muted">None recorded</span>}
            </dd>}
          </dl>

          <CrossingList title="Crossings Out" cells={group.outgoing} other={(cell) => cell.destination} prefix="to"
                        colors={colors} onSelect={onSelect} />
          <CrossingList title="Crossings In" cells={group.incoming} other={(cell) => cell.source} prefix="from"
                        colors={colors} onSelect={onSelect} />
          {group.outgoing.length === 0 && group.incoming.length === 0
              ? <div className="text-muted small">No crossings in the time range.</div> : null}
        </React.Fragment>
    );
  }

  // Rendered first: the sections name their pivots while they are.
  const content = !selection ? empty() : (selection.kind === "cell" ? cellPanel() : groupPanel());

  const title = () => {
    if (!selection) return "Selection";
    if (selection.kind === "cell") return "Selected Crossing";

    const group = model.groupsById.get(selection.id);
    return group ? KIND_TITLES[group.type] : "Selection";
  }

  return (
      <div className="border d-flex flex-column" style={{height: height + "px"}}>
        <div className="p-3 flex-grow-1 d-flex flex-column" style={{overflowY: "auto", minHeight: 0}}>
          <div className="small text-muted text-uppercase mb-2">{title()}</div>
          <div className="flex-grow-1 d-flex flex-column">
            {content}
          </div>
        </div>
        {action || pivots.length > 0 ?
            <div className="d-flex border-top p-2" style={{gap: "6px"}}>
              {action ? <Link to={action.to} className="btn btn-sm btn-outline-secondary flex-fill">{action.label}</Link> : null}
              {pivots.length > 0 ? <VizMenu label="Pivot" items={pivots} block={!action} up={true} /> : null}
            </div> : null}
      </div>
  )

}
