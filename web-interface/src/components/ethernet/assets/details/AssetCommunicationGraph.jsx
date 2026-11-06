import {externalPeerLabel} from "./AssetProfileHelpers";
import React, {useCallback, useEffect, useRef, useState} from "react";
import {
  drag,
  forceCollide,
  forceLink,
  forceManyBody,
  forceSimulation,
  forceX,
  forceY,
  select,
  zoom,
  zoomIdentity
} from "d3";
import moment from "moment";
import Store from "../../../../util/Store";
import ZoomHint, {useZoomHint, wheelZooms} from "../../../shared/ZoomHint";
import numeral from "numeral";

/*
 * Graph data lives in mutable refs because d3-force mutates node and link objects in place. React state is only
 * used for the chrome around the canvas.
 */

const ROOT_RADIUS = 14;
const PEER_MIN_RADIUS = 5;
const PEER_MAX_RADIUS = 14;
const MIN_LINK_WIDTH = 1;
const MAX_LINK_WIDTH = 6;
const FONT_FAMILY = "'Nunito Sans', sans-serif";

const formatBytes = (bytes) => numeral(Number(bytes) || 0).format("0.0b");
const formatCount = (count) => numeral(Number(count) || 0).format("0,0");

const formatDate = (iso) => iso ? moment(iso).format("YYYY-MM-DD HH:mm:ss") : "n/a";

const truncate = (s, max) => (s && s.length > max ? s.substring(0, max - 1) + "…" : s);

// Green and red are avoided on purpose: they mean healthy and alerting elsewhere in the UI.
const colorsForMode = (darkMode) => {
  if (darkMode) {
    return {
      background: "#1c1c22",
      grid: "#2e2e38",
      text: "#dbe3f0",
      muted: "#8b95a7",
      lines: "#2a3447",
      root: "#22d3ee",
      internal: "#e2e8f0",
      external: "#fb923c",
      outbound: "#38bdf8",
      inbound: "#fb7185",
      both: "#facc15",
      selection: "#ffffff",
      glow: 14
    }
  }

  return {
    background: "#f9f9f9",
    grid: "#dcdcdc",
    text: "#0f172a",
    muted: "#64748b",
    lines: "#cbd5e1",
    root: "#0284c7",
    internal: "#475569",
    external: "#ea580c",
    outbound: "#0ea5e9",
    inbound: "#e11d48",
    both: "#ca8a04",
    selection: "#0f172a",
    glow: 8
  }
}

const GRID_SPACING = 24;

// Direction sectors are fixed so the layout reads the same on every asset. Only peers of the root are grouped.
const MAX_GROUPS = 12;
const GROUP_LABEL_OFFSET = 26;
const OTHER_GROUP = "__other__";
const FIT_PADDING = 36;
const FIT_MAX_SCALE = 1.2;

const groupLabelPosition = (group) => {
  let extent = 0;
  group.members.forEach((n) => {
    const along = (n.x - group.cx) * group.ux + (n.y - group.cy) * group.uy;
    extent = Math.max(extent, along + nodeRadius(n));
  });
  const distance = extent + GROUP_LABEL_OFFSET;
  return {x: group.cx + group.ux * distance, y: group.cy + group.uy * distance};
}

const fitTransform = (nodes, groups, width, height) => {
  if (nodes.length === 0 || width <= 0 || height <= 0) return null;

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  const include = (x, y, margin) => {
    minX = Math.min(minX, x - margin);
    maxX = Math.max(maxX, x + margin);
    minY = Math.min(minY, y - margin);
    maxY = Math.max(maxY, y + margin);
  }
  nodes.forEach((n) => include(n.x, n.y, nodeRadius(n) + 6));
  groups.forEach((g) => {
    const l = groupLabelPosition(g);
    include(l.x, l.y, 60);
  });

  const k = Math.min(FIT_MAX_SCALE, (width - FIT_PADDING * 2) / (maxX - minX), (height - FIT_PADDING * 2) / (maxY - minY));
  return zoomIdentity
    .translate(width / 2 - k * (minX + maxX) / 2, height / 2 - k * (minY + maxY) / 2)
    .scale(k);
}

const DIRECTION_SECTORS = {
  "INTERNAL|INBOUND": {label: "Internal peers connecting in", angle: -0.75 * Math.PI},
  "INTERNAL|BOTH": {label: "Internal, both directions", angle: Math.PI},
  "INTERNAL|OUTBOUND": {label: "Internal services used", angle: 0.75 * Math.PI},
  "EXTERNAL|INBOUND": {label: "External networks connecting in", angle: -0.25 * Math.PI},
  "EXTERNAL|BOTH": {label: "External, both directions", angle: 0},
  "EXTERNAL|OUTBOUND": {label: "External services used", angle: 0.25 * Math.PI}
};

