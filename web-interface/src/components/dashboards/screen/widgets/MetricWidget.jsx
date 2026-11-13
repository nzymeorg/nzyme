import React from "react";
import numeral from "numeral";
import {bitrateScale} from "../../../briefing/briefingFormat";

const scaleOf = (value, unit) => {
  if (unit !== "BITS_PER_SECOND") {
    return {divisor: 1, unit: null, format: value >= 100000 ? "0.0a" : "0,0"};
  }

  const scale = bitrateScale(value);

  return {...scale, format: scale.divisor === 1 ? "0" : "0.0"};
}

export const metricUnit = (data) => scaleOf(data.current, data.unit).unit;

const change = (current, previous) => {
  if (previous === null || previous === undefined) {
    return null;
  }

  if (current === previous) {
    return "No change";
  }

  const arrow = current > previous ? "▲" : "▼";

  if (previous === 0) {
    return arrow + " from 0";
  }

  const percent = Math.abs(current - previous)/previous;

  return arrow + " " + numeral(percent).format(percent >= 0.1 ? "0%" : "0.0%");
}

function Plot({samples, bars}) {
  const maximum = Math.max(...samples.map((sample) => sample.value));
  const minimum = bars ? 0 : Math.min(...samples.map((sample) => sample.value));
  const span = maximum - minimum || 1;
  const width = 100/samples.length;
  const distance = 100/(samples.length - 1);
  const y = (value) => (29 - (value - minimum)/span*27).toFixed(2);

  let marks;
  if (bars) {
    marks = samples.map((sample, i) => {
      return sample.value <= 0 ? null :
        <rect key={i} className="dashboard-metric-bar" x={(i*width + width*0.15).toFixed(2)} y={y(sample.value)}
              width={(width*0.7).toFixed(2)} height={(29 - y(sample.value)).toFixed(2)} />
    });
  } else {
    const line = samples.map((sample, i) => {
      return (i === 0 ? "M" : "L") + (i*distance).toFixed(2) + "," + y(sample.value);
    }).join(" ");

    marks = (
      <React.Fragment>
        <path className="dashboard-metric-area" d={line + " L100,29.5 L0,29.5 Z"} />
        <path className="dashboard-metric-line" d={line} vectorEffect="non-scaling-stroke" />
      </React.Fragment>
    );
  }

  return (
    <svg viewBox="0 0 100 30" preserveAspectRatio="none">
      <line className="dashboard-metric-base" x1="0" y1="29.5" x2="100" y2="29.5" vectorEffect="non-scaling-stroke" />
      {marks}
    </svg>
  )
}

export default function MetricWidget({data, windowLabel}) {

  const scale = scaleOf(data.current, data.unit);
  const format = (value) => numeral(value/scale.divisor).format(scale.format);
  const difference = change(data.current, data.previous);
  const bars = data.aggregation === "SUM";

  const values = data.samples.map((sample) => sample.value);
  const range = values.length === 0 ? null
    : (bars ? "Max " + format(Math.max(...values)) : format(Math.min(...values)) + " – " + format(Math.max(...values)));

  return (
    <div className="dashboard-metric">
      <div className="dashboard-metric-figure">
        <div className="dashboard-metric-value">{format(data.current)}</div>
        <div className="dashboard-metric-change">
          {difference ? difference : <span>&nbsp;</span>}
          {data.previous === null || data.previous === undefined ? null : <span>Prev {format(data.previous)}</span>}
        </div>
      </div>

      {data.samples.length < 2 ? null :
        <div className="dashboard-metric-plot">
          <Plot samples={data.samples} bars={bars} />
          <div className="dashboard-metric-axis">
            <span>{windowLabel ? "-" + windowLabel : ""}</span>
            <span>{range}</span>
            <span>Now</span>
          </div>
        </div>}
    </div>
  )

}
