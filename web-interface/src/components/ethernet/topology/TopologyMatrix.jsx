import React, {useEffect, useMemo, useRef, useState} from "react";
import Store from "../../../util/Store";
import {colorsForMode, FONT_FAMILY, GRID_SPACING, tooltipStyle, trafficRampForMode} from "../../shared/viz/VizTheme";
import {formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";
import {pairIdOf} from "./TopologyData";

const LEFT = 204;
const TOP = 132;
const LEGEND = 44;
const MIN_CELL = 9;
const MAX_CELL = 22;

const truncate = (s, max) => (s && s.length > max ? s.substring(0, Math.max(1, max - 1)) + "…" : s);

const compareLabels = (a, b) => a.label.localeCompare(b.label, undefined, {numeric: true, sensitivity: "base"});

const buildMatrix = (model, order, maxRows, maxColumns) => {
  const cells = new Map();
  const rowTotals = new Map();
  const columnTotals = new Map();

  const add = (source, target, bytes, sessions) => {
    if (sessions <= 0) return;
    cells.set(source + "|" + target, {source: source, target: target, bytes: bytes, sessions: sessions});
    rowTotals.set(source, (rowTotals.get(source) || 0) + bytes);
    columnTotals.set(target, (columnTotals.get(target) || 0) + bytes);
  }

  model.links.forEach((l) => add(l.source, l.target, l.bytes, l.sessions));
  model.nodes.forEach((n) => add(n.id, n.id, n.self_bytes, n.self_sessions));

  const axis = (totals, max) => {
    const all = Array.from(totals.entries()).sort((a, b) => b[1] - a[1]);
    const nodes = all.slice(0, max).map(([id, bytes]) => ({...model.nodes.get(id), axisBytes: bytes}));
    nodes.sort((a, b) => (a.type === "INTERNAL" ? 0 : 1) - (b.type === "INTERNAL" ? 0 : 1)
        || (order === "name" ? compareLabels(a, b) : b.axisBytes - a.axisBytes));
    return {nodes: nodes, hidden: all.length - nodes.length};
  }

  const rows = axis(rowTotals, maxRows);
  const columns = axis(columnTotals, maxColumns);

  let min = Infinity, max = 0;
  const shown = [];
  rows.nodes.forEach((r, ri) => columns.nodes.forEach((c, ci) => {
    const cell = cells.get(r.id + "|" + c.id);
    if (!cell) return;
    min = Math.min(min, cell.bytes);
    max = Math.max(max, cell.bytes);
    shown.push({...cell, row: ri, column: ci});
  }));

  return {rows: rows, columns: columns, cells: shown, min: min, max: max};
}

export default function TopologyMatrix({model, order = "traffic", selection = null, onSelect, height = 560,
                                         maxRows = 30, maxColumns = 44}) {

  const containerRef = useRef(null);
  const [width, setWidth] = useState(0);
  const [hovered, setHovered] = useState(null);
  const [pointer, setPointer] = useState(null);

  const darkMode = Boolean(Store.get("dark_mode"));
  const colors = colorsForMode(darkMode);
  const ramp = useMemo(() => trafficRampForMode(darkMode), [darkMode]);

  const matrix = useMemo(() => buildMatrix(model, order, maxRows, maxColumns), [model, order, maxRows, maxColumns]);
  const empty = matrix.cells.length === 0;

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

  // A hovered cell is a position in the matrix, which means something else once the matrix changes.
  useEffect(() => {
    setHovered(null);
  }, [matrix]);

  if (empty) {
    return (
        <div ref={containerRef} className="border rounded d-flex align-items-center justify-content-center text-muted"
             style={{height: height + "px"}}>
          No connections match the selected filters.
        </div>
    )
  }

  const rows = matrix.rows.nodes;
  const columns = matrix.columns.nodes;

  const cell = Math.max(MIN_CELL, Math.min(MAX_CELL,
      Math.floor((Math.max(0, width - LEFT - 24)) / Math.max(1, columns.length)),
      Math.floor((height - TOP - LEGEND - 12) / Math.max(1, rows.length))));
  const gridWidth = cell * columns.length;
  const gridHeight = cell * rows.length;
  const svgWidth = Math.max(width - 2, LEFT + gridWidth + 90);
  const svgHeight = Math.max(height - 2, TOP + gridHeight + LEGEND + 12);

  const heat = (bytes) => {
    if (matrix.max <= matrix.min) return 1;
    return (Math.log(Math.max(1, bytes)) - Math.log(Math.max(1, matrix.min)))
        / (Math.log(Math.max(1, matrix.max)) - Math.log(Math.max(1, matrix.min)));
  }

  const firstExternal = (nodes) => {
    const index = nodes.findIndex((n) => n.type !== "INTERNAL");
    return index > 0 ? index : null;
  }
  const rowDivider = firstExternal(rows);
  const columnDivider = firstExternal(columns);

  const selectedNode = selection && selection.kind === "node" ? selection.id : null;
  const selectedPair = selection && selection.kind === "link" ? selection.id : null;

  const nodeColor = (n) => (n.level === "TAP" ? colors.root : (n.type === "INTERNAL" ? colors.internal : colors.external));

  const glyph = (n, x, y, size) => n.type === "INTERNAL"
      ? <circle cx={x} cy={y} r={size / 2} fill={nodeColor(n)} />
      : <rect x={x - size / 2} y={y - size / 2} width={size} height={size} rx="1.5" fill={nodeColor(n)} />;

  const selectNode = (n) => {
    if (onSelect) onSelect({kind: "node", id: n.id});
  }

  const selectCell = (c) => {
    if (!onSelect) return;
    if (c.source === c.target) {
      onSelect({kind: "node", id: c.source});
    } else {
      onSelect({kind: "link", id: pairIdOf(c.source, c.target), a: c.source, b: c.target});
    }
  }

  // The card scrolls, and the tooltip is placed in its content, not in its viewport.
  const onPointerMove = (event) => {
    const container = containerRef.current;
    const rect = container.getBoundingClientRect();
    setPointer({
      x: event.clientX - rect.left + container.scrollLeft,
      y: event.clientY - rect.top + container.scrollTop
    });
  }

  const hiddenText = () => {
    const parts = [];
    if (matrix.rows.hidden > 0) parts.push(formatCount(matrix.rows.hidden) + " more " + (matrix.rows.hidden === 1 ? "source" : "sources"));
    if (matrix.columns.hidden > 0) parts.push(formatCount(matrix.columns.hidden) + " more " + (matrix.columns.hidden === 1 ? "destination" : "destinations"));
    if (parts.length === 0) return null;
    return parts.join(" and ") + " with less traffic " + (matrix.rows.hidden + matrix.columns.hidden === 1 ? "is" : "are") + " not shown.";
  }

  const labelSize = Math.min(11, Math.max(9, cell - 3));
  const fontWeight = (lit) => (lit ? "bold" : "normal");
  const legendY = TOP + gridHeight + 22;

  return (
      <div ref={containerRef} className="border rounded"
           style={{position: "relative", width: "100%", height: height + "px", overflow: "auto", background: colors.background}}
           onMouseMove={onPointerMove}
           onMouseLeave={() => { setHovered(null); setPointer(null); }}>
        {width > 0 ?
            // Out of the flow, so the drawing follows the width of the card and never holds it open.
            <svg width={svgWidth} height={svgHeight}
                 style={{position: "absolute", top: 0, left: 0, display: "block", fontFamily: FONT_FAMILY}}>
              <defs>
                <pattern id="topology-matrix-grid" width={GRID_SPACING} height={GRID_SPACING} patternUnits="userSpaceOnUse">
                  <rect x={GRID_SPACING / 2} y={GRID_SPACING / 2} width="1.5" height="1.5" fill={colors.grid} />
                </pattern>
                <linearGradient id="topology-matrix-ramp">
                  {[0, 0.25, 0.5, 0.75, 1].map((t) => <stop key={t} offset={(t * 100) + "%"} stopColor={ramp(t)} />)}
                </linearGradient>
              </defs>
              <rect width={svgWidth} height={svgHeight} fill={colors.background} onMouseEnter={() => setHovered(null)} />
              <rect width={svgWidth} height={svgHeight} fill="url(#topology-matrix-grid)" pointerEvents="none" />

              <text x={LEFT - 14} y={TOP - 24} textAnchor="end" fontSize="11" fill={colors.muted}>Rows initiate sessions,</text>
              <text x={LEFT - 14} y={TOP - 10} textAnchor="end" fontSize="11" fill={colors.muted}>columns accept them.</text>

              <rect x={LEFT - 1} y={TOP - 1} width={gridWidth + 2} height={gridHeight + 2} rx="3"
                    fill={colors.background} stroke={colors.lines} pointerEvents="none" />

              {hovered ?
                  <React.Fragment>
                    <rect x={LEFT} y={TOP + hovered.row * cell} width={gridWidth} height={cell}
                          fill={colors.text} opacity="0.07" pointerEvents="none" />
                    <rect x={LEFT + hovered.column * cell} y={TOP} width={cell} height={gridHeight}
                          fill={colors.text} opacity="0.07" pointerEvents="none" />
                  </React.Fragment> : null}

              {rows.map((n, i) => {
                const lit = (hovered && hovered.row === i) || selectedNode === n.id;
                return (
                    <g key={"row-" + n.id} style={{cursor: "pointer"}} onClick={() => selectNode(n)}
                       onMouseEnter={() => setHovered(null)}>
                      <title>{n.label + (n.name || n.detail ? " (" + (n.name || n.detail) + ")" : "")}</title>
                      {glyph(n, LEFT - 9, TOP + i * cell + cell / 2, Math.min(8, cell - 3))}
                      <text x={LEFT - 18} y={TOP + i * cell + cell / 2} textAnchor="end" dominantBaseline="middle"
                            fontSize={labelSize} fontWeight={fontWeight(lit)} fill={lit ? colors.text : colors.muted}>
                        {truncate(n.name || n.detail ? n.label + " " + (n.name || n.detail) : n.label, 30)}
                      </text>
                    </g>
                );
              })}

              {columns.map((n, i) => {
                const lit = (hovered && hovered.column === i) || selectedNode === n.id;
                const x = LEFT + i * cell + cell / 2;
                return (
                    <g key={"column-" + n.id} style={{cursor: "pointer"}} onClick={() => selectNode(n)}
                       onMouseEnter={() => setHovered(null)}>
                      <title>{n.label + (n.name || n.detail ? " (" + (n.name || n.detail) + ")" : "")}</title>
                      {glyph(n, x, TOP - 9, Math.min(8, cell - 3))}
                      <text x={x + 3} y={TOP - 18} textAnchor="start" dominantBaseline="middle"
                            transform={"rotate(-52 " + (x + 3) + " " + (TOP - 18) + ")"}
                            fontSize={labelSize} fontWeight={fontWeight(lit)} fill={lit ? colors.text : colors.muted}>
                        {truncate(n.label, 19)}
                      </text>
                    </g>
                );
              })}

              {matrix.cells.map((c) => {
                const pairSelected = selectedPair !== null && c.source !== c.target && pairIdOf(c.source, c.target) === selectedPair;
                const isHovered = hovered && hovered.row === c.row && hovered.column === c.column;
                const color = ramp(heat(c.bytes));
                return (
                    <rect key={c.source + "|" + c.target}
                          x={LEFT + c.column * cell + 1} y={TOP + c.row * cell + 1}
                          width={cell - 2} height={cell - 2} rx="2"
                          fill={color}
                          stroke={pairSelected || isHovered ? colors.selection : "none"} strokeWidth="1.5"
                          style={{cursor: "pointer"}}
                          onMouseEnter={() => setHovered(c)}
                          onClick={() => selectCell(c)} />
                );
              })}

              {rowDivider !== null ?
                  <line x1={LEFT - 150} x2={LEFT + gridWidth} y1={TOP + rowDivider * cell} y2={TOP + rowDivider * cell}
                        stroke={colors.muted} strokeWidth="1" strokeDasharray="3 3" pointerEvents="none" /> : null}
              {columnDivider !== null ?
                  <line x1={LEFT + columnDivider * cell} x2={LEFT + columnDivider * cell} y1={TOP - 100} y2={TOP + gridHeight}
                        stroke={colors.muted} strokeWidth="1" strokeDasharray="3 3" pointerEvents="none" /> : null}

              <text x={LEFT} y={legendY + 5} fontSize="11" fill={colors.muted} dominantBaseline="middle">
                {formatBytes(matrix.min)}
              </text>
              <rect x={LEFT + 58} y={legendY} width="150" height="10" rx="2" fill="url(#topology-matrix-ramp)" />
              <text x={LEFT + 216} y={legendY + 5} fontSize="11" fill={colors.muted} dominantBaseline="middle">
                {formatBytes(matrix.max)}
              </text>
              {hiddenText() ?
                  <text x={LEFT + 290} y={legendY + 5} fontSize="11" fill={colors.muted} dominantBaseline="middle">
                    {hiddenText()}
                  </text> : null}
            </svg> : null}

        {hovered && pointer && rows[hovered.row] && columns[hovered.column] ? (
            <div style={{
              ...tooltipStyle(colors),
              maxWidth: "290px",
              left: Math.min(pointer.x + 14, Math.max(0, svgWidth - 300)) + "px",
              top: (pointer.y > 130 ? pointer.y - 12 : pointer.y + 24) + "px",
              transform: pointer.y > 130 ? "translateY(-100%)" : "none"
            }}>
              <div style={{fontWeight: "bold", wordBreak: "break-all"}}>
                {hovered.source === hovered.target
                    ? "Within " + rows[hovered.row].label
                    : rows[hovered.row].label + " to " + columns[hovered.column].label}
              </div>
              <div>Sessions: {formatCount(hovered.sessions)}</div>
              <div>Traffic: {formatBytes(hovered.bytes)}</div>
            </div>
        ) : null}
      </div>
  )

}
