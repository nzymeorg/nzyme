import React, {useEffect, useMemo, useRef, useState} from "react";
import {select as d3Select, zoom, zoomIdentity} from "d3";
import Store from "../../../../util/Store";
import {truncate} from "../../../../util/Tools";
import ZoomHint, {useZoomHint, wheelZoomDelta, wheelZooms} from "../../../shared/ZoomHint";
import {FONT_FAMILY, GRID_SPACING, tooltipStyle} from "../../../shared/viz/VizTheme";
import {cellId, strongerStatus} from "./SegmentationOverviewData";
import {isFinding, overviewColors, STATUS_ORDER, statusColor, volumeOf} from "./SegmentationOverviewTheme";
import {arrowPath, placeBoxes, routeLinks} from "./SegmentationMapLayout";
import {GroupGlyph, HatchPattern, StatusMark} from "./SegmentationMarks";
import {CrossingTooltip, GroupTooltip} from "./SegmentationTooltips";

const HATCH = "segmentation-map-hatch";
const MIN_LINK_WIDTH = 1;
const MAX_LINK_WIDTH = 3.6;
const CROWDED_LINKS = 60;
const MIN_CROWDING = 0.45;
// How much of their visibility links without findings keep on the most crowded map.
const CROWDED_CALM = 0.6;
const DIMMED_OPACITY = 0.07;
const DIMMED_BOX_OPACITY = 0.4;
const TAP_MARK_SPACING = 9;
const INSIDE_MARK = 11;
const FLAG = 12;
const BRACKET = 9;
const MIN_ZOOM = 0.4;
const MAX_ZOOM = 5;

const REST_OPACITY = {CROSSED: 1, ATTEMPTED: 0.85, NONE: 0.55, PARTIAL: 0.5, COVERED: 0.24};

const reducedMotion = () => typeof window.matchMedia === "function"
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const bracketPath = (lane) => {
  const left = lane.x;
  const top = lane.y;
  const right = lane.x + lane.width;
  const bottom = lane.y + lane.height;

  return "M" + left + "," + (top + BRACKET) + "V" + top + "H" + (left + BRACKET)
      + "M" + (right - BRACKET) + "," + top + "H" + right + "V" + (top + BRACKET)
      + "M" + right + "," + (bottom - BRACKET) + "V" + bottom + "H" + (right - BRACKET)
      + "M" + (left + BRACKET) + "," + bottom + "H" + left + "V" + (bottom - BRACKET);
}

const sameTarget = (a, b) => {
  if (!a || !b || a.kind !== b.kind) return false;
  return a.kind === "cell" ? a.source === b.source && a.destination === b.destination : a.id === b.id;
}

