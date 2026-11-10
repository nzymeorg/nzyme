import moment from "moment";
import {FONT_FAMILY} from "./VizTheme";
import {OPERATORS} from "../filtering/Filters";
import TimeRangeDescription from "../timerange/TimeRangeDescription";

export const EXPORT_SCALE = 2;

const CAPTION_FONT_SIZE = 11;
const CAPTION_LINE_HEIGHT = 16;
const CAPTION_PADDING = 10;

const SVG_NAMESPACE = "http://www.w3.org/2000/svg";

export const exportFileName = (name, extension) => "nzyme-" + name + "-" + moment().format("YYYYMMDD-HHmmss") + "." + extension;

export function downloadBlob(blob, fileName) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // The download has started by the time the address is no longer needed.
  window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
}

export function timeRangeText(range) {
  if (!range) return "";

  if (range.type === "absolute") {
    return moment(range.from).format("YYYY-MM-DD HH:mm") + " to " + moment(range.to).format("YYYY-MM-DD HH:mm");
  }

  // Plain text for every other type of range. The component uses no hooks, so it can be called like a function.
  return String(TimeRangeDescription({timeRange: range}));
}

export function filtersText(filters, fields) {
  const parts = [];
  Object.keys(filters || {}).forEach((field) => {
    filters[field].forEach((filter) => {
      const operator = Object.values(OPERATORS).find((o) => o.name === filter.operator);
      const title = fields && fields[field] ? fields[field].title : field;
      const sign = operator ? operator.sign : filter.operator;
      parts.push(title + " " + sign + (operator && operator.no_value ? "" : " " + filter.value));
    });
  });
  return parts.length > 0 ? parts.join(", ") : null;
}

let measuringContext = null;

const fitLine = (text, maxWidth) => {
  if (measuringContext === null) {
    measuringContext = document.createElement("canvas").getContext("2d");
  }
  measuringContext.font = CAPTION_FONT_SIZE + "px " + FONT_FAMILY;

  if (measuringContext.measureText(text).width <= maxWidth) return text;

  let cut = text.length;
  while (cut > 1 && measuringContext.measureText(text.substring(0, cut) + "…").width > maxWidth) cut--;
  return text.substring(0, cut) + "…";
}

const captionHeight = (lines) => (lines.length === 0 ? 0 : CAPTION_PADDING * 2 + lines.length * CAPTION_LINE_HEIGHT);

const captionLineY = (top, i) => top + CAPTION_PADDING + i * CAPTION_LINE_HEIGHT + CAPTION_LINE_HEIGHT / 2;

export function exportCanvasAsPng(rendered, lines, colors, name) {
  const {canvas, scale} = rendered;
  const width = canvas.width / scale;
  const height = canvas.height / scale;

  const out = document.createElement("canvas");
  out.width = canvas.width;
  out.height = Math.round((height + captionHeight(lines)) * scale);

  const ctx = out.getContext("2d");
  ctx.drawImage(canvas, 0, 0);

  if (lines.length > 0) {
    ctx.scale(scale, scale);
    ctx.fillStyle = colors.background;
    ctx.fillRect(0, height, width, captionHeight(lines));
    ctx.fillStyle = colors.lines;
    ctx.fillRect(0, height, width, 1);

    ctx.font = CAPTION_FONT_SIZE + "px " + FONT_FAMILY;
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";
    lines.forEach((line, i) => {
      ctx.fillStyle = i === 0 ? colors.text : colors.muted;
      ctx.fillText(fitLine(line, width - CAPTION_PADDING * 2), CAPTION_PADDING, captionLineY(height, i));
    });
  }

  out.toBlob((blob) => {
    if (blob) downloadBlob(blob, exportFileName(name, "png"));
  }, "image/png");
}

export function svgDocument(svg, lines, colors) {
  const width = parseFloat(svg.getAttribute("width"));
  const height = parseFloat(svg.getAttribute("height"));
  const total = height + captionHeight(lines);

  const copy = svg.cloneNode(true);
  copy.setAttribute("xmlns", SVG_NAMESPACE);
  // The style of the element places it on the page. Only its font is part of the drawing.
  copy.removeAttribute("style");
  copy.setAttribute("font-family", FONT_FAMILY);
  copy.setAttribute("width", width);
  copy.setAttribute("height", total);
  copy.setAttribute("viewBox", "0 0 " + width + " " + total);

  const element = (name, attributes, text = null) => {
    const e = document.createElementNS(SVG_NAMESPACE, name);
    Object.keys(attributes).forEach((key) => e.setAttribute(key, attributes[key]));
    if (text !== null) e.textContent = text;
    return e;
  }

  copy.insertBefore(element("rect", {x: 0, y: 0, width: width, height: total, fill: colors.background}), copy.firstChild);

  if (lines.length > 0) {
    copy.appendChild(element("rect", {x: 0, y: height, width: width, height: captionHeight(lines), fill: colors.background}));
    copy.appendChild(element("rect", {x: 0, y: height, width: width, height: 1, fill: colors.lines}));
    lines.forEach((line, i) => {
      copy.appendChild(element("text", {
        x: CAPTION_PADDING,
        y: captionLineY(height, i),
        "font-size": CAPTION_FONT_SIZE,
        "dominant-baseline": "middle",
        fill: i === 0 ? colors.text : colors.muted
      }, fitLine(line, width - CAPTION_PADDING * 2)));
    });
  }

  return {markup: new window.XMLSerializer().serializeToString(copy), width: width, height: total};
}

export function exportSvg(svg, lines, colors, name) {
  const drawing = svgDocument(svg, lines, colors);
  downloadBlob(
      new window.Blob(['<?xml version="1.0" encoding="UTF-8"?>\n', drawing.markup], {type: "image/svg+xml;charset=utf-8"}),
      exportFileName(name, "svg")
  );
}

export function exportSvgAsPng(svg, lines, colors, name, onError = null) {
  const drawing = svgDocument(svg, lines, colors);

  const image = new window.Image();
  image.onload = () => {
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(drawing.width * EXPORT_SCALE);
    canvas.height = Math.round(drawing.height * EXPORT_SCALE);
    canvas.getContext("2d").drawImage(image, 0, 0, canvas.width, canvas.height);
    canvas.toBlob((blob) => {
      if (blob) {
        downloadBlob(blob, exportFileName(name, "png"));
      } else if (onError) {
        onError();
      }
    }, "image/png");
  };
  image.onerror = () => {
    if (onError) onError();
  };
  image.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(drawing.markup);
}

const csvCell = (value) => {
  if (value === null || value === undefined) return "";
  if (typeof value === "number") return String(value);

  let text = String(value);
  // Names come from the network. A spreadsheet must not take one that starts like a formula for a formula.
  if (/^[=+\-@\t\r]/.test(text)) text = "'" + text;
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}

export function exportCsv(columns, rows, name) {
  const lines = [columns.map((c) => csvCell(c[0])).join(",")];
  rows.forEach((row) => lines.push(columns.map((c) => csvCell(c[1](row))).join(",")));
  downloadBlob(new window.Blob([lines.join("\r\n") + "\r\n"], {type: "text/csv;charset=utf-8"}), exportFileName(name, "csv"));
}
