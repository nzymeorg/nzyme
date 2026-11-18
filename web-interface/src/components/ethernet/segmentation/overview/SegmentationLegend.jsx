import React from "react";
import Store from "../../../../util/Store";
import {OUTCOMES} from "../SegmentationFormat";
import {
  OUTCOME_FIELDS,
  outcomeColor,
  outcomeTitle,
  overviewColors,
  STATUS_ORDER,
  statusColor,
  STATUSES
} from "./SegmentationOverviewTheme";
import {HatchPattern, StatusMark} from "./SegmentationMarks";

const HATCH = "segmentation-legend-hatch";
const VOLUMES = [0, 0.35, 0.7, 1];

function Item({title = null, children}) {
  return (
      <span className="d-inline-flex align-items-center me-3 mb-1" title={title}>
        {children}
      </span>
  )
}

export default function SegmentationLegend({view, ref = null}) {

  const colors = overviewColors(Boolean(Store.get("dark_mode")));

  const matrixStatus = (status) => (
      <svg width="16" height="14" className="me-1">
        <StatusMark x={1} y={1} width={14} height={12} status={status} volume={0.8} colors={colors} hatchId={HATCH} />
      </svg>
  );

  const mapStatus = (status) => {
    const opacity = status === "COVERED" ? 0.45 : (status === "PARTIAL" ? 0.7 : 1);
    return (
        <svg width="30" height="14" className="me-1">
          {status === "PARTIAL"
              ? <line x1="1" y1="7" x2="24" y2="7" stroke={colors.unjudged} strokeWidth="2.5" opacity={opacity} />
              : null}
          <line x1="1" y1="7" x2="24" y2="7" stroke={statusColor(colors, status)} strokeWidth="2.5"
                strokeDasharray={status === "ATTEMPTED" ? "4 4" : (status === "PARTIAL" ? "6 6" : null)}
                opacity={opacity} />
          <path d="M29,7L23,3.5L23,10.5Z" fill={statusColor(colors, status)} opacity={opacity} />
        </svg>
    );
  }

  return (
      <div ref={ref} className="d-flex flex-wrap align-items-center small text-muted mt-2">
        {/* Zero-sized: only holds what the marks of the legend refer to. */}
        <svg width="0" height="0" style={{position: "absolute"}} aria-hidden="true">
          <defs>
            <HatchPattern id={HATCH} colors={colors} />
          </defs>
        </svg>

        {STATUS_ORDER.map((status) => (
            <Item key={status} title={STATUSES[status].description}>
              {view === "map" ? mapStatus(status) : matrixStatus(status)}
              {STATUSES[status].title}
            </Item>
        ))}

        <span className="border-start align-self-stretch me-3 mb-1" />

        {view === "map" ?
            <React.Fragment>
              <Item title="The width of a link follows the number of sessions on a logarithmic scale.">
                <svg width="30" height="14" className="me-1">
                  <line x1="1" y1="3" x2="29" y2="3" stroke={colors.muted} strokeWidth="1" />
                  <line x1="1" y1="10" x2="29" y2="10" stroke={colors.muted} strokeWidth="4" />
                </svg>
                Fewer to more sessions
              </Item>
              <Item title="Traffic between addresses of the same segment.">
                <svg width="14" height="14" className="me-1">
                  <StatusMark x={1} y={1} width={12} height={12} nested={true} status="COVERED" volume={0.8} colors={colors} hatchId={HATCH} />
                </svg>
                Inside the segment
              </Item>
              <Item title="A tap that recorded traffic of the segment.">
                <svg width="12" height="14" className="me-1">
                  <rect x="3" y="4" width="6" height="6" fill={colors.root} transform="rotate(45 6 7)" />
                </svg>
                Tap
              </Item>
              <Item title="Color of the strongest finding in the segment's crossings.">
                <svg width="16" height="14" className="me-1">
                  <rect x="0.5" y="1.5" width="15" height="11" fill={colors.box} stroke={colors.boxBorder} />
                  <path d="M0.5,1.5h7L0.5,8.5Z" fill={colors.crossed} />
                </svg>
                Segment with findings
              </Item>
            </React.Fragment>
            :
            <React.Fragment>
              <Item title="The strength of the color follows the number of sessions on a logarithmic scale.">
                <svg width={VOLUMES.length * 13 + 1} height="14" className="me-1">
                  {VOLUMES.map((volume, i) => (
                      <StatusMark key={volume} x={1 + i * 13} y={1} width={11} height={12} status="COVERED"
                                  volume={volume} colors={colors} hatchId={HATCH} />
                  ))}
                </svg>
                Fewer to more sessions
              </Item>
              <Item title="Traffic between addresses of the same segment.">
                <svg width="18" height="14" className="me-1">
                  <StatusMark x={1} y={1} width={14} height={12} nested={true} status="COVERED" volume={0.8} colors={colors} hatchId={HATCH} />
                </svg>
                Inside the segment
              </Item>

              <span className="border-start align-self-stretch me-3 mb-1" />

              <span className="me-2 mb-1">Bar below a cell:</span>
              {OUTCOME_FIELDS.map(([outcome]) => (
                  <Item key={outcome} title={OUTCOMES[outcome].description}>
                    <svg width="14" height="14" className="me-1">
                      <rect x="1" y="5" width="12" height="4" fill={outcomeColor(colors, outcome)} />
                    </svg>
                    {outcomeTitle(outcome)}
                  </Item>
              ))}
            </React.Fragment>}
      </div>
  )

}