function MapBox({box, colors, inside, finding, most, selected, dimmed, onHover, onSelect}) {
  const group = box.group;
  const {x, y, width, height} = box;
  const external = group.lane.external;

  const tapRoom = width - 42 - group.name.length * 6.4;
  const taps = group.taps.length * TAP_MARK_SPACING <= tapRoom ? group.taps : group.taps.slice(0, 1);
  const tapCount = taps.length < group.taps.length ? "×" + group.taps.length : null;
  const countWidth = tapCount ? 4 + tapCount.length * 5 : 0;
  const tapsWidth = taps.length * TAP_MARK_SPACING + countWidth;
  const nameRoom = Math.floor((width - 24 - tapsWidth - 6) / 6.4);
  const detailRoom = Math.floor((width - 14 - (inside ? INSIDE_MARK + 10 : 4)) / 5.2);

  const crossings = group.outgoing.length + " out, " + group.incoming.length + " in";
  const detail = group.description ? group.description : (group.taps.length === 0 && !external ? "Not recorded" : crossings);

  return (
      <g opacity={dimmed ? DIMMED_BOX_OPACITY : 1} style={{cursor: "pointer"}}
         onMouseOver={() => onHover({kind: "group", id: group.id})}
         onClick={() => onSelect({kind: "group", id: group.id})}>
        <rect x={x} y={y} width={width} height={height} fill={colors.box}
              stroke={selected ? colors.selection : (external ? colors.external : colors.boxBorder)}
              strokeOpacity={selected || !external ? 1 : 0.7}
              strokeWidth={selected ? 2 : 1}
              strokeDasharray={group.type === "UNASSIGNED" || group.type === "INTERNET" || group.type === "BROADCAST"
                  ? "4 3" : null} />

        {finding ?
            <path d={"M" + x + "," + y + "h" + FLAG + "L" + x + "," + (y + FLAG) + "Z"}
                  fill={statusColor(colors, finding)} /> : null}

        <GroupGlyph group={group} x={x + 15} y={y + 15} size={7} colors={colors} />
        <text x={x + 24} y={y + 15} dominantBaseline="central" fontSize="12" fontWeight="bold" fill={colors.text}>
          {truncate(group.name, nameRoom)}
        </text>

        {taps.map((tap, i) => {
          const tx = x + width - 12 - (taps.length - 1 - i) * TAP_MARK_SPACING - countWidth;
          return (
              <rect key={tap.uuid} x={tx - 3} y={y + 12} width="6" height="6" fill={colors.root}
                    transform={"rotate(45 " + tx + " " + (y + 15) + ")"}>
                <title>
                  {"Recorded by " + (tapCount ? group.taps : [tap]).map((t) => t.name
                      + (t.location_name ? " (" + t.location_name + ")" : "")).join(", ")}
                </title>
              </rect>
          );
        })}
        {tapCount ?
            <text x={x + width - 7} y={y + 15} textAnchor="end" dominantBaseline="central" fontSize="10" fill={colors.muted}>
              <title>{"Recorded by " + group.taps.map((tap) => tap.name).join(", ")}</title>
              {tapCount}
            </text> : null}

        <text x={x + 12} y={y + 31} dominantBaseline="central" fontSize="10" fill={colors.muted}>
          {truncate(detail, detailRoom)}
        </text>

        {inside ?
            <g onMouseOver={(e) => { e.stopPropagation(); onHover({kind: "cell", source: group.id, destination: group.id}); }}
               onClick={(e) => { e.stopPropagation(); onSelect({kind: "cell", source: group.id, destination: group.id}); }}>
              <StatusMark x={x + width - INSIDE_MARK - 7} y={y + height - INSIDE_MARK - 5}
                          width={INSIDE_MARK} height={INSIDE_MARK} nested={true}
                          status={inside.status} volume={Math.max(0.5, volumeOf(inside.total, most))}
                          colors={colors} hatchId={HATCH} />
              <circle cx={x + width - INSIDE_MARK / 2 - 7} cy={y + height - INSIDE_MARK / 2 - 5} r={INSIDE_MARK / 2 + 3}
                      fill="transparent" />
            </g> : null}
      </g>
  )
}

