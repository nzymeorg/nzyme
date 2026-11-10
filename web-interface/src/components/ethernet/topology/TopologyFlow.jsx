import React, {useEffect, useMemo, useRef, useState} from "react";
import Store from "../../../util/Store";
import {colorsForMode, FONT_FAMILY, GRID_SPACING, tooltipStyle} from "../../shared/viz/VizTheme";
import {formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";
import {LINK_CLASS_TITLES, linkClassColor, nodeKind} from "./TopologyData";

const TOP = 36;
const BOTTOM = 14;
const NODE_WIDTH = 12;
const MIN_NODE_HEIGHT = 13;
const GAP = 4;
const HEADINGS = ["Source", "Service", "Destination"];

const shareText = (value, total) => {
  if (total <= 0) return "0%";
  const share = 100 * value / total;
  return share > 0 && share < 0.1 ? "under 0.1%" : share.toFixed(1) + "%";
}

const truncate = (s, max) => (s && s.length > max ? s.substring(0, Math.max(1, max - 1)) + "…" : s);

const bandPath = (band, y0, y1, thickness) => {
  const xm = (band.x0 + band.x1) / 2;
  return "M" + band.x0 + "," + y0
      + "C" + xm + "," + y0 + " " + xm + "," + y1 + " " + band.x1 + "," + y1
      + "L" + band.x1 + "," + (y1 + thickness)
      + "C" + xm + "," + (y1 + thickness) + " " + xm + "," + (y0 + thickness) + " " + band.x0 + "," + (y0 + thickness)
      + "Z";
}

const layout = (model, width, height) => {
  const labelWidth = Math.max(120, Math.min(230, width * 0.2));
  const xs = [labelWidth, (width - NODE_WIDTH) / 2, width - labelWidth - NODE_WIDTH];
  const available = height - TOP - BOTTOM;
  const gap = GAP;

  let scale = Infinity;
  model.columns.forEach((column) => {
    const total = column.reduce((sum, e) => sum + e.value, 0);
    if (total > 0) {
      scale = Math.min(scale, (available - gap * (column.length - 1) - MIN_NODE_HEIGHT * column.length) / total);
    }
  });
  if (!isFinite(scale) || scale < 0) scale = 0;

  const entries = new Map();
  model.columns.forEach((column, c) => {
    const used = column.reduce((sum, e) => sum + MIN_NODE_HEIGHT + e.value * scale, 0) + gap * (column.length - 1);
    let y = TOP + Math.max(0, (available - used) / 2);
    column.forEach((e, i) => {
      const h = MIN_NODE_HEIGHT + e.value * scale;
      entries.set(c + ":" + e.key, {...e, index: i, x: xs[c], y: y, h: h, out: y + MIN_NODE_HEIGHT / 2, in: y + MIN_NODE_HEIGHT / 2});
      y += h + gap;
    });
  });

  const bands = [];
  model.bands.forEach((side, s) => {
    const placed = side.map((band) => ({
      ...band, from: entries.get(s + ":" + band.from), to: entries.get((s + 1) + ":" + band.to),
      fromKey: band.from, toKey: band.to, thickness: Math.max(0.6, band.value * scale)
    })).filter((band) => band.from && band.to);

    placed.slice().sort((a, b) => a.from.index - b.from.index || a.to.index - b.to.index).forEach((band) => {
      band.y0 = band.from.out;
      band.from.out += band.value * scale;
    });
    placed.slice().sort((a, b) => a.to.index - b.to.index || a.from.index - b.from.index).forEach((band) => {
      band.y1 = band.to.in;
      band.to.in += band.value * scale;
    });
    placed.forEach((band) => {
      band.x0 = band.from.x + NODE_WIDTH;
      band.x1 = band.to.x;
      bands.push(band);
    });
  });

  return {entries: Array.from(entries.values()), bands: bands, scale: scale, labelWidth: labelWidth, xs: xs};
}

const passesThrough = (target) => {
  if (!target) return null;
  if (target.type === "entry") {
    return (path) => path.keys[target.column] === target.key;
  }
  return (path) => path.keys[target.side] === target.fromKey && path.keys[target.side + 1] === target.toKey;
}

export default function TopologyFlow({model, selection = null, onSelect, height = 560}) {

  const containerRef = useRef(null);
  const [width, setWidth] = useState(0);
  const [hovered, setHovered] = useState(null);
  const [pointer, setPointer] = useState(null);

  const darkMode = Boolean(Store.get("dark_mode"));
  const colors = colorsForMode(darkMode);

  const empty = model.columns[0].length === 0;

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

  const placed = useMemo(() => (width > 0 && !empty ? layout(model, width, height) : null), [model, width, height, empty]);

  const selected = useMemo(() => {
    if (!selection || !placed) return null;
    const entry = placed.entries.find((e) => !e.other
        && ((selection.kind === "service" && e.column === 1 && e.id === selection.id)
            || (selection.kind === "node" && e.column !== 1 && e.id === selection.id)));
    if (!entry) return null;
    // A node can be both a source and a destination. Its selection covers both.
    return selection.kind === "node"
        ? {type: "node", id: selection.id}
        : {type: "entry", column: 1, key: entry.key};
  }, [selection, placed]);

  const highlight = useMemo(() => {
    const target = hovered || selected;
    if (!target || !placed) return null;

    const test = target.type === "node"
        ? (path) => path.keys[0] === target.id || path.keys[2] === target.id
        : passesThrough(target);
    const paths = model.paths.filter(test);

    const portions = new Map();
    placed.bands.forEach((band) => {
      let value = 0;
      paths.forEach((path) => {
        if (path.keys[band.side] === band.fromKey && path.keys[band.side + 1] === band.toKey) value += path.value;
      });
      if (value > 0) portions.set(band.id, value);
    });

    const lit = new Set();
    paths.forEach((path) => path.keys.forEach((key, column) => lit.add(column + ":" + key)));

    return {portions: portions, entries: lit};
  }, [hovered, selected, placed, model]);

  if (empty) {
    return (
        <div ref={containerRef} className="border rounded d-flex align-items-center justify-content-center text-muted"
             style={{height: height + "px"}}>
          No flows match the selected filters.
        </div>
    )
  }

  const entryColor = (e) => {
    if (e.other) return colors.muted;
    if (e.column === 1) return colors.text;
    if (e.node.level === "TAP") return colors.root;
    return e.node.type === "INTERNAL" ? colors.internal : colors.external;
  }

  const onPointerMove = (event) => {
    const rect = containerRef.current.getBoundingClientRect();
    setPointer({x: event.clientX - rect.left, y: event.clientY - rect.top});
  }

  const selectEntry = (e) => {
    if (e.other || !onSelect) return;
    onSelect(e.column === 1 ? {kind: "service", id: e.id} : {kind: "node", id: e.id});
  }

  const tooltipBody = () => {
    if (!hovered || !placed) return null;

    if (hovered.type === "entry") {
      const e = placed.entries.find((x) => x.column === hovered.column && x.key === hovered.key);
      if (!e) return null;
      return (
          <React.Fragment>
            <div style={{fontWeight: "bold", wordBreak: "break-all"}}>
              {e.label}
              {e.name ? <span style={{color: colors.muted, fontWeight: "normal"}}> {e.name}</span> : null}
            </div>
            <div style={{color: colors.muted}}>
              {e.other
                  ? formatCount(e.folded.size) + " smaller " + (e.column === 1 ? "services" : (e.column === 0 ? "sources" : "destinations"))
                  : (e.column === 1 ? "Service" : nodeKind(e.node) + (e.column === 0 ? ", as source" : ", as destination"))}
            </div>
            <div>Sessions: {formatCount(e.sessions)}</div>
            <div>Traffic: {formatBytes(e.value)} ({shareText(e.value, model.total)} of all flows)</div>
          </React.Fragment>
      );
    }

    const band = placed.bands.find((b) => b.id === hovered.id);
    if (!band) return null;
    return (
        <React.Fragment>
          <div style={{fontWeight: "bold", wordBreak: "break-all"}}>{band.from.label} to {band.to.label}</div>
          <div style={{color: colors.muted}}>Mostly {LINK_CLASS_TITLES[band.cls].toLowerCase()} traffic</div>
          <div>Sessions: {formatCount(band.sessions)}</div>
          <div>Traffic: {formatBytes(band.value)}</div>
        </React.Fragment>
    );
  }

  const labelCharacters = placed ? Math.floor((placed.labelWidth - 16) / 6.3) : 20;
  const body = tooltipBody();

  return (
      <div ref={containerRef} className="border rounded"
           style={{position: "relative", width: "100%", height: height + "px", overflow: "hidden"}}
           onMouseMove={onPointerMove}
           onMouseLeave={() => { setHovered(null); setPointer(null); }}>
        {placed ?
            // Out of the flow, so the drawing follows the width of the card and never holds it open.
            <svg width={width} height={height}
                 style={{position: "absolute", top: 0, left: 0, display: "block", borderRadius: "4px", fontFamily: FONT_FAMILY}}>
              <defs>
                <pattern id="topology-flow-grid" width={GRID_SPACING} height={GRID_SPACING} patternUnits="userSpaceOnUse">
                  <rect x={GRID_SPACING / 2} y={GRID_SPACING / 2} width="1.5" height="1.5" fill={colors.grid} />
                </pattern>
              </defs>
              <rect width={width} height={height} fill={colors.background} onMouseEnter={() => setHovered(null)} />
              <rect width={width} height={height} fill="url(#topology-flow-grid)" pointerEvents="none" />

              {HEADINGS.map((heading, c) => (
                  <text key={heading}
                        x={c === 0 ? placed.xs[0] + NODE_WIDTH : (c === 1 ? placed.xs[1] + NODE_WIDTH / 2 : placed.xs[2])}
                        y={20}
                        textAnchor={c === 0 ? "end" : (c === 1 ? "middle" : "start")}
                        fontSize="11" fontWeight="bold" fill={colors.muted}
                        style={{textTransform: "uppercase", letterSpacing: "0.04em"}}>
                    {heading}
                  </text>
              ))}

              {placed.bands.map((band) => (
                  <path key={band.id}
                        d={bandPath(band, band.y0, band.y1, band.thickness)}
                        fill={linkClassColor(colors, band.cls)}
                        opacity={highlight ? 0.1 : 0.34}
                        onMouseEnter={() => setHovered({type: "band", id: band.id, side: band.side, fromKey: band.fromKey, toKey: band.toKey})} />
              ))}

              {highlight ? placed.bands.map((band) => {
                const value = highlight.portions.get(band.id);
                if (!value) return null;
                const color = linkClassColor(colors, band.cls);
                return (
                    <path key={"lit-" + band.id}
                          d={bandPath(band, band.y0, band.y1, Math.max(0.6, value * placed.scale))}
                          fill={color}
                          opacity={0.85}
                          pointerEvents="none"
                          style={darkMode ? {filter: "drop-shadow(0 0 3px " + color + ")"} : null} />
                );
              }) : null}

              {placed.entries.map((e) => {
                const color = entryColor(e);
                const dimmed = highlight && !highlight.entries.has(e.column + ":" + e.key);
                const isSelected = selected && !e.other
                    && ((selected.type === "node" && e.column !== 1 && e.id === selected.id)
                        || (selected.type === "entry" && e.column === 1 && e.key === selected.key));
                const textX = e.column === 0 ? e.x - 8 : e.x + NODE_WIDTH + 8;
                const oneLine = e.h < 26;
                const first = truncate(e.label, oneLine ? labelCharacters - 8 : labelCharacters);
                const second = formatBytes(e.value) + (!oneLine && e.name ? " " + truncate(e.name, labelCharacters - 8) : "");
                const plateWidth = 10 + 6.1 * (oneLine ? first.length + 1 + second.length : Math.max(first.length, second.length));

                return (
                    <g key={e.column + ":" + e.key}
                       opacity={dimmed ? 0.3 : 1}
                       style={{cursor: e.other ? "default" : "pointer"}}
                       onMouseEnter={() => setHovered({type: "entry", column: e.column, key: e.key})}
                       onClick={() => selectEntry(e)}>
                      <rect x={e.x} y={e.y} width={NODE_WIDTH} height={e.h} rx="2"
                            fill={color}
                            opacity={e.column === 1 && !e.other ? 0.8 : 1}
                            stroke={isSelected ? colors.selection : "none"} strokeWidth="2"
                            style={darkMode && !dimmed ? {filter: "drop-shadow(0 0 4px " + color + ")"} : null} />
                      {/* Wide and invisible, so thin entries and their labels are easy to point at. */}
                      <rect x={e.column === 0 ? 0 : e.x} y={e.y - 2}
                            width={e.column === 0 ? e.x + NODE_WIDTH
                                : (e.column === 2 ? width - e.x : NODE_WIDTH + Math.min(130, 14 + (e.label.length + 8) * 6.3))}
                            height={e.h + 4} fill="transparent" />
                      {e.column === 1 ?
                          <rect x={textX - 4} y={e.y + e.h / 2 - (oneLine ? 7.5 : 14)} width={plateWidth}
                                height={oneLine ? 15 : 29} rx="3" fill={colors.background} opacity="0.82" /> : null}
                      <text x={textX} y={e.y + e.h / 2 - (oneLine ? 0 : 6)}
                            textAnchor={e.column === 0 ? "end" : "start"} dominantBaseline="middle"
                            fontSize="11" fill={colors.text}>
                        {first}
                        {oneLine ? <tspan fill={colors.muted}> {second}</tspan> : null}
                      </text>
                      {oneLine ? null :
                          <text x={textX} y={e.y + e.h / 2 + 8}
                                textAnchor={e.column === 0 ? "end" : "start"} dominantBaseline="middle"
                                fontSize="11" fill={colors.muted}>
                            {second}
                          </text>}
                    </g>
                );
              })}
            </svg> : null}

        {body && pointer ? (
            <div style={{
              ...tooltipStyle(colors),
              maxWidth: "290px",
              left: Math.min(pointer.x + 14, Math.max(0, width - 300)) + "px",
              top: (pointer.y > 130 ? pointer.y - 12 : pointer.y + 24) + "px",
              transform: pointer.y > 130 ? "translateY(-100%)" : "none"
            }}>
              {body}
            </div>
        ) : null}
      </div>
  )

}
