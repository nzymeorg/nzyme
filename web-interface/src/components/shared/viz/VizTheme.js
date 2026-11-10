import {interpolateRgb, piecewise} from "d3";

export const FONT_FAMILY = "'Nunito Sans', sans-serif";

export const GRID_SPACING = 24;

// Green and red are avoided on purpose: they mean healthy and alerting elsewhere in the UI.
export const colorsForMode = (darkMode) => {
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

export const trafficRampForMode = (darkMode) => darkMode
    ? piecewise(interpolateRgb, ["#2563eb", "#38bdf8", "#e0f2fe", "#fde68a", "#fb923c"])
    : piecewise(interpolateRgb, ["#1e40af", "#0284c7", "#7dd3fc", "#eab308", "#ea580c"]);

export const tooltipStyle = (colors) => ({
  position: "absolute",
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
});

export const drawGrid = (ctx, colors, width, height) => {
  ctx.fillStyle = colors.background;
  ctx.fillRect(0, 0, width, height);

  ctx.fillStyle = colors.grid;
  for (let gx = GRID_SPACING / 2; gx < width; gx += GRID_SPACING) {
    for (let gy = GRID_SPACING / 2; gy < height; gy += GRID_SPACING) {
      ctx.fillRect(gx, gy, 1.5, 1.5);
    }
  }
}