const linkDirection = (link) => (link.outbound && link.inbound ? "BOTH" : (link.outbound ? "OUTBOUND" : "INBOUND"));

const serviceOf = (link) => {
  let best = null;
  link.edges.forEach((edge) => {
    const bytes = (edge.bytes_rx || 0) + (edge.bytes_tx || 0);
    if (!best || bytes > best.bytes) best = {edge: edge, bytes: bytes};
  });
  if (!best) return null;

  const edge = best.edge;
  const port = edge.port === 0 ? edge.l4_type + " other ports" : edge.l4_type + "/" + edge.port;
  const name = edge.protocol ? port + " " + edge.protocol : port;
  return (edge.direction === "OUTBOUND" ? "Outbound " : "Inbound ") + name;
}

const groupKeyFor = (grouping, node, link) => {
  switch (grouping) {
    case "direction":
      return {key: node.type + "|" + linkDirection(link), label: DIRECTION_SECTORS[node.type + "|" + linkDirection(link)].label};
    case "country":
      if (node.type === "INTERNAL") return {key: "internal", label: "Internal network"};
      return node.country_code
        ? {key: "cc:" + node.country_code, label: node.country_code}
        : {key: "cc:none", label: "Unknown country"};
    case "service": {
      const service = serviceOf(link);
      return service ? {key: "svc:" + service, label: service} : {key: "svc:none", label: "Unknown service"};
    }
    default:
      return null;
  }
}

const layoutGroups = (grouping, graph, nodes, links, cx, cy) => {
  nodes.forEach((n) => {
    n.anchor = n.type === "ROOT" ? {x: cx, y: cy} : null;
    n.anchorDistance = 0;
  });

  if (!grouping || grouping === "none") return [];

  const groups = new Map();
  links.forEach((link) => {
    const peer = link.sourceId === graph.rootId ? link.target : (link.targetId === graph.rootId ? link.source : null);
    if (!peer || typeof peer !== "object") return;

    const g = groupKeyFor(grouping, peer, link);
    if (!g) return;
    if (!groups.has(g.key)) groups.set(g.key, {key: g.key, label: g.label, members: [], bytes: 0});
    const group = groups.get(g.key);
    group.members.push(peer);
    group.bytes += link.bytes;
  });

  let ordered = Array.from(groups.values()).sort((a, b) => b.bytes - a.bytes);

  if (grouping !== "direction" && ordered.length > MAX_GROUPS) {
    const other = {key: OTHER_GROUP, label: "Other", members: [], bytes: 0};
    ordered.slice(MAX_GROUPS - 1).forEach((g) => {
      other.members.push(...g.members);
      other.bytes += g.bytes;
    });
    ordered = ordered.slice(0, MAX_GROUPS - 1).concat([other]);
  }

  const ring = Math.max(190, ordered.length * 85 / (2 * Math.PI));

  ordered.forEach((group, i) => {
    const angle = grouping === "direction"
      ? DIRECTION_SECTORS[group.key].angle
      : -Math.PI / 2 + i * 2 * Math.PI / ordered.length;
    const distance = ring + 8 * Math.sqrt(group.members.length);

    group.x = cx + Math.cos(angle) * distance;
    group.y = cy + Math.sin(angle) * distance;
    group.cx = cx;
    group.cy = cy;
    group.ux = Math.cos(angle);
    group.uy = Math.sin(angle);

    group.members.forEach((n) => {
      n.anchor = {x: group.x, y: group.y};
      n.anchorDistance = distance;
    });
  });

  return ordered;
}

const peerLabel = (edge) => {
  if (edge.peer_type === "INTERNAL") {
    return edge.peer_address || edge.peer_mac || edge.peer_key;
  }

  return externalPeerLabel(edge);
}

const linkIdFor = (a, b) => (a < b ? a + "|" + b : b + "|" + a);

const nodeRadius = (node) => (node.type === "ROOT" ? ROOT_RADIUS : (node.radius || PEER_MIN_RADIUS));

const linkWidth = (bytes) => {
  if (!bytes || bytes <= 0) return MIN_LINK_WIDTH;
  // log10 scale: 1 KB -> 1px, 1 TB -> 6px.
  const w = (Math.log10(bytes) - 3) * (MAX_LINK_WIDTH - MIN_LINK_WIDTH) / 9 + MIN_LINK_WIDTH;
  return Math.max(MIN_LINK_WIDTH, Math.min(MAX_LINK_WIDTH, w));
}

