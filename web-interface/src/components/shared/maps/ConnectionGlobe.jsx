import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import Globe from "globe.gl";
import {AdditiveBlending, BufferGeometry, Float32BufferAttribute, Group, Points, PointsMaterial, Vector2} from "three";
import {UnrealBloomPass} from "three/examples/jsm/postprocessing/UnrealBloomPass.js";
import {OutputPass} from "three/examples/jsm/postprocessing/OutputPass.js";
import {feature} from "topojson-client";
import countriesTopology from "world-atlas/countries-110m.json";
import countries from "i18n-iso-countries";
import {interpolateRgb, piecewise} from "d3";
import numeral from "numeral";
import Store from "../../../util/Store";
import ZoomHint, {useZoomHint, wheelZooms} from "../ZoomHint";

/*
 * Props:
 *   origin:          {lat, lng, label, detail} or null. Every point then has one flow, to or from this origin.
 *   origins:         [{id, lat, lng, label, detail, note}] for more than one origin. Needs flows.
 *   points:          [{id, lat, lng, label, detail, country_code, bytes, sessions, direction}], direction one of
 *                    OUTBOUND, INBOUND or BOTH, seen from the origin.
 *   flows:           [{originId, pointId, bytes, sessions, direction, weight}]. Only with origins. The optional
 *                    weight sets the shape of a flow in place of its bytes.
 *   selectedId:      id of the selected point or origin, or null.
 *   onSelect:        called with a point, or null when the selection is cleared.
 *   onSelectOrigin:  called with an origin. Without it, origins cannot be selected and do not react to hovering.
 *   focus:           {id, at} to fly to a point or origin. A new "at" repeats the flight for the same id.
 *   height:          viewport height in pixels.
 */

const SINGLE_ORIGIN = "__origin";

const MAX_ORIGIN_LABELS = 8;
const MAX_ORIGIN_RINGS = 12;

// The traffic ramp skips green on purpose: green means healthy elsewhere in the UI.
const colorsForMode = (darkMode) => {
  if (darkMode) {
    return {
      // Transparent so the card background shows through. A solid color is not an option here: the bloom composer
      // encodes the clear color a second time on its way to the screen, which turns any dark grey much lighter.
      space: "rgba(0,0,0,0)",
      globe: "#0d1424",
      emissive: "#101a30",
      // Kept below the bloom threshold in brightness, otherwise the rim glows too hard over the card background.
      atmosphere: "#1f8fcf",
      land: "rgba(148, 163, 184, 0.42)",
      landActive: "rgba(203, 213, 225, 0.9)",
      origin: "#22d3ee",
      traffic: piecewise(interpolateRgb, ["#2563eb", "#38bdf8", "#e0f2fe", "#fde68a", "#fb923c"]),
      pulse: "#ffffff",
      selection: "#fb7185",
      stars: "#b9c7dd",
      starsBright: "#eef4ff",
      bloom: true,
      text: "#dbe3f0",
      muted: "#8b95a7",
      tooltipBackground: "rgba(28, 28, 34, 0.94)",
      tooltipBorder: "#2a3447",
      ringAlpha: 0.75
    }
  }

  return {
    space: "rgba(0,0,0,0)",
    globe: "#e2e8f0",
    emissive: "#cbd5e1",
    atmosphere: "#0ea5e9",
    land: "rgba(100, 116, 139, 0.4)",
    landActive: "#1e3a8a",
    origin: "#0284c7",
    traffic: piecewise(interpolateRgb, ["#1e40af", "#0284c7", "#7dd3fc", "#eab308", "#ea580c"]),
    pulse: "#0f172a",
    selection: "#e11d48",
    stars: null,
    starsBright: null,
    bloom: false,
    text: "#0f172a",
    muted: "#64748b",
    tooltipBackground: "rgba(249, 249, 249, 0.96)",
    tooltipBorder: "#cbd5e1",
    ringAlpha: 0.6
  }
}

const DEFAULT_ALTITUDE = 2.1;
const FOCUS_ALTITUDE = 1.4;

const PULSE_LENGTH_KM = 700;

