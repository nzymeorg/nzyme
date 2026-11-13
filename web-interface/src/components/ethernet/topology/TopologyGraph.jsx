import React, {useCallback, useEffect, useRef, useState} from "react";
import {
  drag,
  forceCollide,
  forceLink,
  forceManyBody,
  forceRadial,
  forceSimulation,
  forceX,
  forceY,
  select,
  zoom,
  zoomIdentity
} from "d3";
import Store from "../../../util/Store";
import ZoomHint, {useZoomHint, wheelZoomDelta, wheelZooms} from "../../shared/ZoomHint";
import {colorsForMode, drawGrid, FONT_FAMILY, tooltipStyle} from "../../shared/viz/VizTheme";
import {countryName, formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";
import {linkClassColor, nodeFilter, nodeKind, pairIdOf} from "./TopologyData";

const MIN_RADIUS = 4;
const MAX_RADIUS = 15;
const MIN_LINK_WIDTH = 1;
const MAX_LINK_WIDTH = 6;
const AUTO_LABELS = 12;
const MAX_GROUPS_PER_RING = 12;
const GROUP_LABEL_OFFSET = 22;
const OTHER_GROUP = "__other__";
const FIT_PADDING = {x: 40, top: 40, bottom: 40};
// Nobody can pan a stage, so it gives less room away. Labels hang below their nodes.
const STAGE_FIT_PADDING = {x: 28, top: 14, bottom: 36};
const STAGE_LABEL_AREA = 45000;
const FIT_MAX_SCALE = 1.6;
// Shadows are the most expensive part of a frame. Past this many nodes the glow goes.
const GLOW_NODE_LIMIT = 350;
const INACTIVE_ALPHA = 0.14;
const DIMMED_ALPHA = 0.3;
const SIZE_EASING = 90;
const CROWDED_LINKS = 80;
const MIN_CROWDING = 0.2;
const SPOTLIGHT_DELAY = 160;
const DETAILS_WIDTH = 290;
const LIVE_REHEAT = 0.12;
const FOLLOW_EASING = 0.05;
const PULSE_MS = 700;
const PULSE_LENGTH = 0.22;
const MAX_REMEMBERED_POSITIONS = 3000;

const reducedMotion = () => typeof window.matchMedia === "function"
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const truncate = (s, max) => (s && s.length > max ? s.substring(0, max - 1) + "…" : s);

const linkWidth = (bytes) => {
  if (!bytes || bytes <= 0) return MIN_LINK_WIDTH;
  const w = (Math.log10(bytes) - 3) * (MAX_LINK_WIDTH - MIN_LINK_WIDTH) / 9 + MIN_LINK_WIDTH;
  return Math.max(MIN_LINK_WIDTH, Math.min(MAX_LINK_WIDTH, w));
}

const nodeRadius = (node) => node.radius || MIN_RADIUS;

const layoutRadius = (node) => node.layoutRadius || MIN_RADIUS;

const fewerLinks = (link) => Math.min(link.source.degree, link.target.degree);

const linkDistance = (link) => {
  const fewer = fewerLinks(link);
  const radii = layoutRadius(link.source) + layoutRadius(link.target);
  return fewer <= 1
      ? 40 + radii + 3 * Math.sqrt(Math.max(link.source.degree, link.target.degree))
      : 70 + radii + 16 * Math.sqrt(fewer - 1);
}

const linkStrength = (link) => {
  const fewer = fewerLinks(link);
  return fewer <= 1 ? 0.7 : 0.6 / fewer;
}

const chargeStrength = (arrangement, node) => {
  const links = Math.min(node.degree || 0, 40);
  switch (arrangement) {
    case "force": return -(110 + 22 * links);
    case "perimeter": return -(90 + 10 * links);
    default: return -45;
  }
}

const radiusOf = (value, largest) => MIN_RADIUS
    + (MAX_RADIUS - MIN_RADIUS) * (largest > 0 ? Math.sqrt(Math.min(1, Math.max(0, value) / largest)) : 0);

const groupOf = (node) => {
  const data = node.data;

  if (node.type === "INTERNAL") {
    if (data.level === "HOST" && data.address && data.address.indexOf(".") > 0) {
      const network = data.address.split(".").slice(0, 3).join(".") + ".0/24";
      return {key: "net:" + network, label: network, ring: 0};
    }
    return {key: "internal", label: "Internal", ring: 0};
  }

  return data.country_code
      ? {key: "cc:" + data.country_code, label: countryName(data.country_code), ring: 1}
      : {key: "cc:none", label: data.level === "NETWORK" ? "Several or unknown countries" : "Unknown country", ring: 1};
}

const groupLabelPosition = (group) => {
  let extent = 0;
  group.members.forEach((n) => {
    const along = (n.x - group.cx) * group.ux + (n.y - group.cy) * group.uy;
    extent = Math.max(extent, along + layoutRadius(n));
  });
  const distance = Math.max(extent, group.distance) + GROUP_LABEL_OFFSET;
  return {x: group.cx + group.ux * distance, y: group.cy + group.uy * distance};
}

const fitTransform = (nodes, groups, width, height, padding) => {
  if (nodes.length === 0 || width <= 0 || height <= 0) return null;

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  const include = (x, y, margin) => {
    minX = Math.min(minX, x - margin);
    maxX = Math.max(maxX, x + margin);
    minY = Math.min(minY, y - margin);
    maxY = Math.max(maxY, y + margin);
  }
  nodes.forEach((n) => include(n.x, n.y, layoutRadius(n) + 6));
  groups.forEach((g) => {
    const l = groupLabelPosition(g);
    include(l.x, l.y, 60);
  });

  const room = Math.max(1, height - padding.top - padding.bottom);
  const k = Math.min(FIT_MAX_SCALE, Math.max(1, width - padding.x * 2) / Math.max(1, maxX - minX),
      room / Math.max(1, maxY - minY));
  return zoomIdentity
      .translate(width / 2 - k * (minX + maxX) / 2, padding.top + room / 2 - k * (minY + maxY) / 2)
      .scale(k);
}

const layoutAnchors = (arrangement, nodes, cx, cy) => {
  const externalCount = nodes.filter((n) => n.type !== "INTERNAL").length;
  const internalCount = nodes.length - externalCount;

  const squash = Math.pow(Math.max(1, Math.min(2.4, cy > 0 ? cx / cy : 1)), 2.4);

  nodes.forEach((n) => {
    n.anchor = {x: cx, y: cy};
    n.anchorStrength = 0.035;
    n.anchorSquash = arrangement === "force" ? squash : 1;
    n.ringRadius = 0;
    n.ringStrength = 0;
  });

  if (arrangement === "perimeter") {
    const ring = Math.max(240, 120 + 34 * Math.sqrt(internalCount), externalCount * 22 / (2 * Math.PI));
    nodes.forEach((n) => {
      if (n.type === "INTERNAL") {
        n.anchorStrength = 0.05;
      } else {
        n.anchorStrength = 0;
        n.ringRadius = ring;
        n.ringStrength = 0.6;
      }
    });
    return [];
  }

  if (arrangement !== "clusters") {
    return [];
  }

  const groups = new Map();
  nodes.forEach((n) => {
    const g = groupOf(n);
    if (!groups.has(g.key)) groups.set(g.key, {key: g.key, label: g.label, ring: g.ring, members: [], bytes: 0});
    const group = groups.get(g.key);
    group.members.push(n);
    group.bytes += n.fullBytes;
  });

  const result = [];
  [0, 1].forEach((ring) => {
    let ordered = Array.from(groups.values()).filter((g) => g.ring === ring).sort((a, b) => b.bytes - a.bytes);
    if (ordered.length > MAX_GROUPS_PER_RING) {
      const other = {key: OTHER_GROUP + ring, label: "Other", ring: ring, members: [], bytes: 0};
      ordered.slice(MAX_GROUPS_PER_RING - 1).forEach((g) => {
        other.members.push(...g.members);
        other.bytes += g.bytes;
      });
      ordered = ordered.slice(0, MAX_GROUPS_PER_RING - 1).concat([other]);
    }

    const innerGroups = ring === 0 ? ordered.length : 0;
    const radius = ring === 0
        ? (innerGroups <= 1 ? 0 : Math.max(110, innerGroups * 70 / (2 * Math.PI)))
        : Math.max(330, ordered.length * 95 / (2 * Math.PI));

    ordered.forEach((group, i) => {
      const angle = (ring === 0 ? -Math.PI / 2 : -Math.PI / 2 + Math.PI / Math.max(2, ordered.length))
          + i * 2 * Math.PI / ordered.length;
      const distance = radius === 0 ? 0 : radius + 6 * Math.sqrt(group.members.length);

      group.cx = cx;
      group.cy = cy;
      group.ux = radius === 0 ? 0 : Math.cos(angle);
      group.uy = radius === 0 ? -1 : Math.sin(angle);
      group.distance = distance;
      group.x = cx + Math.cos(angle) * distance;
      group.y = cy + Math.sin(angle) * distance;

      group.members.forEach((n) => {
        n.anchor = {x: group.x, y: group.y};
        n.anchorStrength = 0.16;
      });

      result.push(group);
    });
  });

  return result;
}

const pairDirectionText = (pair, a, b) => {
  const lines = [];
  if (pair.forward && pair.forward.sessions > 0) {
    lines.push(truncate(a.label, 22) + " to " + truncate(b.label, 22) + ": "
        + formatCount(pair.forward.sessions) + (pair.forward.sessions === 1 ? " session, " : " sessions, ")
        + formatBytes(pair.forward.bytes));
  }
  if (pair.backward && pair.backward.sessions > 0) {
    lines.push(truncate(b.label, 22) + " to " + truncate(a.label, 22) + ": "
        + formatCount(pair.backward.sessions) + (pair.backward.sessions === 1 ? " session, " : " sessions, ")
        + formatBytes(pair.backward.bytes));
  }
  return lines;
}

export default function TopologyGraph({model, scale = null, dataKey, minBytes = 0, arrangement = "force",
                                        sizeBy = "bytes", labels = "auto", selectedId = null, onSelect, onFilter,
                                        focus = null, height = 560, exportRef = null, live = false,
                                        pulses = null, stage = null}) {

  // A stage ({colors, fontFamily, unit}) is a drawing that nobody operates, like a widget on a wall: it fills its
  // container, has no controls and takes no pointer. One unit is that many pixels.

  const containerRef = useRef(null);
  const canvasRef = useRef(null);

  // d3-force mutates nodes and links in place, so they live in refs, not in state.
  const nodesRef = useRef(new Map());
  const visibleRef = useRef({nodes: [], links: []});
  const linksByIdRef = useRef(new Map());
  const departedPositionsRef = useRef(new Map());
  const pulsesRef = useRef([]);
  const liveRef = useRef(live);
  const fittedRef = useRef(false);
  const simulationRef = useRef(null);
  const zoomRef = useRef(null);
  const transformRef = useRef(zoomIdentity);
  const sizeRef = useRef({width: 0, height: height});
  const frameRef = useRef(null);
  const drawRef = useRef(null);
  const hoveredRef = useRef(null);
  const hoveredLinkRef = useRef(null);
  const spotlightRef = useRef(null);
  const spotlightTimerRef = useRef(null);
  const selectedRef = useRef(selectedId);
  const frozenRef = useRef(false);
  const groupsRef = useRef([]);
  const fitPendingRef = useRef(false);
  const dataKeyRef = useRef(null);
  const optionsRef = useRef({minBytes: minBytes, arrangement: arrangement, sizeBy: sizeBy, labels: labels});
  const modelRef = useRef(model);
  const scaleRef = useRef(scale);
  const easingRef = useRef(null);
  const colorsRef = useRef(colorsForMode(false));
  const stageRef = useRef(stage);
  const resizeRef = useRef(null);
  const callbacksRef = useRef({onSelect: null, onFilter: null});
  // A focus that was already there when the graph was created is old news, not a request to move.
  const handledFocusRef = useRef(focus ? focus.at : null);

  const [frozen, setFrozen] = useState(false);
  const [tooltip, setTooltip] = useState(null);
  const [zoomHint, showZoomHint] = useZoomHint();

  // A live graph stays up while nothing happens: taking it down would lose where its nodes were.
  const hasData = live || model.nodes.size > 0;
  const darkMode = Boolean(Store.get("dark_mode"));
  const colors = colorsForMode(darkMode);

  const scheduleDraw = useCallback(() => {
    if (frameRef.current !== null) return;
    frameRef.current = window.requestAnimationFrame(() => {
      frameRef.current = null;
      if (drawRef.current) drawRef.current();
    });
  }, []);

  const easeSizes = useCallback(() => {
    if (easingRef.current !== null) return;

    let last = null;
    const frame = (now) => {
      const share = last === null ? 0 : 1 - Math.exp(-(now - last) / SIZE_EASING);
      last = now;

      let moving = false;
      visibleRef.current.nodes.forEach((n) => {
        const distance = n.targetRadius - n.radius;
        if (Math.abs(distance) < 0.05) {
          n.radius = n.targetRadius;
        } else {
          n.radius += distance * share;
          moving = true;
        }
      });

      // Drawn here, in place of a frame that is already waiting, so that no frame is drawn twice.
      if (frameRef.current !== null) {
        window.cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
      if (drawRef.current) drawRef.current();
      easingRef.current = moving ? window.requestAnimationFrame(frame) : null;
    }

    easingRef.current = window.requestAnimationFrame(frame);
  }, []);

  const draw = (target = null) => {
    const canvas = target ? target.canvas : canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    const dpr = target ? target.scale : (window.devicePixelRatio || 1) * (stageRef.current ? stageRef.current.unit : 1);
    const {width, height: h} = sizeRef.current;
    const t = transformRef.current;
    const c = colorsRef.current;
    const fontFamily = stageRef.current ? stageRef.current.fontFamily : FONT_FAMILY;
    const {nodes, links} = visibleRef.current;
    const hovered = hoveredRef.current;
    const hoveredLink = hoveredLinkRef.current;
    const selected = selectedRef.current;
    const glow = nodes.length <= GLOW_NODE_LIMIT;

    let selectedNode = null;
    let selectedLink = null;
    if (selected) {
      selectedNode = nodes.find((n) => n.id === selected) || null;
      if (!selectedNode) selectedLink = links.find((l) => l.id === selected) || null;
    }

    const spotNode = spotlightRef.current || selectedNode;
    const spotLink = spotlightRef.current ? null : selectedLink;
    const linkInSpot = (l) => l === spotLink || (spotNode !== null && (l.source === spotNode || l.target === spotNode));

    let lit = null;
    if (spotNode || spotLink) {
      lit = new Set();
      if (spotNode) lit.add(spotNode.id);
      links.forEach((l) => {
        if (linkInSpot(l)) {
          lit.add(l.source.id);
          lit.add(l.target.id);
        }
      });
    }

    const linkLit = (l) => lit === null || linkInSpot(l);

    const crowding = Math.max(MIN_CROWDING, Math.min(1, Math.sqrt(CROWDED_LINKS / Math.max(1, links.length))));
    const widthOf = (link) => MIN_LINK_WIDTH + (linkWidth(link.bytes) - MIN_LINK_WIDTH) * crowding;
    const idleAlpha = (link) => {
      const weight = (linkWidth(link.bytes) - MIN_LINK_WIDTH) / (MAX_LINK_WIDTH - MIN_LINK_WIDTH);
      return 0.8 * (crowding + (1 - crowding) * weight);
    }
    const nodeAlpha = (n) => {
      if (!n.active) return INACTIVE_ALPHA;
      return lit === null || lit.has(n.id) ? 1 : DIMMED_ALPHA;
    }
    const isHighlighted = (node) => node === hovered || node === selectedNode
        || (hoveredLink !== null && (hoveredLink.source === node || hoveredLink.target === node))
        || (selectedLink !== null && (selectedLink.source === node || selectedLink.target === node));

    ctx.save();
    ctx.scale(dpr, dpr);
    drawGrid(ctx, c, width, h);

    ctx.translate(t.x, t.y);
    ctx.scale(t.k, t.k);

    const strokeLink = (link, extraWidth) => {
      const s = link.source;
      const d = link.target;
      const color = linkClassColor(c, link.cls);
      const w = widthOf(link) + extraWidth;

      ctx.beginPath();
      ctx.moveTo(s.x, s.y);
      ctx.lineTo(d.x, d.y);
      ctx.lineWidth = w;
      ctx.setLineDash(link.udp && !link.tcp ? [4, 3] : []);
      ctx.strokeStyle = color;
      ctx.stroke();
      ctx.setLineDash([]);

      const dx = d.x - s.x;
      const dy = d.y - s.y;
      const length = Math.hypot(dx, dy);
      if (length < 1) return;
      const ux = dx / length;
      const uy = dy / length;
      const size = 1 + 2.5 * crowding + w;

      const arrow = (tipX, tipY, dirX, dirY) => {
        ctx.beginPath();
        ctx.moveTo(tipX, tipY);
        ctx.lineTo(tipX - dirX * size * 1.7 - dirY * size * 0.75, tipY - dirY * size * 1.7 + dirX * size * 0.75);
        ctx.lineTo(tipX - dirX * size * 1.7 + dirY * size * 0.75, tipY - dirY * size * 1.7 - dirX * size * 0.75);
        ctx.closePath();
        ctx.fillStyle = color;
        ctx.fill();
      }

      if (link.toTarget) {
        const r = nodeRadius(d) + 2;
        arrow(d.x - ux * r, d.y - uy * r, ux, uy);
      }
      if (link.toSource) {
        const r = nodeRadius(s) + 2;
        arrow(s.x + ux * r, s.y + uy * r, -ux, -uy);
      }
    }

    links.forEach((link) => {
      if (lit !== null && linkLit(link)) return;
      ctx.globalAlpha = !link.active ? INACTIVE_ALPHA * 0.8 * crowding
          : (lit === null ? idleAlpha(link) : DIMMED_ALPHA * 0.6 * crowding);
      strokeLink(link, 0);
    });
    if (lit !== null) {
      links.forEach((link) => {
        if (!linkLit(link)) return;
        ctx.globalAlpha = link.active ? 0.9 : INACTIVE_ALPHA * 0.8;
        strokeLink(link, 0);
      });
    }
    ctx.globalAlpha = 1;

    [hoveredLink, selectedLink].forEach((link) => {
      if (!link) return;
      const color = linkClassColor(c, link.cls);
      ctx.shadowColor = color;
      // Shadows ignore the canvas transform, so the blur is in device pixels regardless of zoom.
      ctx.shadowBlur = c.glow * dpr;
      strokeLink(link, 2);
      ctx.shadowBlur = 0;
      ctx.shadowColor = "transparent";
    });

    if (!target && pulsesRef.current.length > 0) {
      const now = window.performance.now();
      pulsesRef.current = pulsesRef.current.filter((p) => now - p.at < PULSE_MS);
      ctx.lineCap = "round";
      pulsesRef.current.forEach((p) => {
        const link = linksByIdRef.current.get(p.linkId);
        if (now < p.at || !link || !linkLit(link)) return;

        const progress = (now - p.at) / PULSE_MS;
        const from = p.forward ? link.source : link.target;
        const to = p.forward ? link.target : link.source;
        const tail = Math.max(0, progress - PULSE_LENGTH);

        ctx.globalAlpha = Math.min(1, 6 * (1 - progress));
        ctx.beginPath();
        ctx.moveTo(from.x + (to.x - from.x) * tail, from.y + (to.y - from.y) * tail);
        ctx.lineTo(from.x + (to.x - from.x) * progress, from.y + (to.y - from.y) * progress);
        ctx.lineWidth = widthOf(link) + 2.5;
        ctx.strokeStyle = linkClassColor(c, link.cls);
        ctx.stroke();
      });
      ctx.lineCap = "butt";
      ctx.globalAlpha = 1;
    }

    nodes.forEach((node) => {
      const r = nodeRadius(node);
      const anchorNode = node.level === "TAP";
      const fill = anchorNode ? c.root : (node.type === "INTERNAL" ? c.internal : c.external);
      const alpha = nodeAlpha(node);

      ctx.globalAlpha = alpha;

      if (anchorNode) {
        ctx.beginPath();
        ctx.arc(node.x, node.y, r + 6, 0, 2 * Math.PI);
        ctx.fillStyle = fill;
        ctx.globalAlpha = 0.22 * alpha;
        ctx.fill();
        ctx.globalAlpha = alpha;
      }

      ctx.beginPath();
      if (node.type === "EXTERNAL") {
        if (ctx.roundRect) ctx.roundRect(node.x - r, node.y - r, r * 2, r * 2, 3);
        else ctx.rect(node.x - r, node.y - r, r * 2, r * 2);
      } else {
        ctx.arc(node.x, node.y, r, 0, 2 * Math.PI);
      }
      ctx.fillStyle = fill;
      if (glow && alpha === 1) {
        ctx.shadowColor = fill;
        ctx.shadowBlur = c.glow * dpr;
      }
      ctx.fill();
      ctx.shadowBlur = 0;
      ctx.shadowColor = "transparent";
      ctx.globalAlpha = 1;

      if (isHighlighted(node)) {
        ctx.beginPath();
        ctx.arc(node.x, node.y, r + 3, 0, 2 * Math.PI);
        ctx.lineWidth = node === selectedNode ? 2 : 1;
        ctx.strokeStyle = c.selection;
        ctx.stroke();
      }
    });

    const fontSize = 11 / t.k;
    const lineHeight = 14 / t.k;
    const padding = 4 / t.k;

    const placed = [];
    const overlaps = (x, y, w, h) => placed.some((p) => x < p.x + p.w && p.x < x + w && y < p.y + p.h && p.y < y + h);

    ctx.font = "bold " + fontSize + "px " + fontFamily;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    groupsRef.current.forEach((group) => {
      const text = group.label + " (" + formatCount(group.members.length) + ")";
      const l = groupLabelPosition(group);
      const plateWidth = ctx.measureText(text).width + padding * 3;
      const plateHeight = lineHeight + padding;
      placed.push({x: l.x - plateWidth / 2, y: l.y - plateHeight / 2, w: plateWidth, h: plateHeight});

      ctx.globalAlpha = 0.88;
      ctx.fillStyle = c.background;
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(l.x - plateWidth / 2, l.y - plateHeight / 2, plateWidth, plateHeight, 3 / t.k);
      else ctx.rect(l.x - plateWidth / 2, l.y - plateHeight / 2, plateWidth, plateHeight);
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.lineWidth = 1 / t.k;
      ctx.strokeStyle = c.lines;
      ctx.stroke();

      ctx.fillStyle = c.text;
      ctx.fillText(text, l.x, l.y);
    });

    const mode = optionsRef.current.labels;
    const autoLabels = stageRef.current
        ? Math.max(3, Math.min(16, Math.round(width * h / STAGE_LABEL_AREA))) : AUTO_LABELS;
    const labeled = (node) => {
      if (!node.label) return false;
      if (isHighlighted(node)) return true;
      if (!node.active) return false;
      if (lit !== null) return lit.has(node.id) && (lit.size <= 30 || node.rank < AUTO_LABELS);
      if (mode === "all") return true;
      return mode === "auto" && node.rank < autoLabels;
    }

    ctx.textBaseline = "top";
    nodes.filter(labeled).sort((a, b) => (isHighlighted(b) ? 1 : 0) - (isHighlighted(a) ? 1 : 0) || a.rank - b.rank).forEach((node) => {
      const strong = isHighlighted(node);
      const lines = [truncate(node.label, 28)];
      const second = node.name || node.detail;
      if (second && (strong || mode !== "all")) lines.push(truncate(second, 28));

      const top = node.y + nodeRadius(node) + 4 / t.k;
      const textWidth = Math.max(...lines.map((line) => ctx.measureText(line).width));
      const plateWidth = textWidth + padding * 2;
      const plateHeight = lines.length * lineHeight + padding * 2 - (lineHeight - fontSize);

      // A stage cannot be panned, so a label at its edge is moved in.
      const center = stageRef.current && plateWidth < width / t.k
          ? Math.max(t.invertX(0) + plateWidth / 2, Math.min(t.invertX(width) - plateWidth / 2, node.x)) : node.x;

      if (!strong && overlaps(center - plateWidth / 2, top, plateWidth, plateHeight)) return;
      placed.push({x: center - plateWidth / 2, y: top, w: plateWidth, h: plateHeight});

      ctx.globalAlpha = strong ? 0.9 : 0.7;
      ctx.fillStyle = c.background;
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(center - plateWidth / 2, top, plateWidth, plateHeight, 3 / t.k);
      else ctx.rect(center - plateWidth / 2, top, plateWidth, plateHeight);
      ctx.fill();
      ctx.globalAlpha = 1;

      lines.forEach((line, i) => {
        ctx.fillStyle = i === 0 ? c.text : c.muted;
        ctx.fillText(line, center, top + padding + i * lineHeight);
      });
    });

    ctx.restore();

    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.font = "11px " + fontFamily;
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";
    // A stage explains its marks itself, in its own type.
    const legend = stageRef.current ? [] : [
      {text: "Internal node", node: "circle", color: c.internal},
      {text: "External node", node: "square", color: c.external},
      {text: "Outbound", color: c.outbound},
      {text: "Inbound", color: c.inbound},
      {text: "Both directions", color: c.both},
      {text: "Internal to internal", color: c.internalLink},
      {text: "UDP dashed, TCP solid", color: c.muted, dashed: true}
    ];
    legend.forEach((entry, i) => {
      const y = h - 12 - (legend.length - 1 - i) * 16;
      if (entry.node) {
        ctx.beginPath();
        if (entry.node === "square") {
          if (ctx.roundRect) ctx.roundRect(17, y - 5, 10, 10, 2); else ctx.rect(17, y - 5, 10, 10);
        } else {
          ctx.arc(22, y, 5, 0, 2 * Math.PI);
        }
        ctx.fillStyle = entry.color;
        ctx.fill();
      } else {
        ctx.beginPath();
        ctx.moveTo(10, y);
        ctx.lineTo(34, y);
        ctx.lineWidth = 2;
        ctx.setLineDash(entry.dashed ? [4, 3] : []);
        ctx.strokeStyle = entry.color;
        ctx.stroke();
        ctx.setLineDash([]);
      }
      ctx.fillStyle = c.muted;
      ctx.fillText(entry.text, 40, y);
    });
    ctx.restore();

    if (!target && pulsesRef.current.length > 0) scheduleDraw();
  }

  const renderForExport = (scale) => {
    const {width, height: h} = sizeRef.current;
    if (width <= 0 || h <= 0) return null;

    const canvas = document.createElement("canvas");
    canvas.width = Math.round(width * scale);
    canvas.height = Math.round(h * scale);
    draw({canvas: canvas, scale: scale});
    return {canvas: canvas, scale: scale};
  }

  useEffect(() => {
    drawRef.current = draw;
    if (exportRef) exportRef.current = renderForExport;
    callbacksRef.current = {onSelect: onSelect, onFilter: onFilter};
  });

  useEffect(() => () => {
    if (exportRef) exportRef.current = null;
  }, [exportRef]);

  const stageColors = stage ? stage.colors : null;
  const stageFont = stage ? stage.fontFamily : null;
  const stageUnit = stage ? stage.unit : null;

  useEffect(() => {
    stageRef.current = stage;
    colorsRef.current = stageColors ? stageColors : colorsForMode(darkMode);
    if (resizeRef.current) resizeRef.current();
    scheduleDraw();
  }, [darkMode, stageColors, stageFont, stageUnit, scheduleDraw]);

  const fitView = useCallback((animated, eased = false) => {
    const canvas = canvasRef.current;
    const zoomBehavior = zoomRef.current;
    if (!canvas || !zoomBehavior) return;

    const {width, height: h} = sizeRef.current;
    let transform = fitTransform(visibleRef.current.nodes, groupsRef.current, width, h,
        stageRef.current ? STAGE_FIT_PADDING : FIT_PADDING);
    if (!transform) return;

    if (eased && fittedRef.current) {
      const t = transformRef.current;
      transform = zoomIdentity
          .translate(t.x + (transform.x - t.x) * FOLLOW_EASING, t.y + (transform.y - t.y) * FOLLOW_EASING)
          .scale(t.k + (transform.k - t.k) * FOLLOW_EASING);
    }
    fittedRef.current = true;

    if (animated) {
      select(canvas).transition().duration(400).call(zoomBehavior.transform, transform);
    } else {
      select(canvas).call(zoomBehavior.transform, transform);
    }
  }, []);

  const sync = useCallback((rebuild, reheat, fit) => {
    const simulation = simulationRef.current;
    if (!simulation) return;

    const m = modelRef.current;
    const options = optionsRef.current;
    const {width, height: h} = sizeRef.current;
    const cx = width / 2;
    const cy = h / 2;

    if (rebuild) {
      nodesRef.current = new Map();
      departedPositionsRef.current = new Map();
      fittedRef.current = false;
    }
    const known = nodesRef.current;
    const departed = departedPositionsRef.current;

    const threshold = Number(options.minBytes) || 0;
    const pairs = m.pairs.filter((p) => p.full_bytes >= threshold);
    const connected = new Set();
    pairs.forEach((p) => {
      connected.add(p.a);
      connected.add(p.b);
    });

    const neighborOf = new Map();
    if (known.size > 0) {
      pairs.forEach((p) => {
        if (!known.has(p.a) && known.has(p.b)) neighborOf.set(p.a, known.get(p.b));
        if (!known.has(p.b) && known.has(p.a)) neighborOf.set(p.b, known.get(p.a));
      });
    }

    const nodes = [];
    m.nodes.forEach((n) => {
      // A node without a visible link stays if it has traffic of its own, like a network talking to itself.
      if (!connected.has(n.id) && !(n.self_bytes > 0 || (pairs.length === 0 && n.full_bytes > 0))) return;

      let node = known.get(n.id);
      if (!node) {
        const angle = Math.random() * 2 * Math.PI;
        const neighbor = neighborOf.get(n.id);
        if (departed.has(n.id)) {
          node = {id: n.id, ...departed.get(n.id)};
          departed.delete(n.id);
        } else if (neighbor) {
          node = {id: n.id, x: neighbor.x + Math.cos(angle) * 50, y: neighbor.y + Math.sin(angle) * 50};
        } else {
          const distance = n.type === "INTERNAL" ? 20 + Math.random() * 100 : 180 + Math.random() * 120;
          node = {id: n.id, x: cx + Math.cos(angle) * distance, y: cy + Math.sin(angle) * distance};
        }
        known.set(n.id, node);
      }

      node.type = n.type;
      node.level = n.level;
      node.data = n.data;
      node.label = n.label;
      node.name = n.name;
      node.detail = n.detail;
      node.sessions = n.sessions;
      node.bytes = n.bytes;
      node.bytesSent = n.bytes_sent;
      node.bytesReceived = n.bytes_received;
      node.selfBytes = n.self_bytes;
      node.fullBytes = n.full_bytes;
      node.fullSessions = n.full_sessions;
      node.fullPeers = n.full_peers;
      node.peers = n.peers;
      node.active = n.active;
      nodes.push(node);
    });

    const ids = new Set(nodes.map((n) => n.id));
    Array.from(known.keys()).forEach((id) => {
      if (ids.has(id)) return;
      departed.set(id, {x: known.get(id).x, y: known.get(id).y});
      known.delete(id);
    });
    while (departed.size > MAX_REMEMBERED_POSITIONS) {
      departed.delete(departed.keys().next().value);
    }

    const sizeBy = options.sizeBy === "peers" || options.sizeBy === "sessions" ? options.sizeBy : "bytes";
    const metric = (n) => (sizeBy === "peers" ? n.peers : (sizeBy === "sessions" ? n.sessions : n.bytes));
    const fullMetric = (n) => (sizeBy === "peers" ? n.fullPeers : (sizeBy === "sessions" ? n.fullSessions : n.fullBytes));

    let largest = scaleRef.current ? scaleRef.current[sizeBy] || 0 : 0;
    if (largest <= 0) nodes.forEach((n) => { largest = Math.max(largest, metric(n)); });
    let largestFull = 0;
    nodes.forEach((n) => { largestFull = Math.max(largestFull, fullMetric(n)); });

    const still = reducedMotion();
    let resized = false;
    nodes.forEach((n) => {
      n.layoutRadius = radiusOf(fullMetric(n), largestFull);
      n.targetRadius = radiusOf(metric(n), largest);
      if (n.radius === undefined || still) {
        n.radius = n.targetRadius;
      } else if (n.radius !== n.targetRadius) {
        resized = true;
      }
    });
    if (resized) easeSizes();

    nodes.slice().sort((a, b) => b.bytes - a.bytes).forEach((n, i) => { n.rank = i; });

    const links = pairs.map((p) => ({
      id: p.id, source: known.get(p.a), target: known.get(p.b), pair: p, cls: p.cls, bytes: p.bytes,
      sessions: p.sessions, tcp: p.tcp, udp: p.udp, active: p.active,
      toTarget: Boolean(p.forward && (p.forward.active || !p.active)),
      toSource: Boolean(p.backward && (p.backward.active || !p.active)),
      forward: p.forward, backward: p.backward
    }));

    nodes.forEach((n) => { n.degree = 0; });
    links.forEach((l) => {
      l.source.degree++;
      l.target.degree++;
    });

    const gentle = reheat === null;
    if (gentle) {
      const before = linksByIdRef.current;
      const changed = nodes.length !== visibleRef.current.nodes.length || links.length !== before.size
          || links.some((l) => !before.has(l.id));
      reheat = changed ? LIVE_REHEAT : 0;
    }

    visibleRef.current = {nodes: nodes, links: links};
    linksByIdRef.current = new Map(links.map((l) => [l.id, l]));

    if (hoveredRef.current && !ids.has(hoveredRef.current.id)) hoveredRef.current = null;
    // Links are made anew every time, so the one under the pointer is found again by its ID.
    hoveredLinkRef.current = hoveredLinkRef.current ? linksByIdRef.current.get(hoveredLinkRef.current.id) || null : null;
    if (spotlightRef.current !== hoveredRef.current) spotlightRef.current = null;

    if (rebuild || reheat > 0) {
      groupsRef.current = layoutAnchors(options.arrangement, nodes, cx, cy);
      simulation.force("ring").x(cx).y(cy);
    }

    // Setting the nodes makes every force read its accessors again, which is how new anchors take effect.
    simulation.nodes(nodes);
    simulation.force("link").links(links);

    if (fit) fitPendingRef.current = true;

    if (reheat > 0 && !frozenRef.current) {
      simulation.alpha(gentle ? Math.max(simulation.alpha(), reheat) : reheat).restart();
    } else {
      if (fit) fitView(false);
      scheduleDraw();
    }
  }, [scheduleDraw, fitView, easeSizes]);

  const nodeAtScreen = useCallback((px, py) => {
    const simulation = simulationRef.current;
    if (!simulation) return null;

    const t = transformRef.current;
    const wx = t.invertX(px);
    const wy = t.invertY(py);
    const node = simulation.find(wx, wy, (MAX_RADIUS + 6) / Math.min(t.k, 1));
    if (!node) return null;

    const dx = node.x - wx;
    const dy = node.y - wy;
    const hit = nodeRadius(node) + 4 / t.k;
    return dx * dx + dy * dy <= hit * hit ? node : null;
  }, []);

  const linkAtScreen = useCallback((px, py) => {
    const t = transformRef.current;
    const wx = t.invertX(px);
    const wy = t.invertY(py);
    let best = null;
    let bestDistance = Infinity;

    visibleRef.current.links.forEach((link) => {
      const s = link.source;
      const d = link.target;

      const dx = d.x - s.x;
      const dy = d.y - s.y;
      const lengthSquared = dx * dx + dy * dy;
      const along = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((wx - s.x) * dx + (wy - s.y) * dy) / lengthSquared));
      const distance = Math.hypot(wx - (s.x + along * dx), wy - (s.y + along * dy));
      const threshold = (linkWidth(link.bytes) / 2 + 4) / t.k;

      if (distance <= threshold && distance < bestDistance) {
        best = link;
        bestDistance = distance;
      }
    });

    return best;
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const options = () => optionsRef.current;

    const simulation = forceSimulation()
        .force("link", forceLink().id((d) => d.id)
            .distance((l) => (options().arrangement === "force"
                ? linkDistance(l) : 58 + layoutRadius(l.source) + layoutRadius(l.target)))
            .strength((l) => (options().arrangement === "force" ? linkStrength(l) : 0.04)))
        .force("charge", forceManyBody()
            .strength((d) => chargeStrength(options().arrangement, d))
            .distanceMax(600))
        .force("x", forceX().x((d) => (d.anchor ? d.anchor.x : 0)).strength((d) => d.anchorStrength || 0))
        .force("y", forceY().y((d) => (d.anchor ? d.anchor.y : 0))
            .strength((d) => (d.anchorStrength || 0) * (d.anchorSquash || 1)))
        .force("ring", forceRadial().radius((d) => d.ringRadius || 0).strength((d) => d.ringStrength || 0))
        .force("collide", forceCollide().radius((d) => layoutRadius(d) + 5))
        .on("tick", () => {
          if (fitPendingRef.current) fitView(false, liveRef.current);
          scheduleDraw();
        })
        .on("end", () => {
          // A live graph is never done: it keeps following its nodes until the view is moved by hand.
          if (!liveRef.current) fitPendingRef.current = false;
        });
    simulationRef.current = simulation;

    const resize = () => {
      const unit = stageRef.current ? stageRef.current.unit : 1;
      const cssWidth = container.clientWidth;
      const cssHeight = stageRef.current ? container.clientHeight : height;
      const dpr = window.devicePixelRatio || 1;
      const pixelWidth = Math.round(cssWidth * dpr);
      const pixelHeight = Math.round(cssHeight * dpr);
      const size = {width: cssWidth / unit, height: cssHeight / unit};
      if (canvas.width === pixelWidth && canvas.height === pixelHeight
          && sizeRef.current.width === size.width && sizeRef.current.height === size.height) {
        return;
      }
      sizeRef.current = size;
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
      canvas.style.width = cssWidth + "px";
      canvas.style.height = cssHeight + "px";
      // Anchors are absolute, so a new center means a new layout.
      if (visibleRef.current.nodes.length > 0) sync(false, 0.2, true); else scheduleDraw();
    }

    // Applied in the next frame: a resize inside the observer callback is reported as a ResizeObserver loop.
    let resizeFrame = null;
    const onResize = () => {
      if (resizeFrame !== null) return;
      resizeFrame = window.requestAnimationFrame(() => {
        resizeFrame = null;
        resize();
      });
    }

    resize();
    resizeRef.current = resize;

    let observer = null;
    if (typeof window.ResizeObserver !== "undefined") {
      observer = new window.ResizeObserver(onResize);
      observer.observe(container);
    } else {
      window.addEventListener("resize", onResize);
    }

    const selection = select(canvas);

    const zoomBehavior = zoom()
        .scaleExtent([0.08, 8])
        .wheelDelta(wheelZoomDelta)
        .filter((event) => !event.button && (event.type === "wheel" ? wheelZooms(event) : !event.ctrlKey))
        .on("zoom", (event) => {
          // Programmatic transforms have no source event.
          if (event.sourceEvent) fitPendingRef.current = false;
          transformRef.current = event.transform;
          scheduleDraw();
        });
    zoomRef.current = zoomBehavior;

    const dragBehavior = drag()
        .container(canvas)
        .subject((event) => {
          const node = nodeAtScreen(event.x, event.y);
          if (!node) return null;
          const t = transformRef.current;
          return {node: node, x: t.applyX(node.x), y: t.applyY(node.y)};
        })
        .on("start", (event) => {
          const node = event.subject.node;
          fitPendingRef.current = false;
          if (!frozenRef.current && !event.active) simulation.alphaTarget(0.3).restart();
          node.fx = node.x;
          node.fy = node.y;
        })
        .on("drag", (event) => {
          const node = event.subject.node;
          const t = transformRef.current;
          node.fx = t.invertX(event.x);
          node.fy = t.invertY(event.y);
          if (frozenRef.current) {
            node.x = node.fx;
            node.y = node.fy;
            scheduleDraw();
          }
        })
        .on("end", (event) => {
          const node = event.subject.node;
          if (!frozenRef.current && !event.active) simulation.alphaTarget(0);
          node.fx = null;
          node.fy = null;
        });

    // Drag is registered first so a drag that starts on a node stops the zoom's mousedown.
    selection.call(dragBehavior).call(zoomBehavior).on("dblclick.zoom", null);

    const pointerPosition = (event) => {
      const rect = canvas.getBoundingClientRect();
      const unit = stageRef.current ? stageRef.current.unit : 1;
      return [(event.clientX - rect.left) / unit, (event.clientY - rect.top) / unit];
    }

    const spotlight = (node) => {
      if (spotlightTimerRef.current !== null) {
        window.clearTimeout(spotlightTimerRef.current);
        spotlightTimerRef.current = null;
      }
      if (spotlightRef.current === node) return;

      spotlightRef.current = null;
      if (node) {
        spotlightTimerRef.current = window.setTimeout(() => {
          spotlightTimerRef.current = null;
          if (hoveredRef.current === node) {
            spotlightRef.current = node;
            scheduleDraw();
          }
        }, SPOTLIGHT_DELAY);
      }
    }

    const onMouseMove = (event) => {
      const [px, py] = pointerPosition(event);
      const node = nodeAtScreen(px, py);
      const link = node ? null : linkAtScreen(px, py);
      if (node !== hoveredRef.current || link !== hoveredLinkRef.current) {
        hoveredRef.current = node;
        hoveredLinkRef.current = link;
        spotlight(node);
        canvas.style.cursor = node || link ? "pointer" : "grab";
        scheduleDraw();
      }

      if (!node && !link) {
        setTooltip(null);
        return;
      }

      const raised = px > sizeRef.current.width - DETAILS_WIDTH - 40 && py > sizeRef.current.height - 200;
      setTooltip((current) => (current && current.node === node && current.link === link && current.raised === raised
          ? current : {node: node, link: link, raised: raised}));
    }

    const onMouseLeave = () => {
      if (hoveredRef.current || hoveredLinkRef.current) {
        hoveredRef.current = null;
        hoveredLinkRef.current = null;
        spotlight(null);
        scheduleDraw();
      }
      setTooltip(null);
    }

    const onClick = (event) => {
      const [px, py] = pointerPosition(event);
      const node = nodeAtScreen(px, py);
      const link = node ? null : linkAtScreen(px, py);

      const notify = callbacksRef.current.onSelect;
      if (!notify) return;
      if (node) {
        notify({kind: "node", id: node.id});
      } else if (link) {
        notify({kind: "link", id: link.id, a: link.source.id, b: link.target.id});
      } else {
        notify(null);
      }
    }

    const onDoubleClick = (event) => {
      const [px, py] = pointerPosition(event);
      const node = nodeAtScreen(px, py);
      if (node && callbacksRef.current.onFilter && nodeFilter(node.data)) callbacksRef.current.onFilter(node.data);
    }

    canvas.style.cursor = "grab";
    canvas.addEventListener("mousemove", onMouseMove);
    canvas.addEventListener("mouseleave", onMouseLeave);
    canvas.addEventListener("click", onClick);
    canvas.addEventListener("dblclick", onDoubleClick);

    // A simulation that replaces an earlier one, after a resize of the card for example, takes over its data.
    if (dataKeyRef.current !== null) {
      sync(true, 1, true);
    }

    return () => {
      simulation.stop();
      simulation.on("tick", null);
      simulationRef.current = null;
      zoomRef.current = null;
      resizeRef.current = null;

      if (resizeFrame !== null) window.cancelAnimationFrame(resizeFrame);
      if (observer) observer.disconnect(); else window.removeEventListener("resize", onResize);

      selection.on(".zoom", null).on(".drag", null);
      canvas.removeEventListener("mousemove", onMouseMove);
      canvas.removeEventListener("mouseleave", onMouseLeave);
      canvas.removeEventListener("click", onClick);
      canvas.removeEventListener("dblclick", onDoubleClick);

      if (frameRef.current !== null) {
        window.cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
      if (easingRef.current !== null) {
        window.cancelAnimationFrame(easingRef.current);
        easingRef.current = null;
      }
      spotlight(null);
    }
  }, [hasData, height, scheduleDraw, nodeAtScreen, linkAtScreen, sync, fitView]);

  useEffect(() => {
    if (!hasData) return;

    const previous = optionsRef.current;
    const structural = previous.minBytes !== minBytes || previous.arrangement !== arrangement;
    optionsRef.current = {minBytes: minBytes, arrangement: arrangement, sizeBy: sizeBy, labels: labels};
    modelRef.current = model;
    scaleRef.current = scale;
    liveRef.current = live;

    if (dataKeyRef.current !== dataKey) {
      dataKeyRef.current = dataKey;
      hoveredRef.current = null;
      spotlightRef.current = null;
      sync(true, 1, true);
    } else if (structural) {
      sync(false, 0.6, true);
    } else {
      sync(false, live ? null : 0, false);
    }

    setTooltip((current) => {
      if (!current) return current;
      if (current.node) return nodesRef.current.has(current.node.id) ? current : null;
      const link = linksByIdRef.current.get(current.link.id);
      return link ? {...current, link: link} : null;
    });
  }, [model, scale, dataKey, minBytes, arrangement, sizeBy, labels, live, hasData, sync]);

  useEffect(() => {
    if (!pulses || pulses.length === 0 || reducedMotion()) return;

    const now = window.performance.now();
    pulses.forEach((p) => {
      pulsesRef.current.push({linkId: pairIdOf(p.source, p.target), forward: p.source < p.target, at: now + p.delay});
    });
    scheduleDraw();
  }, [pulses, scheduleDraw]);

  useEffect(() => {
    selectedRef.current = selectedId;
    scheduleDraw();
  }, [selectedId, scheduleDraw]);

  useEffect(() => {
    if (!focus || focus.at === handledFocusRef.current) return;
    handledFocusRef.current = focus.at;

    const node = visibleRef.current.nodes.find((n) => n.id === focus.id);
    if (!node) return;

    fitPendingRef.current = false;

    const canvas = canvasRef.current;
    if (!canvas || !zoomRef.current) return;
    const k = Math.max(transformRef.current.k, 0.9);
    const {width, height: h} = sizeRef.current;
    select(canvas).transition().duration(400)
        .call(zoomRef.current.transform, zoomIdentity.translate(width / 2 - node.x * k, h / 2 - node.y * k).scale(k));
  }, [focus]);

  const onResetView = () => {
    fitView(true);
    if (live) fitPendingRef.current = true;
  }

  const onToggleFreeze = () => {
    const next = !frozenRef.current;
    frozenRef.current = next;
    setFrozen(next);

    const simulation = simulationRef.current;
    if (!simulation) return;
    if (next) {
      simulation.stop();
    } else {
      simulation.alpha(0.3).restart();
    }
  }

  if (stage) {
    return (
        <div ref={containerRef} style={{position: "relative", width: "100%", height: "100%", overflow: "hidden"}}>
          <canvas ref={canvasRef} style={{position: "absolute", top: 0, left: 0, display: "block", pointerEvents: "none"}} />
        </div>
    )
  }

  if (!hasData) {
    return (
        <div className="border rounded d-flex align-items-center justify-content-center text-muted" style={{height: height + "px"}}>
          No connections match the selected filters.
        </div>
    )
  }

  const tooltipBody = () => {
    if (tooltip.node) {
      const n = tooltip.node;
      return (
          <React.Fragment>
            <div style={{fontWeight: "bold", wordBreak: "break-all"}}>
              {n.label}
              {n.name ? <span className="context-name context-name-asset ms-1">{n.name}</span> : null}
            </div>
            <div style={{color: colors.muted}}>{nodeKind(n.data)}{n.detail ? ", " + n.detail : ""}</div>
            {n.active
                ? <React.Fragment>
                    <div>Sessions: {formatCount(n.sessions)}</div>
                    <div>Sent: {formatBytes(n.bytesSent)}, received: {formatBytes(n.bytesReceived)}</div>
                    <div>Connected nodes: {formatCount(n.peers)}</div>
                  </React.Fragment>
                : <div>No traffic in the time window.</div>}
            {nodeFilter(n.data) ? <div style={{color: colors.muted}}>Double-click to add a filter for this node.</div> : null}
          </React.Fragment>
      );
    }

    const l = tooltip.link;
    return (
        <React.Fragment>
          <div style={{fontWeight: "bold", wordBreak: "break-all"}}>
            {truncate(l.source.label, 26)} and {truncate(l.target.label, 26)}
          </div>
          {l.active
              ? pairDirectionText(l, l.source, l.target).map((line, i) => <div key={i}>{line}</div>)
              : <div>No traffic in the time window.</div>}
        </React.Fragment>
    );
  }

  return (
      <div>
        <div ref={containerRef} className="border rounded" style={{position: "relative", width: "100%", height: height + "px", overflow: "hidden"}}>
          {/* Out of the flow, so the canvas follows the width of the card and never holds it open. */}
          <canvas ref={canvasRef}
                  style={{position: "absolute", top: 0, left: 0, display: "block", borderRadius: "4px"}}
                  onWheel={(e) => { if (!wheelZooms(e)) showZoomHint(); }} />

          <div style={{position: "absolute", top: "8px", right: "8px", zIndex: 5}}>
            <button className="btn btn-sm btn-outline-secondary me-2" type="button" onClick={onResetView}>
              Reset view
            </button>
            <button className={"btn btn-sm " + (frozen ? "btn-secondary" : "btn-outline-secondary")}
                    type="button"
                    onClick={onToggleFreeze}>
              {frozen ? "Unfreeze layout" : "Freeze layout"}
            </button>
          </div>

          <ZoomHint visible={zoomHint} dragText="pan" />

          {tooltip ? (
              <div style={{
                ...tooltipStyle(colors),
                maxWidth: "none",
                width: DETAILS_WIDTH + "px",
                right: "8px",
                ...(tooltip.raised ? {top: "48px"} : {bottom: "36px"})
              }}>
                {tooltipBody()}
              </div>
          ) : null}
        </div>
      </div>
  )

}