const createNode = (id, type, label, extra) => ({
  id: id,
  type: type,
  label: label,
  name: extra.name || (extra.asset && extra.asset.name) || null,
  mac: extra.mac || null,
  address: extra.address || null,
  asset: extra.asset || null,
  asn_number: extra.asn_number || null,
  asn_name: extra.asn_name || null,
  country_code: extra.country_code || null,
  expanded: false,
  loading: false
});

// Edges that point back at the root asset, possible after expanding a peer, are mapped onto the root node.
const peerNodeId = (graph, edge) => {
  if (edge.peer_type === "INTERNAL" && graph.rootMac) {
    const mac = edge.peer_mac || (edge.peer_asset ? edge.peer_asset.mac : null);
    if (mac && mac.toLowerCase() === graph.rootMac.toLowerCase()) return graph.rootId;
  }

  return edge.peer_type + ":" + edge.peer_key;
}

// Direction flags on a link are relative to link.source, so edges arriving from the other end are flipped.
const mergeEdges = (graph, ownerId, edges) => {
  const owner = graph.nodes.get(ownerId);
  if (!owner) return;

  edges.forEach((edge) => {
    const peerId = peerNodeId(graph, edge);
    if (peerId === ownerId) return;

    let peer = graph.nodes.get(peerId);
    if (!peer) {
      peer = createNode(peerId, edge.peer_type, peerLabel(edge), {
        mac: edge.peer_mac,
        address: edge.peer_address,
        asset: edge.peer_asset,
        asn_number: edge.peer_asn_number,
        asn_name: edge.peer_asn_name,
        country_code: edge.peer_country_code
      });
      const angle = Math.random() * 2 * Math.PI;
      peer.x = (owner.x || 0) + Math.cos(angle) * 40;
      peer.y = (owner.y || 0) + Math.sin(angle) * 40;
      graph.nodes.set(peerId, peer);
    }

    const linkId = linkIdFor(ownerId, peerId);
    let link = graph.links.get(linkId);
    if (!link) {
      link = {
        id: linkId,
        source: ownerId,
        target: peerId,
        sourceId: ownerId,
        targetId: peerId,
        edges: [],
        bytes: 0,
        sessions: 0,
        outbound: false,
        inbound: false,
        tcp: false,
        udp: false
      };
      graph.links.set(linkId, link);
    }

    link.edges.push(edge);
    link.bytes += (edge.bytes_rx || 0) + (edge.bytes_tx || 0);
    link.sessions += edge.sessions || 0;

    const fromSource = link.sourceId === ownerId;
    const outbound = edge.direction === "OUTBOUND";
    if (outbound === fromSource) link.outbound = true; else link.inbound = true;
    if (edge.l4_type === "UDP") link.udp = true; else link.tcp = true;
  });
}

// Edge bytes are recorded from the perspective of the asset that owns the edge, so on the peer side rx/tx are
// swapped.
const aggregateNode = (node, visibleLinks) => {
  const agg = {sessions: 0, bytesIn: 0, bytesOut: 0, firstSeen: null, lastSeen: null, edges: []};

  visibleLinks.forEach((link) => {
    if (link.sourceId !== node.id && link.targetId !== node.id) return;
    const owns = link.sourceId === node.id;

    link.edges.forEach((edge) => {
      agg.edges.push(edge);
      agg.sessions += edge.sessions || 0;
      agg.bytesIn += owns ? (edge.bytes_rx || 0) : (edge.bytes_tx || 0);
      agg.bytesOut += owns ? (edge.bytes_tx || 0) : (edge.bytes_rx || 0);
      if (edge.first_seen && (!agg.firstSeen || edge.first_seen < agg.firstSeen)) agg.firstSeen = edge.first_seen;
      if (edge.last_seen && (!agg.lastSeen || edge.last_seen > agg.lastSeen)) agg.lastSeen = edge.last_seen;
    });
  });

  return agg;
}

const publicNode = (node) => ({
  id: node.id,
  type: node.type,
  label: node.label,
  name: node.name,
  mac: node.mac,
  address: node.address,
  asset: node.asset,
  asn_number: node.asn_number,
  asn_name: node.asn_name,
  country_code: node.country_code
});

