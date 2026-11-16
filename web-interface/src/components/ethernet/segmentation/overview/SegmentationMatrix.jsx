import React, {useEffect, useMemo, useRef, useState} from "react";
import numeral from "numeral";
import Store from "../../../../util/Store";
import {FONT_FAMILY, tooltipStyle} from "../../../shared/viz/VizTheme";
import {GROUP_TYPE_TITLES} from "./SegmentationOverviewData";
import {gridBackground, overviewColors, volumeOf} from "./SegmentationOverviewTheme";
import {GroupGlyph, HatchPattern, OutcomeBar, StatusMark} from "./SegmentationMarks";
import {CrossingTooltip} from "./SegmentationTooltips";

// Multiples of the spacing of the background grid, so that the grid of the sticky headers continues the one behind.
const LEFT = 120;
const TOP = 96;
const RIGHT = 60;
const BOTTOM = 14;
const SECTION_GAP = 10;
const ORIGIN = 1;
const MIN_CELL = 16;
const MAX_CELL = 64;
const LABELED_CELL = 38;
const HATCH = "segmentation-matrix-hatch";

const countLabel = (count) => (count < 1000 ? String(count) : numeral(count).format("0.[0]a"));

const groupTitle = (group) => group.name + " (" + (group.description ? group.description : GROUP_TYPE_TITLES[group.type]) + ")";