const distanceKm = (lat1, lng1, lat2, lng2) => {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLng = (lng2 - lng1) * rad;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

const formatBytes = (bytes) => numeral(Number(bytes) || 0).format("0.0b");
const formatCount = (count) => numeral(Number(count) || 0).format("0,0");

const escapeHtml = (s) => String(s === undefined || s === null ? "" : s)
  .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

const hexToRgba = (hex, alpha) => {
  const n = parseInt(hex.replace("#", ""), 16);
  return "rgba(" + ((n >> 16) & 255) + ", " + ((n >> 8) & 255) + ", " + (n & 255) + ", " + alpha + ")";
}

const withAlpha = (rgb, alpha) => rgb.replace(/^rgb\(/, "rgba(").replace(/\)$/, ", " + alpha + ")");

// A ring needs at least three distinct points to enclose anything. The atlas carries a few collapsed rings, which
// H3 refuses to fill with hexagons, so they are dropped here.
const usableRing = (ring) => {
  const distinct = new Set(ring.map((pt) => pt[0] + "," + pt[1]));
  return distinct.size >= 3;
}

const usablePolygon = (polygon) => polygon.length > 0 && usableRing(polygon[0]);

const LAND = feature(countriesTopology, countriesTopology.objects.countries).features
  .map((f) => {
    const g = f.geometry;
    if (g.type === "Polygon") {
      return usablePolygon(g.coordinates) ? f : null;
    }
    if (g.type === "MultiPolygon") {
      const polygons = g.coordinates.filter(usablePolygon);
      return polygons.length > 0 ? {...f, geometry: {...g, coordinates: polygons}} : null;
    }
    return null;
  })
  .filter((f) => f !== null);

// Sits to the right of its point, out of the way of the hover tooltip, which opens below the pointer.
const labelPlate = (d, colors) => {
  const el = window.document.createElement("div");
  el.style.pointerEvents = "none";
  el.style.position = "relative";
  el.style.width = "0";
  el.style.height = "0";
  const plate = window.document.createElement("div");
  plate.textContent = d.text;
  plate.style.position = "absolute";
  plate.style.left = "14px";
  plate.style.top = "0";
  plate.style.transform = "translateY(-50%)";
  plate.style.padding = "2px 7px";
  plate.style.borderRadius = "4px";
  plate.style.whiteSpace = "nowrap";
  plate.style.fontFamily = "'Nunito Sans', sans-serif";
  plate.style.fontSize = "12px";
  plate.style.fontWeight = "bold";
  plate.style.color = d.color;
  plate.style.background = colors.tooltipBackground;
  plate.style.border = "1px solid " + colors.tooltipBorder;
  el.appendChild(plate);
  return el;
}

const directionText = (direction) => {
  switch (direction) {
    case "OUTBOUND": return "Outbound";
    case "INBOUND": return "Inbound";
    default: return "Inbound and outbound";
  }
}

// globe.gl tells new from known data by object identity, so items keep their object for as long as their key stays.
const useStableObjects = (items, keyOf, keep = []) => {
  const known = useRef(new Map());

  return useMemo(() => {
    const next = new Map();
    const result = items.map((item) => {
      const key = keyOf(item);
      const existing = known.current.get(key);
      if (existing) {
        const kept = {};
        keep.forEach((field) => { kept[field] = existing[field]; });
        Object.assign(existing, item, kept);
        next.set(key, existing);
        return existing;
      }
      const created = {...item};
      next.set(key, created);
      return created;
    });
    known.current = next;
    return result;
    // The key function and the kept fields are fixed per call site.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items]);
}

const weightOf = (f) => (f.weight === undefined || f.weight === null ? (f.bytes || 0) : f.weight);

// Averaging longitudes directly breaks around the date line.
const centerOf = (places) => {
  let x = 0, y = 0, z = 0;
  places.forEach((p) => {
    const lat = p.lat * Math.PI / 180;
    const lng = p.lng * Math.PI / 180;
    x += Math.cos(lat) * Math.cos(lng);
    y += Math.cos(lat) * Math.sin(lng);
    z += Math.sin(lat);
  });
  const length = Math.hypot(x, y, z);
  if (length < 1e-6) {
    return {lat: places[0].lat, lng: places[0].lng};
  }
  return {lat: Math.asin(z / length) * 180 / Math.PI, lng: Math.atan2(y, x) * 180 / Math.PI};
}

const buildStars = (count, minRadius, maxRadius, size, color, opacity) => {
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    const r = minRadius + Math.random() * (maxRadius - minRadius);
    const theta = Math.random() * Math.PI * 2;
    const phi = Math.acos(2 * Math.random() - 1);
    positions[i * 3] = r * Math.sin(phi) * Math.cos(theta);
    positions[i * 3 + 1] = r * Math.sin(phi) * Math.sin(theta);
    positions[i * 3 + 2] = r * Math.cos(phi);
  }
  const geometry = new BufferGeometry();
  geometry.setAttribute("position", new Float32BufferAttribute(positions, 3));
  const material = new PointsMaterial({
    color: color, size: size, transparent: true, opacity: opacity, sizeAttenuation: true, depthWrite: false,
    blending: AdditiveBlending
  });
  return new Points(geometry, material);
}

const buildScenery = (colors) => {
  const group = new Group();

  if (colors.stars) {
    group.add(buildStars(1800, 1600, 2600, 4, colors.stars, 0.45));
    group.add(buildStars(60, 900, 1500, 7, colors.stars, 0.55));
    group.add(buildStars(22, 900, 1600, 10, colors.starsBright, 0.95));
  }

  const dispose = () => {
    group.traverse((obj) => {
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) obj.material.dispose();
    });
  };

  return {group: group, dispose: dispose};
}

export default function ConnectionGlobe({origin, origins, points, flows, selectedId, onSelect, onSelectOrigin,
                                         focus, height}) {

  const containerRef = useRef(null);
  const globeRef = useRef(null);
  const [failed, setFailed] = useState(false);
  const [rotating, setRotating] = useState(false);
  const [hovered, setHovered] = useState(null);
  const [zoomHint, showZoomHint] = useZoomHint();
  // Counts up whenever a new globe is built, so the data effects run against it.
  const [generation, setGeneration] = useState(0);

  const darkMode = Boolean(Store.get("dark_mode"));
  const colors = useMemo(() => colorsForMode(darkMode), [darkMode]);

  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;
  const onSelectOriginRef = useRef(onSelectOrigin);
  onSelectOriginRef.current = onSelectOrigin;
  const colorsRef = useRef(colors);
  colorsRef.current = colors;

  const originList = useMemo(() => {
    if (origins) return origins;
    return origin ? [{...origin, id: SINGLE_ORIGIN}] : [];
  }, [origins, origin]);

  const flowList = useMemo(() => {
    if (flows) return flows;
    return points.map((p) => ({
      originId: SINGLE_ORIGIN, pointId: p.id, bytes: p.bytes, sessions: p.sessions, direction: p.direction
    }));
  }, [flows, points]);

  const maxBytes = useMemo(() => points.reduce((m, p) => Math.max(m, p.bytes || 0), 0), [points]);
  const maxFlowBytes = useMemo(() => flowList.reduce((m, f) => Math.max(m, f.bytes || 0), 0), [flowList]);
  const maxFlowWeight = useMemo(() => flowList.reduce((m, f) => Math.max(m, weightOf(f)), 0), [flowList]);

  const share = useCallback((d) => {
    const max = d.kind === "POINT" ? maxBytes : maxFlowBytes;
    if (max <= 0) return 0.3;
    return 0.15 + 0.85 * Math.sqrt((d.bytes || 0) / max);
  }, [maxBytes, maxFlowBytes]);

  const weightShare = useCallback((a) => {
    if (maxFlowWeight <= 0) return 0.3;
    return 0.15 + 0.85 * Math.sqrt(weightOf(a) / maxFlowWeight);
  }, [maxFlowWeight]);

  const tooltip = useCallback((title, lines) => {
    const c = colorsRef.current;
    return '<div style="max-width: 260px; padding: 6px 8px; font-size: 12px; line-height: 1.35; border-radius: 4px; ' +
      'border: 1px solid ' + c.tooltipBorder + '; background: ' + c.tooltipBackground + '; color: ' + c.text + '; ' +
      'box-shadow: 0 2px 6px rgba(0,0,0,0.25); font-family: \'Nunito Sans\', sans-serif;">' +
      '<div style="font-weight: bold;">' + escapeHtml(title) + '</div>' +
      lines.map((l) => '<div style="' + (l.muted ? 'color: ' + c.muted + ';' : '') + '">' + escapeHtml(l.text) + '</div>').join("") +
      '</div>';
  }, []);

  const pointTooltip = useCallback((p) => tooltip(p.label, [
    {text: p.detail, muted: true},
    {text: directionText(p.direction), muted: false},
    {text: "Sessions: " + formatCount(p.sessions)},
    {text: "Traffic: " + formatBytes(p.bytes)},
  ].filter((l) => l.text)), [tooltip]);

  const originTooltip = useCallback((o) => tooltip(o.label, [
    {text: o.detail, muted: true},
    {text: o.note ? o.note : "This asset", muted: false}
  ].filter((l) => l.text)), [tooltip]);

  const flowTooltip = useCallback((a) => {
    if (a.single) return pointTooltip(a.point);
    return tooltip(a.origin.label + " and " + a.point.label, [
      {text: directionText(a.direction), muted: false},
      {text: "Sessions: " + formatCount(a.sessions)},
      {text: "Traffic: " + formatBytes(a.bytes)},
    ]);
  }, [tooltip, pointTooltip]);

  // Numeric country ids of the countries with traffic, matching the ids in the atlas.
  const activeCountries = useMemo(() => {
    const ids = new Set();
    points.forEach((p) => {
      if (!p.country_code) return;
      const numeric = countries.alpha2ToNumeric(p.country_code);
      if (numeric) ids.add(String(numeric));
    });
    return ids;
  }, [points]);

  const pointItems = useMemo(() => points.map((p) => ({...p, kind: "POINT"}))
      .concat(originList.map((o) => ({...o, kind: "ORIGIN"}))), [points, originList]);
  const pointData = useStableObjects(pointItems, (d) => d.kind + ":" + d.id);

  // Inbound flows run from the location to the origin so the pulse moves toward the origin.
  const arcItems = useMemo(() => {
    const byId = new Map(pointData.map((d) => [d.kind + ":" + d.id, d]));
    return flowList.flatMap((f) => {
      const o = byId.get("ORIGIN:" + f.originId);
      const p = byId.get("POINT:" + f.pointId);
      if (!o || !p) return [];

      const inbound = f.direction === "INBOUND";
      const flow = {
        startLat: inbound ? p.lat : o.lat, startLng: inbound ? p.lng : o.lng,
        endLat: inbound ? o.lat : p.lat, endLng: inbound ? o.lng : p.lng,
        distance: Math.max(50, distanceKm(o.lat, o.lng, p.lat, p.lng)),
        id: f.originId + "|" + f.pointId, originId: f.originId, pointId: f.pointId, origin: o, point: p,
        bytes: f.bytes, sessions: f.sessions, direction: f.direction, weight: f.weight, single: !flows
      };
      return [
        {...flow, kind: "BASE", gap: 0},
        {...flow, kind: "PULSE", gap: Math.random()}
      ];
    });
  }, [flowList, pointData, flows]);
  // The gap is where on its way a pulse starts. It stays, or every update would make the pulses jump.
  const arcData = useStableObjects(arcItems, (a) => a.kind + ":" + a.id, ["gap"]);

  const viewCenter = useCallback(() => {
    if (originList.length > 0) {
      return centerOf(originList);
    }
    if (points.length > 0) {
      return {lat: points[0].lat, lng: points[0].lng};
    }
    return {lat: 20, lng: 0};
  }, [originList, points]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return undefined;
    }

    let globe;
    try {
      globe = new Globe(container, {animateIn: true, rendererConfig: {antialias: true, alpha: true}});
    } catch {
      setFailed(true);
      return undefined;
    }

    const c = colorsRef.current;

    globe
      .width(container.clientWidth)
      .height(height)
      .backgroundColor(c.space)
      .showAtmosphere(true)
      .atmosphereColor(c.atmosphere)
      .atmosphereAltitude(darkMode ? 0.15 : 0.18)
      .hexPolygonResolution(3)
      .hexPolygonMargin(0.4)
      .hexPolygonUseDots(true)
      .hexPolygonAltitude(0.004)
      .pointLat("lat")
      .pointLng("lng")
      .pointsMerge(false)
      .pointsTransitionDuration(300)
      .arcStartLat("startLat")
      .arcStartLng("startLng")
      .arcEndLat("endLat")
      .arcEndLng("endLng")
      .arcDashInitialGap("gap")
      .arcsTransitionDuration(300)
      .ringLat("lat")
      .ringLng("lng")
      .ringAltitude(0.006)
      .ringColor((r) => (t) => hexToRgba(r.color, colorsRef.current.ringAlpha * (1 - t)))
      .ringMaxRadius("maxRadius")
      .ringPropagationSpeed("speed")
      .ringRepeatPeriod("period")
      .htmlLat("lat")
      .htmlLng("lng")
      .htmlAltitude(0.02)
      .htmlTransitionDuration(0)
      .htmlElement((d) => labelPlate(d, colorsRef.current))
      .htmlElementVisibilityModifier((el, visible) => { el.style.opacity = visible ? "1" : "0"; })
      .lineHoverPrecision(2)
      .onGlobeClick(() => onSelectRef.current(null))
      .onPointClick((p) => {
        if (p.kind === "POINT") {
          onSelectRef.current(p);
        } else if (onSelectOriginRef.current) {
          onSelectOriginRef.current(p);
        }
      })
      .onPointHover((p) => {
        if (p && p.kind === "POINT") {
          setHovered({pointId: p.id});
        } else if (p && onSelectOriginRef.current) {
          setHovered({originId: p.id});
        } else {
          setHovered(null);
        }
      })
      .onArcClick((a) => onSelectRef.current(a.point))
      .onArcHover((a) => setHovered(a ? (a.single ? {pointId: a.pointId} : {pointId: a.pointId, originId: a.originId}) : null));

    const material = globe.globeMaterial();
    material.color.set(c.globe);
    material.emissive.set(c.emissive);
    material.emissiveIntensity = 0.35;
    material.shininess = 12;

    // The land arrives after the first resize: the canvas starts at window width, and filling the countries with
    // hexagons would delay the size taking effect.
    const landTimer = window.setTimeout(() => globe.hexPolygonsData(LAND), 50);

    const scenery = buildScenery(c);
    globe.scene().add(scenery.group);

    // Bloom makes everything bright glow: pulses, busy flows and the atmosphere rim. The output pass after it is
    // what puts the result on screen: the bloom pass would otherwise do that itself through an opaque copy that
    // turns the transparent backdrop black and encodes the colors a second time.
    let bloom = null;
    let output = null;
    if (c.bloom) {
      bloom = new UnrealBloomPass(new Vector2(container.clientWidth, height), 0.35, 0.4, 0.6);
      output = new OutputPass();
      globe.postProcessingComposer().addPass(bloom);
      globe.postProcessingComposer().addPass(output);
    }

    const controls = globe.controls();
    controls.autoRotate = false;
    controls.autoRotateSpeed = 0.5;
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.minDistance = 130;
    controls.maxDistance = 600;
    const stopRotation = () => {
      if (controls.autoRotate) {
        controls.autoRotate = false;
        setRotating(false);
      }
    };
    controls.addEventListener("start", stopRotation);

    // A wheel event without Ctrl is stopped before it reaches the controls and left for the page to scroll.
    const onWheel = (event) => {
      if (!wheelZooms(event)) {
        event.stopPropagation();
        showZoomHint();
      }
    };
    container.addEventListener("wheel", onWheel, {capture: true, passive: true});

    globeRef.current = globe;
    setFailed(false);
    setRotating(false);
    setGeneration((g) => g + 1);

    const center = viewCenter();
    globe.pointOfView({lat: center.lat, lng: center.lng, altitude: DEFAULT_ALTITUDE}, 0);

    // Applied in the next frame so a resize caused by this resize does not loop.
    let resizeFrame = null;
    let lastWidth = container.clientWidth;
    const observer = new window.ResizeObserver(() => {
      if (resizeFrame !== null) return;
      resizeFrame = window.requestAnimationFrame(() => {
        resizeFrame = null;
        const width = container.clientWidth;
        if (width !== lastWidth && width > 0) {
          lastWidth = width;
          globe.width(width);
        }
      });
    });
    observer.observe(container);

    return () => {
      observer.disconnect();
      if (resizeFrame !== null) window.cancelAnimationFrame(resizeFrame);
      window.clearTimeout(landTimer);
      controls.removeEventListener("start", stopRotation);
      container.removeEventListener("wheel", onWheel, {capture: true});
      if (bloom) {
        globe.postProcessingComposer().removePass(output);
        globe.postProcessingComposer().removePass(bloom);
        output.dispose();
        bloom.dispose();
      }
      globe.scene().remove(scenery.group);
      scenery.dispose();
      globe._destructor();
      globeRef.current = null;
    };
    // Rebuilt on a theme change only. Height changes go through their own effect below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [darkMode]);

  useEffect(() => {
    if (globeRef.current) {
      globeRef.current.height(height);
    }
  }, [height, generation]);

  useEffect(() => {
    const globe = globeRef.current;
    if (!globe) return;
    globe
      .pointsData(pointData)
      .pointLabel((p) => p.kind === "ORIGIN" ? originTooltip(p) : pointTooltip(p))
      .arcsData(arcData)
      .arcLabel((a) => flowTooltip(a));
  }, [pointData, arcData, pointTooltip, originTooltip, flowTooltip, generation]);

  useEffect(() => {
    const globe = globeRef.current;
    if (!globe) return;

    const c = colors;
    const pointLit = (id) => id === selectedId || (hovered !== null && !hovered.originId && hovered.pointId === id);
    const originLit = (id) => id === selectedId || (hovered !== null && !hovered.pointId && hovered.originId === id);
    const flowLit = (a) => pointLit(a.pointId) || originLit(a.originId)
        || (hovered !== null && hovered.pointId === a.pointId && hovered.originId === a.originId);
    const heat = (d) => (share(d) - 0.15) / 0.85;
    const trafficColor = (d) => c.traffic(heat(d));
    const baseStroke = (a) => 0.15 + 0.9 * weightShare(a);
    // Kilometers per millisecond.
    const pulseSpeed = (a) => 1.2 + 2.3 * heat(a);

    globe
      .pointColor((p) => {
        if (p.kind === "ORIGIN") return c.origin;
        return pointLit(p.id) ? c.selection : trafficColor(p);
      })
      .pointAltitude((p) => p.kind === "ORIGIN" ? 0.05 : 0.03 + 0.28 * share(p))
      .pointRadius((p) => {
        if (p.kind === "ORIGIN") return originLit(p.id) ? 0.72 : 0.55;
        return (0.3 + 0.5 * share(p)) * (pointLit(p.id) ? 1.3 : 1);
      })
      .arcColor((a) => {
        if (a.kind === "PULSE") {
          return c.pulse;
        }
        const color = flowLit(a) ? c.selection : trafficColor(a);
        return withAlpha(color, flowLit(a) ? 1 : 0.6 + 0.4 * heat(a));
      })
      .arcStroke((a) => baseStroke(a) * (a.kind === "PULSE" ? 1.5 : 1) * (flowLit(a) ? 1.5 : 1))
      .arcAltitudeAutoScale((a) => 0.3 + 0.2 * weightShare(a))
      // The pulse is a dash of fixed length at a fixed speed, so a short flow does not look faster than a long
      // one. Dash and gap are fractions of the flow length, the animation time is the time for one full length.
      .arcDashLength((a) => a.kind === "PULSE" ? Math.min(1, PULSE_LENGTH_KM / a.distance) : 1)
      .arcDashGap((a) => {
        if (a.kind !== "PULSE") return 0;
        const speed = pulseSpeed(a);
        const interval = 2600 - 1400 * heat(a);
        return Math.max(0, interval * speed / a.distance - Math.min(1, PULSE_LENGTH_KM / a.distance));
      })
      .arcDashAnimateTime((a) => a.kind === "PULSE" ? a.distance / pulseSpeed(a) : 0);
  }, [selectedId, hovered, colors, share, weightShare, generation]);

  const labelItems = useMemo(() => {
    const pointLit = (id) => id === selectedId || (hovered !== null && !hovered.originId && hovered.pointId === id);
    const originLit = (id) => id === selectedId || (hovered !== null && !hovered.pointId && hovered.originId === id);

    const labels = [];
    originList.forEach((o) => {
      if (originList.length <= MAX_ORIGIN_LABELS || originLit(o.id)) {
        labels.push({key: "ORIGIN:" + o.id, lat: o.lat, lng: o.lng, text: o.label, color: colors.origin});
      }
    });
    points.forEach((p) => {
      if (pointLit(p.id)) {
        labels.push({key: "POINT:" + p.id, lat: p.lat, lng: p.lng, text: p.label, color: colors.selection});
      }
    });
    return labels;
  }, [points, originList, selectedId, hovered, colors]);
  const labelData = useStableObjects(labelItems, (l) => l.key + "|" + l.text + "|" + l.color);

  useEffect(() => {
    if (globeRef.current) {
      globeRef.current.htmlElementsData(labelData);
    }
  }, [labelData, generation]);

  // A ring that is replaced restarts its pulse, so rings only change with their places, not with every new value.
  const ringItems = useMemo(() => {
    const rings = originList.slice(0, MAX_ORIGIN_RINGS).map((o) => ({
      key: "ORIGIN:" + o.id + ":" + o.lat + "," + o.lng + (o.id === selectedId ? ":selected" : ""),
      lat: o.lat, lng: o.lng, color: o.id === selectedId ? colors.selection : colors.origin,
      maxRadius: 4, speed: 1.5, period: 1400
    }));
    const selected = points.find((p) => p.id === selectedId);
    if (selected) {
      rings.push({
        key: "POINT:" + selected.id + ":" + selected.lat + "," + selected.lng,
        lat: selected.lat, lng: selected.lng, color: colors.selection, maxRadius: 5, speed: 2.5, period: 900
      });
    }
    return rings;
  }, [points, originList, selectedId, colors]);
  const ringData = useStableObjects(ringItems, (r) => r.key + "|" + r.color);

  useEffect(() => {
    if (globeRef.current) {
      globeRef.current.ringsData(ringData);
    }
  }, [ringData, generation]);

  // A margin change rebuilds the hex cells of the affected countries only.
  useEffect(() => {
    const globe = globeRef.current;
    if (!globe) return;
    const active = (d) => activeCountries.has(String(d.id));
    globe
      .hexPolygonColor((d) => active(d) ? colors.landActive : colors.land)
      .hexPolygonMargin((d) => active(d) ? 0.22 : 0.4);
  }, [activeCountries, colors, generation]);

  useEffect(() => {
    const globe = globeRef.current;
    if (!globe || !focus) {
      return;
    }
    const target = points.find((p) => p.id === focus.id) || originList.find((o) => o.id === focus.id);
    if (!target) {
      return;
    }
    globe.controls().autoRotate = false;
    setRotating(false);
    globe.pointOfView({lat: target.lat, lng: target.lng, altitude: FOCUS_ALTITUDE}, 900);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focus]);

  const resetView = () => {
    const globe = globeRef.current;
    if (!globe) return;
    const center = viewCenter();
    globe.pointOfView({lat: center.lat, lng: center.lng, altitude: DEFAULT_ALTITUDE}, 800);
  }

  const toggleRotation = () => {
    const globe = globeRef.current;
    if (!globe) return;
    const next = !rotating;
    globe.controls().autoRotate = next;
    setRotating(next);
  }

  if (failed) {
    return (
      <div className="border rounded d-flex align-items-center justify-content-center text-muted text-center p-3" style={{height: height + "px"}}>
        The map needs WebGL, which this browser does not provide.
      </div>
    )
  }

  return (
    <div className="border rounded connection-globe" style={{position: "relative", width: "100%", height: height + "px", overflow: "hidden"}}>
      {/* Out of the flow: the canvas starts at window width and would hold the card open for good. */}
      <div ref={containerRef} style={{position: "absolute", top: 0, left: 0, width: "100%", height: height + "px"}} />
      <ZoomHint visible={zoomHint} dragText="turn the globe" />
      <div style={{position: "absolute", top: "8px", right: "8px", zIndex: 5}}>
        <button className="btn btn-sm btn-outline-secondary me-2" type="button" onClick={resetView}>
          Reset view
        </button>
        <button className={"btn btn-sm " + (rotating ? "btn-secondary" : "btn-outline-secondary")}
                type="button"
                onClick={toggleRotation}>
          {rotating ? "Stop rotation" : "Rotate"}
        </button>
      </div>
    </div>
  )

}