export default function SegmentationBoundaryMap({model, groups, cells, selection = null, onSelect, filtered = false,
                                                  height = 580, resetKey = 0}) {

  const containerRef = useRef(null);
  const svgRef = useRef(null);
  const zoomRef = useRef(null);
  const [width, setWidth] = useState(0);
  const [transform, setTransform] = useState(zoomIdentity);
  const [zoomHint, showZoomHint] = useZoomHint();
  const [hoveredTarget, setHovered] = useState(null);
  const [pointer, setPointer] = useState(null);

  const darkMode = Boolean(Store.get("dark_mode"));
  const colors = overviewColors(darkMode);

  const empty = groups.length === 0;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;

    setWidth(container.clientWidth);

    // Applied in the next frame: a resize inside the observer callback is reported as a ResizeObserver loop.
    let frame = null;
    const observer = new window.ResizeObserver(() => {
      if (frame !== null) return;
      frame = window.requestAnimationFrame(() => {
        frame = null;
        setWidth(container.clientWidth);
      });
    });
    observer.observe(container);

    return () => {
      observer.disconnect();
      if (frame !== null) window.cancelAnimationFrame(frame);
    };
    // The empty state is another element, so the observer moves with it.
  }, [empty]);

  const layout = useMemo(() => (width > 0 && !empty ? placeBoxes(groups, width, height) : null),
      [groups, width, height, empty]);

  const links = useMemo(() => {
    if (!layout) return [];
    return routeLinks(layout, cells)
        .sort((a, b) => STATUS_ORDER.indexOf(b.cell.status) - STATUS_ORDER.indexOf(a.cell.status) || a.cell.total - b.cell.total);
  }, [layout, cells]);

  // The drawing only exists once there is a layout. The zoom is attached to it when it appears.
  const drawn = layout !== null;
  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return undefined;

    const zoomBehavior = zoom()
        .scaleExtent([MIN_ZOOM, MAX_ZOOM])
        .wheelDelta(wheelZoomDelta)
        .filter((event) => !event.button && (event.type === "wheel" ? wheelZooms(event) : !event.ctrlKey))
        .on("zoom", (event) => setTransform(event.transform));
    zoomRef.current = zoomBehavior;

    // Not to be confused with select() of this component, which selects a box or a link.
    const drawing = d3Select(svg);
    drawing.call(zoomBehavior).on("dblclick.zoom", null);

    return () => {
      drawing.on(".zoom", null);
      zoomRef.current = null;
    };
  }, [drawn]);

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg || !zoomRef.current) return;

    d3Select(svg).call(zoomRef.current.transform, zoomIdentity);
  }, [resetKey]);

  const marks = useMemo(() => {
    const inside = new Map();
    const findings = new Map();
    cells.forEach((cell) => {
      if (cell.inside) inside.set(cell.source.id, cell);
      if (isFinding(cell.status)) {
        findings.set(cell.source.id, strongerStatus(findings.get(cell.source.id), cell.status));
        findings.set(cell.destination.id, strongerStatus(findings.get(cell.destination.id), cell.status));
      }
    });
    return {inside: inside, findings: findings, shown: new Set(cells.map((cell) => cell.id))};
  }, [cells]);

  // What the pointer was on may be gone once a filter changed. It only counts while it is still drawn.
  const hovered = useMemo(() => {
    if (!hoveredTarget || !layout) return null;
    if (hoveredTarget.kind === "group") return layout.boxes.has(hoveredTarget.id) ? hoveredTarget : null;
    return marks.shown.has(cellId(hoveredTarget.source, hoveredTarget.destination)) ? hoveredTarget : null;
  }, [hoveredTarget, layout, marks]);

  const spotlight = hovered || selection;
  const lit = useMemo(() => {
    if (!spotlight) return null;

    const litLinks = new Set();
    const litGroups = new Set();
    if (spotlight.kind === "cell") {
      litLinks.add(cellId(spotlight.source, spotlight.destination));
      litGroups.add(spotlight.source);
      litGroups.add(spotlight.destination);
    } else {
      litGroups.add(spotlight.id);
      links.forEach((link) => {
        if (link.a.group.id === spotlight.id || link.b.group.id === spotlight.id) {
          litLinks.add(link.id);
          litGroups.add(link.a.group.id);
          litGroups.add(link.b.group.id);
        }
      });
    }

    return {links: litLinks, groups: litGroups};
  }, [spotlight, links]);

  if (empty) {
    return (
        <div ref={containerRef} className="border d-flex align-items-center justify-content-center text-muted"
             style={{height: height + "px"}}>
          No segments to show.
        </div>
    )
  }

  const onPointerMove = (event) => {
    const container = containerRef.current;
    const rect = container.getBoundingClientRect();
    setPointer({
      x: event.clientX - rect.left + container.scrollLeft,
      y: event.clientY - rect.top,
      right: container.scrollLeft + container.clientWidth
    });
  }

  const crowding = Math.max(MIN_CROWDING, Math.min(1, Math.sqrt(CROWDED_LINKS / Math.max(1, links.length))));
  const widthOf = (cell) => MIN_LINK_WIDTH + (MAX_LINK_WIDTH - MIN_LINK_WIDTH) * volumeOf(cell.total, model.most) * crowding
      * (cell.status === "ATTEMPTED" ? 0.6 : 1);
  const calm = CROWDED_CALM + (1 - CROWDED_CALM) * (crowding - MIN_CROWDING) / (1 - MIN_CROWDING);
  const restOpacity = (status) => REST_OPACITY[status] * (isFinding(status) ? 1 : calm);
  const isLit = (link) => lit !== null && lit.links.has(link.id);

  const selectedLinkId = selection && selection.kind === "cell" ? cellId(selection.source, selection.destination) : null;
  const hoveredLinkId = hovered && hovered.kind === "cell" ? cellId(hovered.source, hovered.destination) : null;
  const animated = !reducedMotion();

  const select = (target) => {
    if (onSelect) onSelect(target);
  }

  // The pointer reports the same target many times while it moves over it.
  const hover = (target) => setHovered((current) => (sameTarget(current, target) ? current : target));

  const drawLink = (link) => {
    const status = link.cell.status;
    const color = statusColor(colors, status);
    const emphasized = link.id === selectedLinkId || link.id === hoveredLinkId;
    const w = widthOf(link.cell) + (emphasized ? 1.2 : 0);
    const opacity = lit === null ? restOpacity(status) : (isLit(link) ? 1 : DIMMED_OPACITY);

    return (
        <g key={link.id} opacity={opacity} pointerEvents="none"
           style={emphasized && darkMode ? {filter: "drop-shadow(0 0 4px " + color + ")"} : null}>
          {link.id === selectedLinkId
              ? <path d={link.path} fill="none" stroke={colors.selection} strokeWidth={w + 4} strokeOpacity="0.28" />
              : null}
          {status === "PARTIAL"
              ? <path d={link.path} fill="none" stroke={colors.unjudged} strokeWidth={w} />
              : null}
          <path d={link.path} fill="none" stroke={color} strokeWidth={w}
                strokeDasharray={status === "ATTEMPTED" ? "4 4" : (status === "PARTIAL" ? "7 7" : null)} />
          <path d={arrowPath(link.arrow, w)} fill={color} />
          {emphasized ?
              <path d={link.path} fill="none" stroke={colors.selection} strokeWidth="1.4" strokeLinecap="square"
                    strokeDasharray="0.1 11" strokeOpacity="0.9">
                {animated
                    ? <animate attributeName="stroke-dashoffset" from="11.1" to="0" dur="0.5s" repeatCount="indefinite" />
                    : null}
              </path> : null}
        </g>
    );
  }

  const hoveredCell = hovered && hovered.kind === "cell"
      ? model.cellsById.get(cellId(hovered.source, hovered.destination)) : null;
  const hoveredGroup = hovered && hovered.kind === "group" ? model.groupsById.get(hovered.id) : null;
  const mapHeight = layout ? Math.max(height, layout.height) : height;

  return (
      <div ref={containerRef} className="border"
           style={{position: "relative", width: "100%", height: mapHeight + "px", overflowX: "auto", overflowY: "hidden"}}
           onMouseMove={onPointerMove}
           onMouseLeave={() => { setHovered(null); setPointer(null); }}>
        {layout ?
            // Out of the flow, so the drawing follows the width of the card and never holds it open.
            <svg ref={svgRef} width={layout.width} height={mapHeight}
                 style={{position: "absolute", top: 0, left: 0, display: "block", fontFamily: FONT_FAMILY, cursor: "grab"}}
                 onWheel={(e) => { if (!wheelZooms(e)) showZoomHint(); }}>
              <defs>
                <pattern id="segmentation-map-grid" width={GRID_SPACING} height={GRID_SPACING} patternUnits="userSpaceOnUse"
                         patternTransform={transform.toString()}>
                  <rect x={GRID_SPACING / 2} y={GRID_SPACING / 2} width="1.5" height="1.5" fill={colors.grid} />
                </pattern>
                <HatchPattern id={HATCH} colors={colors} />
              </defs>
              <rect width={layout.width} height={mapHeight} fill={colors.background} onMouseEnter={() => setHovered(null)} />
              <rect width={layout.width} height={mapHeight} fill="url(#segmentation-map-grid)" pointerEvents="none" />

              <g transform={transform.toString()}>
                <g transform={"translate(0 " + Math.round((mapHeight - layout.height) / 2) + ")"}>
                  {layout.lanes.map((lane) => (
                      <g key={lane.key} pointerEvents="none">
                        <rect x={lane.x} y={lane.y} width={lane.width} height={lane.height}
                              fill={colors.text} fillOpacity="0.03" />
                        <path d={bracketPath(lane)} fill="none" stroke={lane.external ? colors.external : colors.muted}
                              strokeWidth="1.5" strokeOpacity={lane.external ? 0.9 : 0.7} />
                      </g>
                  ))}

                  {links.filter((link) => !isLit(link)).map(drawLink)}
                  {links.filter(isLit).map(drawLink)}

                  {/* Wide and invisible, so thin links are easy to point at. */}
                  {links.map((link) => {
                    const target = {kind: "cell", source: link.cell.source.id, destination: link.cell.destination.id};
                    return (
                        <path key={link.id} d={link.path} fill="none" stroke="transparent" strokeWidth="9"
                              pointerEvents="stroke" style={{cursor: "pointer"}}
                              onMouseEnter={() => hover(target)}
                              onClick={() => select(target)} />
                    );
                  })}

                  {layout.lanes.map((lane) => (
                      <g key={lane.key} pointerEvents="none">
                        <rect x={lane.x + BRACKET + 4} y={lane.y - 7} width={lane.title.length * 6.1 + 10} height="14"
                              fill={colors.background} />
                        <text x={lane.x + BRACKET + 9} y={lane.y} dominantBaseline="central" fontSize="11" fontWeight="600"
                              fill={lane.external ? colors.external : colors.muted}>
                          {lane.title}
                        </text>
                      </g>
                  ))}

                  {Array.from(layout.boxes.values()).map((box) => (
                      <MapBox key={box.group.id}
                              box={box}
                              colors={colors}
                              inside={marks.inside.get(box.group.id)}
                              finding={marks.findings.get(box.group.id)}
                              most={model.most}
                              selected={selection !== null && selection.kind === "group" && selection.id === box.group.id}
                              dimmed={lit !== null && !lit.groups.has(box.group.id)}
                              onHover={hover}
                              onSelect={select} />
                  ))}
                </g>
              </g>
            </svg> : null}

        {layout ? <ZoomHint visible={zoomHint} dragText="pan" /> : null}

        {(hoveredCell || hoveredGroup) && pointer ? (
            <div style={{
              ...tooltipStyle(colors),
              borderRadius: 0,
              maxWidth: "none",
              width: "250px",
              left: Math.max(0, Math.min(pointer.x + 14, pointer.right - 262)) + "px",
              top: (pointer.y > 210 ? pointer.y - 12 : pointer.y + 24) + "px",
              transform: pointer.y > 210 ? "translateY(-100%)" : "none"
            }}>
              {hoveredCell
                  ? <CrossingTooltip cell={hoveredCell} colors={colors} />
                  : <GroupTooltip group={hoveredGroup} colors={colors} />}
            </div>
        ) : null}

        {filtered && links.length === 0 && marks.inside.size === 0 ?
            <div className="text-muted small"
                 style={{position: "absolute", left: "12px", bottom: "8px", pointerEvents: "none"}}>
              No crossings match the selected filters.
            </div> : null}
      </div>
  )

}