export default function SegmentationMatrix({model, groups, cells, selection = null, onSelect, filtered = false,
                                             height = 580}) {

  const containerRef = useRef(null);
  const [width, setWidth] = useState(0);
  const [hoveredId, setHovered] = useState(null);
  const [pointer, setPointer] = useState(null);

  const darkMode = Boolean(Store.get("dark_mode"));
  const colors = overviewColors(darkMode);

  const empty = groups.length === 0 || cells.length === 0;

  const matrix = useMemo(() => {
    const indexes = new Map();
    const breaks = [];
    let count = 0;
    groups.forEach((group, i) => {
      if (i > 0 && groups[i - 1].type !== group.type) count++;
      indexes.set(group.id, i);
      breaks.push(count);
    });

    const placed = [];
    cells.forEach((cell) => {
      if (indexes.has(cell.source.id) && indexes.has(cell.destination.id)) {
        placed.push({cell: cell, row: indexes.get(cell.source.id), column: indexes.get(cell.destination.id)});
      }
    });

    return {
      indexes: indexes,
      breaks: breaks,
      sections: count + 1,
      cells: placed,
      cellsById: new Map(placed.map((p) => [p.cell.id, p]))
    };
  }, [groups, cells]);

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

  const hovered = hoveredId !== null && matrix.cellsById.has(hoveredId) ? matrix.cellsById.get(hoveredId) : null;

  if (empty) {
    return (
        <div ref={containerRef} className="border d-flex align-items-center justify-content-center text-muted"
             style={{height: height + "px"}}>
          {filtered ? "No crossings match the selected filters." : "No crossings in the selected time range."}
        </div>
    )
  }

  const gaps = (matrix.sections - 1) * SECTION_GAP;
  const cell = Math.max(MIN_CELL, Math.min(MAX_CELL,
      Math.floor((width - LEFT - RIGHT - gaps) / groups.length),
      Math.floor((height - TOP - BOTTOM - gaps - 2) / groups.length)));
  const gridSize = cell * groups.length + gaps;
  const position = (i) => ORIGIN + i * cell + matrix.breaks[i] * SECTION_GAP;

  const bar = cell >= 24 ? 4 : 3;
  const markHeight = cell - 2 - bar - 1;
  const labelSize = Math.min(12, Math.max(10, cell - 5));

  const sections = [];
  groups.forEach((group, i) => {
    if (i === 0 || matrix.breaks[i] !== matrix.breaks[i - 1]) sections.push({from: i, to: i});
    sections[sections.length - 1].to = i;
  });
  const extent = (section) => position(section.to) + cell - position(section.from);

  const selectedCell = selection && selection.kind === "cell" ? selection : null;
  const selectedGroup = selection && selection.kind === "group" && matrix.indexes.has(selection.id)
      ? matrix.indexes.get(selection.id) : null;

  const selectGroup = (group) => {
    if (onSelect) onSelect({kind: "group", id: group.id});
  }

  const selectCell = (c) => {
    if (onSelect) onSelect({kind: "cell", source: c.source.id, destination: c.destination.id});
  }

  const onPointerMove = (event) => {
    const container = containerRef.current;
    const rect = container.getBoundingClientRect();
    setPointer({
      x: event.clientX - rect.left + container.scrollLeft,
      y: event.clientY - rect.top + container.scrollTop,
      right: container.scrollLeft + container.clientWidth,
      raised: event.clientY - rect.top > 200
    });
  }

  const rowBand = (index, opacity, color) => (
      <rect x={ORIGIN} y={position(index)} width={gridSize} height={cell} fill={color} opacity={opacity} pointerEvents="none" />
  );

  const columnBand = (index, opacity, color) => (
      <rect x={position(index)} y={ORIGIN} width={cell} height={gridSize} fill={color} opacity={opacity} pointerEvents="none" />
  );

  const fontWeight = (lit) => (lit ? "bold" : "normal");
  const contentWidth = LEFT + gridSize + RIGHT;
  const headerStyle = {...gridBackground(colors), flex: "none"};

  return (
      <div ref={containerRef} className="border"
           style={{position: "relative", width: "100%", height: height + "px", overflow: "auto", ...gridBackground(colors)}}
           onMouseMove={onPointerMove}
           onMouseLeave={() => { setHovered(null); setPointer(null); }}>
        {width > 0 ?
            <div style={{width: contentWidth + "px", fontFamily: FONT_FAMILY}}>
              <div style={{position: "sticky", top: 0, zIndex: 3, display: "flex", height: TOP + "px", ...headerStyle}}
                   onMouseEnter={() => setHovered(null)}>
                <div style={{position: "sticky", left: 0, zIndex: 4, width: LEFT + "px", height: TOP + "px", ...headerStyle}}>
                  <svg width={LEFT} height={TOP} style={{display: "block"}}>
                    <text x={LEFT - 16} y={TOP - 26} textAnchor="end" fontSize="11" fill={colors.muted}>Traffic goes from</text>
                    <text x={LEFT - 16} y={TOP - 12} textAnchor="end" fontSize="11" fill={colors.muted}>rows to columns.</text>
                  </svg>
                </div>

                <svg width={gridSize + RIGHT} height={TOP} style={{display: "block", flex: "none"}}>
                  {groups.map((group, i) => {
                    const lit = (hovered && hovered.column === i) || selectedGroup === i;
                    const x = position(i) + cell / 2;
                    return (
                        <g key={group.id} style={{cursor: "pointer"}} onClick={() => selectGroup(group)}>
                          <title>{groupTitle(group)}</title>
                          <GroupGlyph group={group} x={x} y={TOP - 9} size={Math.min(8, cell - 9)} colors={colors} />
                          <text x={x + 3} y={TOP - 18} textAnchor="start" dominantBaseline="middle"
                                transform={"rotate(-52 " + (x + 3) + " " + (TOP - 18) + ")"}
                                fontSize={labelSize} fontWeight={fontWeight(lit)} fill={lit ? colors.text : colors.muted}>
                            {group.name}
                          </text>
                        </g>
                    );
                  })}
                </svg>
              </div>

              <div style={{display: "flex"}}>
                <div style={{position: "sticky", left: 0, zIndex: 2, width: LEFT + "px", ...headerStyle}}
                     onMouseEnter={() => setHovered(null)}>
                  <svg width={LEFT} height={gridSize + BOTTOM} style={{display: "block"}}>
                    {groups.map((group, i) => {
                      const lit = (hovered && hovered.row === i) || selectedGroup === i;
                      const y = position(i) + cell / 2;
                      return (
                          <g key={group.id} style={{cursor: "pointer"}} onClick={() => selectGroup(group)}>
                            <title>{groupTitle(group)}</title>
                            <GroupGlyph group={group} x={LEFT - 9} y={y} size={Math.min(8, cell - 9)} colors={colors} />
                            <text x={LEFT - 18} y={y} textAnchor="end" dominantBaseline="middle"
                                  fontSize={labelSize} fontWeight={fontWeight(lit)} fill={lit ? colors.text : colors.muted}>
                              {group.name}
                            </text>
                          </g>
                      );
                    })}
                  </svg>
                </div>

                <svg width={gridSize + RIGHT} height={gridSize + BOTTOM} style={{display: "block", flex: "none"}}>
                  <defs>
                    <HatchPattern id={HATCH} colors={colors} />
                  </defs>
                  <rect width={gridSize + RIGHT} height={gridSize + BOTTOM} fill="transparent"
                        onMouseEnter={() => setHovered(null)} />

                  {sections.flatMap((rows) => sections.map((columns) => (
                      <rect key={rows.from + ":" + columns.from}
                            x={position(columns.from) - 1} y={position(rows.from) - 1}
                            width={extent(columns) + 2} height={extent(rows) + 2}
                            fill={colors.background} stroke={colors.lines} pointerEvents="none" />
                  )))}

                  {sections.flatMap((rows) => sections.map((columns) => {
                    const lines = [];
                    for (let i = rows.from + 1; i <= rows.to; i++) {
                      lines.push("M" + position(columns.from) + "," + position(i) + "h" + extent(columns));
                    }
                    for (let i = columns.from + 1; i <= columns.to; i++) {
                      lines.push("M" + position(i) + "," + position(rows.from) + "v" + extent(rows));
                    }
                    return <path key={rows.from + ":" + columns.from} d={lines.join("")} stroke={colors.grid}
                                 strokeWidth="1" strokeOpacity="0.55" fill="none" pointerEvents="none" />;
                  }))}

                  {selectedGroup !== null ? rowBand(selectedGroup, 0.06, colors.selection) : null}
                  {selectedGroup !== null ? columnBand(selectedGroup, 0.06, colors.selection) : null}
                  {hovered ? rowBand(hovered.row, 0.07, colors.text) : null}
                  {hovered ? columnBand(hovered.column, 0.07, colors.text) : null}

                  {groups.map((group, i) => {
                    const from = position(i) + 3;
                    const to = position(i) + cell - 3;
                    return <path key={group.id} d={"M" + from + "," + from + "L" + to + "," + to}
                                 stroke={colors.lines} strokeWidth="1" pointerEvents="none" />;
                  })}

                  {matrix.cells.map((placed) => {
                    const c = placed.cell;
                    const x = position(placed.column);
                    const y = position(placed.row);
                    const isSelected = selectedCell !== null && selectedCell.source === c.source.id
                        && selectedCell.destination === c.destination.id;
                    const isHovered = hovered && hovered.row === placed.row && hovered.column === placed.column;

                    return (
                        <g key={c.id} style={{cursor: "pointer"}}
                           onMouseEnter={() => setHovered(c.id)}
                           onClick={() => selectCell(c)}>
                          {/* What covers the stroke of the diagonal where there is traffic inside of a group. */}
                          {c.inside
                              ? <rect x={x + 1} y={y + 1} width={cell - 2} height={markHeight} fill={colors.background} />
                              : null}
                          <StatusMark x={x + 1} y={y + 1} width={cell - 2} height={markHeight} nested={c.inside}
                                      status={c.status} volume={volumeOf(c.total, model.most)}
                                      colors={colors} hatchId={HATCH} />
                          <OutcomeBar x={x + 1} y={y + cell - 1 - bar} width={cell - 2} height={bar} counts={c} colors={colors} />
                          {cell >= LABELED_CELL ?
                              <text x={x + cell / 2} y={y + 1 + markHeight / 2} textAnchor="middle" dominantBaseline="central"
                                    fontSize="11" fontWeight="bold"
                                    fill={c.status === "CROSSED" ? (darkMode ? colors.background : "#ffffff") : colors.text}
                                    stroke={c.status === "NONE" || c.status === "PARTIAL" ? colors.background : "none"}
                                    strokeWidth="3" paintOrder="stroke" pointerEvents="none">
                                {countLabel(c.total)}
                              </text> : null}
                          <rect x={x} y={y} width={cell} height={cell} fill="transparent"
                                stroke={isSelected || isHovered ? colors.selection : "none"}
                                strokeWidth={isSelected ? 2 : 1.5} />
                        </g>
                    );
                  })}
                </svg>
              </div>
            </div> : null}

        {hovered && pointer ? (
            <div style={{
              ...tooltipStyle(colors),
              borderRadius: 0,
              maxWidth: "none",
              width: "250px",
              left: Math.max(0, Math.min(pointer.x + 14, pointer.right - 262)) + "px",
              top: (pointer.raised ? pointer.y - 12 : pointer.y + 24) + "px",
              transform: pointer.raised ? "translateY(-100%)" : "none"
            }}>
              <CrossingTooltip cell={hovered.cell} colors={colors} />
            </div>
        ) : null}
      </div>
  )

}
