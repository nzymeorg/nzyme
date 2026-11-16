import React from "react";
import {OUTCOME_FIELDS, outcomeColor, sessionsOf, statusPaint} from "./SegmentationOverviewTheme";

// Every SVG that draws a StatusMark defines it once, under its own ID.
export function HatchPattern({id, colors}) {
  return (
      <pattern id={id} width="4.5" height="4.5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
        <line x1="2.25" y1="0" x2="2.25" y2="4.5" stroke={colors.unjudged} strokeWidth="1.5" />
      </pattern>
  )
}

export function StatusMark({x, y, width, height, status, volume = 1, colors, hatchId, nested = false}) {
  const paint = statusPaint(colors, status, volume);
  const hatch = "url(#" + hatchId + ")";

  const lowerHalf = "M" + (x + width) + "," + y + "L" + (x + width) + "," + (y + height) + "L" + x + "," + (y + height) + "Z";

  const inset = Math.min(2, width / 5, height / 5);

  return (
      <g>
        {paint.fill
            ? <rect x={x} y={y} width={width} height={height} fill={paint.fill} fillOpacity={paint.fillOpacity} />
            : null}
        {paint.hatch > 0 && !paint.half
            ? <rect x={x} y={y} width={width} height={height} fill={hatch} fillOpacity={paint.hatch} />
            : null}
        {paint.hatch > 0 && paint.half ? <path d={lowerHalf} fill={hatch} fillOpacity={paint.hatch} /> : null}
        {paint.stroke
            ? <rect x={x + 0.75} y={y + 0.75} width={Math.max(0, width - 1.5)} height={Math.max(0, height - 1.5)}
                    fill="none" stroke={paint.stroke}
                    strokeWidth={status === "NONE" ? 1 : 1.5} strokeOpacity={status === "NONE" ? 0.75 : 1} />
            : null}
        {nested
            ? <rect x={x + inset + 0.5} y={y + inset + 0.5} width={Math.max(0, width - 2 * inset - 1)}
                    height={Math.max(0, height - 2 * inset - 1)} fill="none" stroke={colors.background} strokeWidth="1" />
            : null}
      </g>
  )
}

export function GroupGlyph({group, x, y, size = 7, colors}) {
  if (group.type === "BROADCAST") {
    const h = (size - 1.4) / 2;
    return <path d={"M" + x + "," + (y - h) + "L" + (x + h) + "," + (y + h) + "L" + (x - h) + "," + (y + h) + "Z"}
                 fill="none" stroke={colors.muted} strokeWidth="1.4" strokeLinejoin="miter" />;
  }

  const external = group.type === "EXTERNAL" || group.type === "INTERNET";
  const hollow = group.type === "UNASSIGNED" || group.type === "INTERNET";
  const color = external ? colors.external : colors.internal;
  const paint = hollow ? {fill: "none", stroke: color, strokeWidth: 1.4} : {fill: color};
  const s = hollow ? size - 1.4 : size;

  return external
      ? <rect x={x - s / 2} y={y - s / 2} width={s} height={s} {...paint} />
      : <circle cx={x} cy={y} r={s / 2} {...paint} />;
}

export function OutcomeBar({x, y, width, height, counts, colors}) {
  const total = sessionsOf(counts);
  if (total <= 0 || width <= 0) return null;

  const widths = OUTCOME_FIELDS.map(([, field]) => {
    const count = counts[field] || 0;
    return count > 0 ? Math.max(Math.min(1.5, width / 3), width * count / total) : 0;
  });
  const excess = widths.reduce((sum, w) => sum + w, 0) - width;
  if (excess > 0) {
    const largest = widths.indexOf(Math.max(...widths));
    widths[largest] = Math.max(0, widths[largest] - excess);
  }

  const offsets = widths.map((w, i) => x + widths.slice(0, i).reduce((sum, before) => sum + before, 0));

  return (
      <g pointerEvents="none">
        {OUTCOME_FIELDS.map(([outcome], i) => (widths[i] > 0
            ? <rect key={outcome} x={offsets[i]} y={y} width={widths[i]} height={height}
                    fill={outcomeColor(colors, outcome)} />
            : null))}
      </g>
  )
}
