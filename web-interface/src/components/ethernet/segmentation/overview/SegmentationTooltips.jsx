import React from "react";
import {formatCount} from "../../assets/details/AssetProfileHelpers";
import {flowServiceLabel} from "../SegmentationFormat";
import {crossingTitle, GROUP_TYPE_TITLES, locationsOf} from "./SegmentationOverviewData";
import {
  COVERAGE_TEXT,
  outcomeColor,
  outcomeFieldsOf,
  outcomeTitle,
  sessionsOf,
  statusColor,
  STATUSES
} from "./SegmentationOverviewTheme";

const TOOLTIP_SERVICES = 3;

const sessionsText = (count) => formatCount(count) + (count === 1 ? " session" : " sessions");

export function CrossingTooltip({cell, colors}) {
  const services = cell.services.slice(0, TOOLTIP_SERVICES);
  const more = cell.service_count - services.length;
  const outcomes = outcomeFieldsOf(cell);

  return (
      <React.Fragment>
        <div style={{fontWeight: "bold"}}>{crossingTitle(cell)}</div>
        <div style={{color: colors.muted}}>
          <span style={{display: "inline-block", width: "8px", height: "8px", marginRight: "5px",
            background: statusColor(colors, cell.status)}} />
          {STATUSES[cell.status].title}. {COVERAGE_TEXT[cell.coverage]}.
        </div>

        <div style={{marginTop: "4px"}}>{sessionsText(cell.total)}</div>
        <div style={{display: "flex", height: "4px", margin: "2px 0 3px", overflow: "hidden"}}>
          {outcomes.map(([outcome, field]) => (cell[field] > 0
              ? <div key={outcome} style={{flexGrow: cell[field], flexBasis: "2px", background: outcomeColor(colors, outcome)}} />
              : null))}
        </div>
        {outcomes.map(([outcome, field]) => (
            <div key={outcome} style={{display: "flex", justifyContent: "space-between"}}>
              <span>
                <span style={{display: "inline-block", width: "8px", height: "8px",
                  marginRight: "5px", background: outcomeColor(colors, outcome)}} />
                {outcomeTitle(outcome)}
              </span>
              <span>{formatCount(cell[field])}</span>
            </div>
        ))}

        {cell.crossed_findings > 0 || cell.attempted_findings > 0 ?
            <div style={{marginTop: "4px"}}>
              Findings: {formatCount(cell.crossed_findings)} crossed, {formatCount(cell.attempted_findings)} attempted
            </div> : null}

        {services.length > 0 ?
            <div style={{marginTop: "4px", color: colors.muted}}>
              {services.length === 1 && more <= 0 ? "Service" : "Top services"}
            </div> : null}
        {services.map((service) => (
            <div key={service.l4_type + "/" + service.port + "/" + service.protocol}
                 style={{display: "flex", justifyContent: "space-between"}}>
              <span className="machine-data" style={{marginRight: "12px"}}>{flowServiceLabel(service)}</span>
              <span>{formatCount(sessionsOf(service))}</span>
            </div>
        ))}
        {more > 0 ? <div style={{color: colors.muted}}>and {formatCount(more)} more</div> : null}
      </React.Fragment>
  )
}

export function GroupTooltip({group, colors}) {
  const locations = locationsOf(group.taps);

  return (
      <React.Fragment>
        <div style={{fontWeight: "bold"}}>{group.name}</div>
        <div style={{color: colors.muted}}>
          {group.description ? group.description : GROUP_TYPE_TITLES[group.type]}
        </div>
        <div style={{marginTop: "4px"}}>
          {formatCount(group.outgoing.length) + (group.outgoing.length === 1 ? " crossing" : " crossings") + " out, "
              + formatCount(group.incoming.length) + " in"}
        </div>
        {group.inside ? <div>{sessionsText(group.inside.total)} inside</div> : null}
        {(group.networks || []).length > 0
            ? <div className="machine-data" style={{marginTop: "4px"}}>
                {group.networks.slice(0, 3).join(", ")}{group.networks.length > 3 ? ", …" : ""}
              </div> : null}
        {group.taps.length > 0
            ? <div style={{marginTop: "4px"}}>
                <span style={{color: colors.muted}}>Recorded by</span> {group.taps.map((tap) => tap.name).join(", ")}
                {locations.length > 0 ? <span style={{color: colors.muted}}> ({locations.join(", ")})</span> : null}
              </div>
            : <div style={{marginTop: "4px", color: colors.muted}}>No tap recorded traffic of it in the time range.</div>}
      </React.Fragment>
  )
}
