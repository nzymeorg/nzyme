import React from "react";
import Plot from "react-plotly.js";
import Store from "../../util/Store";
import moment from "moment";
import {formatValue, windowStartLabel} from "./briefingFormat";

export default function BriefingChart({data, window, unit, kind}) {

  const dark = Store.get("dark_mode");
  const colors = dark
    ? { text: "#c4c4d4", grid: "#2a2a33", line: "#9baee5", fill: "rgba(123,147,219,0.14)", previous: "rgba(255,255,255,0.03)", label: "#1e1e26" }
    : { text: "#111111", grid: "#e6e6e6", line: "#1d30d7", fill: "rgba(29,48,215,0.08)", previous: "rgba(0,0,0,0.025)", label: "#f1f1f1" };

  // Plotly ignores time zones: Date objects are read as local, strings as given. Local strings keep everything aligned.
  const local = (t) => moment(t).format("YYYY-MM-DD HH:mm:ss");

  const sorted = [...(data || [])].sort((a, b) => new Date(a.bucket) - new Date(b.bucket));
  const x = sorted.map((d) => local(d.bucket));
  const y = sorted.map((d) => d.value);

  const from = local(window.previous_from);
  const split = local(window.from);
  const to = local(window.to);

  const empty = y.length === 0 || y.every((v) => v === 0);

  const hover = y.map((v) => formatValue(v, unit));

  const trace = kind === "bar" ? {
    x: x, y: y, type: "bar", marker: { color: colors.line }, text: hover, hovertemplate: "%{text}<extra></extra>"
  } : {
    x: x, y: y, type: "scatter", mode: "lines", line: { width: 2, color: colors.line, shape: "linear" },
    fill: "tozeroy", fillcolor: colors.fill, text: hover, hovertemplate: "%{text}<extra></extra>"
  };

  const layout = {
    // Date ticks take two lines (time, then the date on day changes), so the bottom margin holds both.
    height: 204,
    margin: { l: 36, r: 8, t: 8, b: 42 },
    paper_bgcolor: "rgba(0,0,0,0)",
    plot_bgcolor: "rgba(0,0,0,0)",
    font: { family: "Geist, sans-serif", size: 11, color: colors.text },
    showlegend: false,
    hovermode: "x",
    xaxis: {
      range: [from, to],
      gridcolor: colors.grid,
      linecolor: colors.grid,
      zeroline: false,
      fixedrange: true
    },
    yaxis: {
      gridcolor: colors.grid,
      zeroline: false,
      rangemode: "tozero",
      fixedrange: true,
      tickformat: unit === "bytes" ? "~s" : "~s"
    },
    shapes: [
      { type: "rect", xref: "x", yref: "paper", x0: from, x1: split, y0: 0, y1: 1, fillcolor: colors.previous, line: { width: 0 }, layer: "below" },
      { type: "line", xref: "x", yref: "paper", x0: split, x1: split, y0: 0, y1: 1, line: { color: colors.line, width: 1, dash: "dot" }, layer: "below" }
    ],
    annotations: [
      { xref: "x", yref: "paper", x: split, y: 1, xshift: 3, yshift: -3, text: windowStartLabel(window), showarrow: false,
        xanchor: "left", yanchor: "top", font: { size: 10, color: colors.text },
        bgcolor: colors.label, bordercolor: colors.grid, borderwidth: 1, borderpad: 3 }
    ]
  };

  return (
    <div className="briefing-chart">
      {empty ? (
        <div className="briefing-chart-empty">No data in this period.</div>
      ) : (
        <Plot data={[trace]} layout={layout} config={{ displayModeBar: false, responsive: true }} style={{ width: "100%" }} useResizeHandler />
      )}
    </div>
  )

}