export default function AssetCommunicationGraph({rootMac, rootLabel, edges, minBytes = 0, grouping = "direction", onExpand, onSelect, focus = null, height = 520}) {

  const containerRef = useRef(null);
  const canvasRef = useRef(null);

  const graphRef = useRef({nodes: new Map(), links: new Map(), rootId: null, rootMac: null});
  const visibleRef = useRef({nodes: [], links: []});
  const simulationRef = useRef(null);
  const zoomRef = useRef(null);
  const transformRef = useRef(zoomIdentity);
  const sizeRef = useRef({width: 0, height: height});
  const frameRef = useRef(null);
  const drawRef = useRef(null);
  const hoveredRef = useRef(null);
  const hoveredLinkRef = useRef(null);
  const selectedRef = useRef(null);
  const frozenRef = useRef(false);
  const mountedRef = useRef(false);
  const minBytesRef = useRef(minBytes);
  const groupingRef = useRef(grouping);
  const groupsRef = useRef([]);
  const fitPendingRef = useRef(false);
  const colorsRef = useRef(colorsForMode(false));
  const callbacksRef = useRef({onExpand: null, onSelect: null});

  const [frozen, setFrozen] = useState(false);
  const [tooltip, setTooltip] = useState(null);
  const [zoomHint, showZoomHint] = useZoomHint();

  const hasEdges = Array.isArray(edges) && edges.length > 0;
  const darkMode = Boolean(Store.get("dark_mode"));
  const colors = colorsForMode(darkMode);

  const scheduleDraw = useCallback(() => {
    if (frameRef.current !== null) return;
    frameRef.current = window.requestAnimationFrame(() => {
      frameRef.current = null;
      if (drawRef.current) drawRef.current();
    });
  }, []);

  const draw = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    const dpr = window.devicePixelRatio || 1;
    const {width, height: h} = sizeRef.current;
    const t = transformRef.current;
    const c = colorsRef.current;
    const {nodes, links} = visibleRef.current;
    const hovered = hoveredRef.current;
    const hoveredLink = hoveredLinkRef.current;
    const selected = selectedRef.current;
    const isHighlighted = (node) => node === hovered || node === selected
      || (hoveredLink !== null && (hoveredLink.source === node || hoveredLink.target === node));

    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.fillStyle = c.background;
    ctx.fillRect(0, 0, width, h);

    ctx.fillStyle = c.grid;
    for (let gx = GRID_SPACING / 2; gx < width; gx += GRID_SPACING) {
      for (let gy = GRID_SPACING / 2; gy < h; gy += GRID_SPACING) {
        ctx.fillRect(gx, gy, 1.5, 1.5);
      }
    }

    ctx.translate(t.x, t.y);
    ctx.scale(t.k, t.k);

    ctx.globalAlpha = 0.8;
    links.forEach((link) => {
      const s = link.source;
      const d = link.target;
      if (typeof s !== "object" || typeof d !== "object") return;

      ctx.beginPath();
      ctx.moveTo(s.x, s.y);
      ctx.lineTo(d.x, d.y);
      ctx.lineWidth = linkWidth(link.bytes);
      ctx.setLineDash(link.udp && !link.tcp ? [4, 3] : []);
      ctx.strokeStyle = link.outbound && link.inbound ? c.both : (link.outbound ? c.outbound : c.inbound);
      ctx.stroke();
    });
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;

    if (hoveredLink && typeof hoveredLink.source === "object" && typeof hoveredLink.target === "object") {
      const color = hoveredLink.outbound && hoveredLink.inbound ? c.both : (hoveredLink.outbound ? c.outbound : c.inbound);
      ctx.beginPath();
      ctx.moveTo(hoveredLink.source.x, hoveredLink.source.y);
      ctx.lineTo(hoveredLink.target.x, hoveredLink.target.y);
      ctx.lineWidth = linkWidth(hoveredLink.bytes) + 2;
      ctx.setLineDash(hoveredLink.udp && !hoveredLink.tcp ? [4, 3] : []);
      ctx.strokeStyle = color;
      ctx.shadowColor = color;
      ctx.shadowBlur = c.glow * dpr;
      ctx.stroke();
      ctx.shadowBlur = 0;
      ctx.shadowColor = "transparent";
      ctx.setLineDash([]);
    }

    const now = Date.now();
    let anyLoading = false;
    nodes.forEach((node) => {
      const r = nodeRadius(node);
      const fill = node.type === "ROOT" ? c.root : (node.type === "INTERNAL" ? c.internal : c.external);

      if (node.type === "ROOT") {
        ctx.beginPath();
        ctx.arc(node.x, node.y, r + 6, 0, 2 * Math.PI);
        ctx.fillStyle = fill;
        ctx.globalAlpha = 0.22;
        ctx.fill();
        ctx.globalAlpha = 1;
      }

      ctx.beginPath();
      if (node.type === "EXTERNAL") {
        if (ctx.roundRect) ctx.roundRect(node.x - r, node.y - r, r * 2, r * 2, 3);
        else ctx.rect(node.x - r, node.y - r, r * 2, r * 2);
      } else {
        ctx.arc(node.x, node.y, r, 0, 2 * Math.PI);
      }
      ctx.fillStyle = fill;
      ctx.shadowColor = fill;
      // Shadows ignore the canvas transform, so the blur is in device pixels regardless of zoom.
      ctx.shadowBlur = c.glow * dpr;
      ctx.fill();
      ctx.shadowBlur = 0;
      ctx.shadowColor = "transparent";

      if (node.expanded) {
        ctx.lineWidth = 2;
        ctx.strokeStyle = c.background;
        ctx.stroke();
      }

      if (isHighlighted(node)) {
        ctx.beginPath();
        ctx.arc(node.x, node.y, r + 3, 0, 2 * Math.PI);
        ctx.lineWidth = node === selected ? 2 : 1;
        ctx.strokeStyle = c.selection;
        ctx.stroke();
      }

      if (node.loading) {
        anyLoading = true;
        const start = (now / 300) % (2 * Math.PI);
        ctx.beginPath();
        ctx.arc(node.x, node.y, r + 5, start, start + Math.PI * 1.3);
        ctx.lineWidth = 2;
        ctx.strokeStyle = c.text;
        ctx.stroke();
      }
    });

    const fontSize = 11 / t.k;
    const lineHeight = 14 / t.k;
    const padding = 4 / t.k;

    ctx.font = "bold " + fontSize + "px " + FONT_FAMILY;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    groupsRef.current.forEach((group) => {
      const text = group.label + " · " + formatCount(group.members.length);
      const l = groupLabelPosition(group);
      const plateWidth = ctx.measureText(text).width + padding * 3;
      const plateHeight = lineHeight + padding;

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

    ctx.textBaseline = "top";
    nodes.forEach((node) => {
      if (!isHighlighted(node)) return;

      const lines = [truncate(node.label, 28)];
      if (node.name) lines.push(truncate(node.name, 28));

      const top = node.y + nodeRadius(node) + 4 / t.k;
      const textWidth = Math.max(...lines.map((line) => ctx.measureText(line).width));
      const plateWidth = textWidth + padding * 2;
      const plateHeight = lines.length * lineHeight + padding * 2 - (lineHeight - fontSize);

      ctx.globalAlpha = 0.82;
      ctx.fillStyle = c.background;
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(node.x - plateWidth / 2, top, plateWidth, plateHeight, 3 / t.k);
      else ctx.rect(node.x - plateWidth / 2, top, plateWidth, plateHeight);
      ctx.fill();
      ctx.globalAlpha = 1;

      lines.forEach((line, i) => {
        ctx.fillStyle = i === 0 ? c.text : c.muted;
        ctx.fillText(line, node.x, top + padding + i * lineHeight);
      });
    });

    ctx.restore();

    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.font = "11px " + FONT_FAMILY;
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";
    const legend = [
      ["Outbound", c.outbound, false],
      ["Inbound", c.inbound, false],
      ["Both directions", c.both, false],
      ["UDP dashed, TCP solid", c.muted, true]
    ];
    legend.forEach((entry, i) => {
      const y = h - 12 - (legend.length - 1 - i) * 16;
      ctx.beginPath();
      ctx.moveTo(10, y);
      ctx.lineTo(34, y);
      ctx.lineWidth = 2;
      ctx.setLineDash(entry[2] ? [4, 3] : []);
      ctx.strokeStyle = entry[1];
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = c.muted;
      ctx.fillText(entry[0], 40, y);
    });
    ctx.restore();

    if (anyLoading) scheduleDraw();
  }

  useEffect(() => {
    drawRef.current = draw;
    callbacksRef.current = {onExpand: onExpand, onSelect: onSelect};
  });

  useEffect(() => {
    colorsRef.current = colorsForMode(darkMode);
    scheduleDraw();
  }, [darkMode, scheduleDraw]);

  const fitView = useCallback((animated) => {
    const canvas = canvasRef.current;
    const zoomBehavior = zoomRef.current;
    if (!canvas || !zoomBehavior) return;

    const {width, height: h} = sizeRef.current;
    const transform = fitTransform(visibleRef.current.nodes, groupsRef.current, width, h);
    if (!transform) return;

    if (animated) {
      select(canvas).transition().duration(400).call(zoomBehavior.transform, transform);
    } else {
      select(canvas).call(zoomBehavior.transform, transform);
    }
  }, []);

  // `reheat` is the alpha to restart the simulation with, or 0 to only redraw. `fit` keeps the layout in view
  // while it settles, until the user zooms or pans.
  const applyVisibility = useCallback((reheat, fit = false) => {
    const graph = graphRef.current;
    const simulation = simulationRef.current;
    if (!simulation) return;

    const threshold = Number(minBytesRef.current) || 0;
    const links = Array.from(graph.links.values()).filter((l) => l.bytes >= threshold);
    const connected = new Set();
    links.forEach((l) => {
      connected.add(l.sourceId);
      connected.add(l.targetId);
    });

    const nodes = Array.from(graph.nodes.values()).filter((n) => n.id === graph.rootId || connected.has(n.id));
    const nodeIds = new Set(nodes.map((n) => n.id));
    const visibleLinks = links.filter((l) => nodeIds.has(l.sourceId) && nodeIds.has(l.targetId));

    const bytesByNode = new Map();
    visibleLinks.forEach((l) => {
      bytesByNode.set(l.sourceId, (bytesByNode.get(l.sourceId) || 0) + l.bytes);
      bytesByNode.set(l.targetId, (bytesByNode.get(l.targetId) || 0) + l.bytes);
    });
    let maxPeerBytes = 0;
    nodes.forEach((n) => {
      if (n.id !== graph.rootId) maxPeerBytes = Math.max(maxPeerBytes, bytesByNode.get(n.id) || 0);
    });
    nodes.forEach((n) => {
      const share = maxPeerBytes > 0 ? Math.sqrt((bytesByNode.get(n.id) || 0) / maxPeerBytes) : 0;
      n.radius = PEER_MIN_RADIUS + (PEER_MAX_RADIUS - PEER_MIN_RADIUS) * share;
    });

    visibleRef.current = {nodes: nodes, links: visibleLinks};

    // Links hold node ids until the link force resolves them, group layout needs the objects.
    const byId = new Map(nodes.map((n) => [n.id, n]));
    visibleLinks.forEach((l) => {
      if (typeof l.source !== "object") l.source = byId.get(l.sourceId);
      if (typeof l.target !== "object") l.target = byId.get(l.targetId);
    });

    const {width, height: h} = sizeRef.current;
    groupsRef.current = layoutGroups(groupingRef.current, graph, nodes, visibleLinks, width / 2, h / 2);

    simulation.nodes(nodes);
    simulation.force("link").links(visibleLinks);

    if (hoveredRef.current && !nodeIds.has(hoveredRef.current.id)) hoveredRef.current = null;
    hoveredLinkRef.current = null;
    if (selectedRef.current && !nodeIds.has(selectedRef.current.id)) selectedRef.current = null;

    if (fit) fitPendingRef.current = true;

    if (reheat > 0 && !frozenRef.current) {
      simulation.alpha(reheat).restart();
    } else {
      if (fit) fitView(false);
      scheduleDraw();
    }
  }, [scheduleDraw, fitView]);

  const nodeAtScreen = useCallback((px, py) => {
    const simulation = simulationRef.current;
    if (!simulation) return null;

    const t = transformRef.current;
    const wx = t.invertX(px);
    const wy = t.invertY(py);
    const node = simulation.find(wx, wy, (ROOT_RADIUS + 6) / Math.min(t.k, 1));
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
      if (typeof s !== "object" || typeof d !== "object") return;

      const dx = d.x - s.x;
      const dy = d.y - s.y;
      const lengthSquared = dx * dx + dy * dy;
      const along = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((wx - s.x) * dx + (wy - s.y) * dy) / lengthSquared));
      const cx = s.x + along * dx;
      const cy = s.y + along * dy;
      const distance = Math.hypot(wx - cx, wy - cy);
      const threshold = (linkWidth(link.bytes) / 2 + 4) / t.k;

      if (distance <= threshold && distance < bestDistance) {
        best = link;
        bestDistance = distance;
      }
    });

    return best;
  }, []);

  const expandNode = useCallback(async (node) => {
    const expand = callbacksRef.current.onExpand;
    if (!expand || node.type !== "INTERNAL" || !node.asset || node.expanded || node.loading) return;

    node.loading = true;
    scheduleDraw();

    let result = null;
    try {
      result = await expand(node.asset);
    } catch {
      result = null;
    }

    if (!mountedRef.current) return;
    node.loading = false;

    if (Array.isArray(result)) {
      node.expanded = true;
      mergeEdges(graphRef.current, node.id, result);
      applyVisibility(0.5);
    } else {
      scheduleDraw();
    }
  }, [applyVisibility, scheduleDraw]);

  useEffect(() => {
    mountedRef.current = true;

    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const grouped = () => groupingRef.current && groupingRef.current !== "none";

    const simulation = forceSimulation()
      .force("link", forceLink().id((d) => d.id).distance((l) => {
        const anchored = l.source.anchorDistance || l.target.anchorDistance;
        return anchored ? anchored : 50 + nodeRadius(l.source) + nodeRadius(l.target);
      }))
      .force("charge", forceManyBody().strength((d) => (d.type === "ROOT" ? -400 : (grouped() ? -45 : -120))))
      .force("x", forceX().x((d) => (d.anchor ? d.anchor.x : 0)).strength((d) => (d.type === "ROOT" ? 0.9 : (d.anchor ? 0.14 : 0))))
      .force("y", forceY().y((d) => (d.anchor ? d.anchor.y : 0)).strength((d) => (d.type === "ROOT" ? 0.9 : (d.anchor ? 0.14 : 0))))
      .force("collide", forceCollide().radius((d) => nodeRadius(d) + 6))
      .on("tick", () => {
        if (fitPendingRef.current) fitView(false);
        scheduleDraw();
      })
      .on("end", () => {
        fitPendingRef.current = false;
      });
    simulationRef.current = simulation;

    const resize = () => {
      const width = container.clientWidth;
      const dpr = window.devicePixelRatio || 1;
      const pixelWidth = Math.round(width * dpr);
      const pixelHeight = Math.round(height * dpr);
      if (canvas.width === pixelWidth && canvas.height === pixelHeight) {
        return;
      }
      sizeRef.current = {width: width, height: height};
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
      canvas.style.width = width + "px";
      canvas.style.height = height + "px";
      // Anchors are absolute, so a new center means a new layout.
      if (visibleRef.current.nodes.length > 0) applyVisibility(0.2, true); else scheduleDraw();
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

    let observer = null;
    if (typeof window.ResizeObserver !== "undefined") {
      observer = new window.ResizeObserver(onResize);
      observer.observe(container);
    } else {
      window.addEventListener("resize", onResize);
    }

    const selection = select(canvas);

    const zoomBehavior = zoom()
      .scaleExtent([0.1, 8])
      // Wheel zooms only with Ctrl held, drag pans only without it.
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
      return [event.clientX - rect.left, event.clientY - rect.top];
    }

    const onMouseMove = (event) => {
      const [px, py] = pointerPosition(event);
      const node = nodeAtScreen(px, py);
      const link = node ? null : linkAtScreen(px, py);
      if (node !== hoveredRef.current || link !== hoveredLinkRef.current) {
        hoveredRef.current = node;
        hoveredLinkRef.current = link;
        canvas.style.cursor = node || link ? "pointer" : "grab";
        scheduleDraw();
      }

      if (node) {
        setTooltip({x: px, y: py, width: sizeRef.current.width, node: node, agg: aggregateNode(node, visibleRef.current.links)});
      } else {
        setTooltip(null);
      }
    }

    const onMouseLeave = () => {
      if (hoveredRef.current || hoveredLinkRef.current) {
        hoveredRef.current = null;
        hoveredLinkRef.current = null;
        scheduleDraw();
      }
      setTooltip(null);
    }

    const onClick = (event) => {
      const [px, py] = pointerPosition(event);
      const node = nodeAtScreen(px, py);
      selectedRef.current = node;
      scheduleDraw();

      const notify = callbacksRef.current.onSelect;
      if (!notify) return;
      if (node) {
        notify({node: publicNode(node), links: aggregateNode(node, visibleRef.current.links).edges});
      } else {
        notify(null);
      }
    }

    const onDoubleClick = (event) => {
      const [px, py] = pointerPosition(event);
      const node = nodeAtScreen(px, py);
      if (node) expandNode(node);
    }

    canvas.style.cursor = "grab";
    canvas.addEventListener("mousemove", onMouseMove);
    canvas.addEventListener("mouseleave", onMouseLeave);
    canvas.addEventListener("click", onClick);
    canvas.addEventListener("dblclick", onDoubleClick);

    applyVisibility(0.3);

    return () => {
      mountedRef.current = false;
      simulation.stop();
      simulation.on("tick", null);
      simulationRef.current = null;
      zoomRef.current = null;

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
    }
  }, [hasEdges, height, scheduleDraw, nodeAtScreen, linkAtScreen, expandNode, applyVisibility, fitView]);

  useEffect(() => {
    if (!hasEdges) return;

    const rootId = "ROOT:" + rootMac;
    const graph = {nodes: new Map(), links: new Map(), rootId: rootId, rootMac: rootMac};
    const root = createNode(rootId, "ROOT", rootMac, {mac: rootMac, name: rootLabel || null});
    root.x = sizeRef.current.width / 2;
    root.y = sizeRef.current.height / 2;
    graph.nodes.set(rootId, root);
    mergeEdges(graph, rootId, edges);

    graphRef.current = graph;
    hoveredRef.current = null;
    hoveredLinkRef.current = null;
    selectedRef.current = null;

    applyVisibility(1, true);
  }, [rootMac, rootLabel, edges, hasEdges, applyVisibility]);

  useEffect(() => {
    minBytesRef.current = minBytes;
    if (hasEdges) applyVisibility(0.3, true);
  }, [minBytes, hasEdges, applyVisibility]);

  useEffect(() => {
    groupingRef.current = grouping;
    if (hasEdges) applyVisibility(0.6, true);
  }, [grouping, hasEdges, applyVisibility]);

  useEffect(() => {
    if (!focus) return;
    const node = visibleRef.current.nodes.find((n) => n.id === focus.id);
    if (!node) return;

    selectedRef.current = node;
    fitPendingRef.current = false;
    scheduleDraw();

    const notify = callbacksRef.current.onSelect;
    if (notify) {
      notify({node: publicNode(node), links: aggregateNode(node, visibleRef.current.links).edges});
    }

    const canvas = canvasRef.current;
    if (!canvas || !zoomRef.current) return;
    const k = transformRef.current.k;
    const {width, height: h} = sizeRef.current;
    select(canvas).transition().duration(400)
      .call(zoomRef.current.transform, zoomIdentity.translate(width / 2 - node.x * k, h / 2 - node.y * k).scale(k));
  }, [focus, scheduleDraw]);

  const onResetView = () => fitView(true);

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

  const viewControls = (
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
  );

  if (!hasEdges) {
    return (
      <div className="border rounded d-flex align-items-center justify-content-center text-muted" style={{height: height + "px"}}>
        No communication matches the selected filters.
      </div>
    )
  }

  return (
    <div>
      <div ref={containerRef} className="border rounded" style={{position: "relative", width: "100%", height: height + "px", overflow: "hidden"}}>
        <canvas ref={canvasRef}
                style={{display: "block", borderRadius: "4px"}}
                onWheel={(e) => { if (!wheelZooms(e)) showZoomHint(); }} />
        {viewControls}
        <ZoomHint visible={zoomHint} dragText="pan" />

        {tooltip ? (
          <div style={{
            position: "absolute",
            left: Math.min(tooltip.x + 14, Math.max(0, tooltip.width - 260)) + "px",
            top: (tooltip.y > 140 ? tooltip.y - 12 : tooltip.y + 48) + "px",
            transform: tooltip.y > 140 ? "translateY(-100%)" : "none",
            pointerEvents: "none",
            zIndex: 10,
            maxWidth: "250px",
            padding: "6px 8px",
            fontSize: "12px",
            lineHeight: "1.35",
            borderRadius: "4px",
            border: "1px solid " + colors.lines,
            background: colors.background,
            color: colors.text,
            boxShadow: "0 2px 6px rgba(0,0,0,0.25)"
          }}>
            <div style={{fontWeight: "bold", wordBreak: "break-all"}}>
              {tooltip.node.label}
              {tooltip.node.name ? <span className="context-name context-name-asset ms-1">{tooltip.node.name}</span> : null}
            </div>
            <div style={{color: colors.muted}}>
              {tooltip.node.type === "ROOT" ? "This asset" : (tooltip.node.type === "INTERNAL" ? "Internal asset" : "External")}
              {tooltip.node.mac ? " · " + tooltip.node.mac : ""}
              {tooltip.node.address ? " · " + tooltip.node.address : ""}
              {tooltip.node.asn_number ? " · AS" + tooltip.node.asn_number : ""}
              {tooltip.node.country_code ? " · " + tooltip.node.country_code : ""}
            </div>
            <div>Sessions: {formatCount(tooltip.agg.sessions)}</div>
            <div>Bytes in: {formatBytes(tooltip.agg.bytesIn)} &middot; out: {formatBytes(tooltip.agg.bytesOut)}</div>
            <div>First seen: {formatDate(tooltip.agg.firstSeen)}</div>
            <div>Last seen: {formatDate(tooltip.agg.lastSeen)}</div>
            {tooltip.node.type === "INTERNAL" && tooltip.node.asset && !tooltip.node.expanded ? (
              <div style={{color: colors.muted}}>Double-click to expand</div>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  )

}
