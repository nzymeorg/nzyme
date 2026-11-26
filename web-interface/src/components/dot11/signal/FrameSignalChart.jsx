import React, {useMemo} from "react";
import numeral from "numeral";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";
import Store from "../../../util/Store";
import {useFrameSignalInvestigation} from "./FrameSignalInvestigation";

const MISMATCH_COLOR = "#EF233C";
const GAP_BUCKETS = 10;

export function frameSignalSourceColor(index) {
  const colors = Store.get("dark_mode") ? ["#4d94f0", "#1baf7a"] : ["#1d30d7", "#178f65"];
  return colors[index % colors.length];
}

export function frameSignalReferenceColor() {
  return Store.get("dark_mode") ? "#c4c4d4" : "#373737";
}

export function frameSignalReferenceDash(index) {
  return index % 2 === 0 ? "solid" : "dash";
}

export function frameSignalMismatchColor() {
  return MISMATCH_COLOR;
}

export function frameSignalChartKey(item) {
  return item.tap_uuid + ":" + item.frequency;
}

const translucent = (hex, alpha) => {
  const value = parseInt(hex.slice(1), 16);
  return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`;
}

const signed = (value) => (value > 0 ? "+" : "") + numeral(value).format("0.0");

/*
 * A tap does not record a transmitter all the time. The line stops where it was not recorded for a while instead
 * of running straight through the gap. Every bucket is drawn as wide as it is, so that a single one is visible.
 */
const stretches = (buckets, bucketMs) => {
  const result = [];
  let stretch = [];

  buckets.forEach((bucket, i) => {
    const start = new Date(bucket.bucket).getTime();

    if (i > 0 && start - new Date(buckets[i - 1].bucket).getTime() > GAP_BUCKETS * bucketMs) {
      result.push(stretch);
      stretch = [];
    }

    stretch.push({...bucket, start: start});
  });

  if (stretch.length > 0) {
    result.push(stretch);
  }

  return result;
}

const lineOf = (allStretches, bucketMs) => {
  const x = [];
  const y = [];

  allStretches.forEach((stretch) => {
    stretch.forEach((b) => { x.push(new Date(b.start)); y.push(b.average); });

    const last = stretch[stretch.length - 1];
    x.push(new Date(last.start + bucketMs), null);
    y.push(last.average, null);
  });

  return {x: x, y: y};
}

// One closed outline per stretch: along the maximum and back along the minimum.
const rangeOf = (allStretches, bucketMs) => {
  const x = [];
  const y = [];

  allStretches.forEach((stretch) => {
    const last = stretch[stretch.length - 1];
    const end = {...last, start: last.start + bucketMs};
    const points = stretch.concat([end]);

    points.forEach((b) => { x.push(new Date(b.start)); y.push(b.max); });
    points.slice().reverse().forEach((b) => { x.push(new Date(b.start)); y.push(b.min); });
    x.push(null);
    y.push(null);
  });

  return {x: x, y: y};
}

const marksOf = (frame) => {
  const histogram = frame.signal_histogram;

  if (histogram && Object.keys(histogram).length > 0) {
    return Object.keys(histogram).map((signal) => {
      return {frame: frame, signal: parseInt(signal, 10), frames: histogram[signal], exact: true};
    });
  }

  return [{frame: frame, signal: frame.signal, frames: frame.frame_count, exact: false}];
}

const tracesOf = (chart, sources, threshold, bucketMs, dark, describeFrame) => {
  const surface = dark ? "#1c1c22" : "#f9f9f9";
  const referenceColor = frameSignalReferenceColor();
  const traces = [];
  const matches = [];
  const mismatches = [];

  sources.forEach((source, index) => {
    const reference = chart.references.find((r) => r.source === source.address);
    const allStretches = stretches(reference ? reference.buckets : [], bucketMs);
    const what = source.type === "ACCESS_POINT" ? "beacons" : "other frames";

    const range = rangeOf(allStretches, bucketMs);
    traces.push({x: range.x, y: range.y, type: "scatter", mode: "lines", line: {width: 0}, fill: "toself",
      fillcolor: translucent(referenceColor, 0.10), hoverinfo: "skip"});

    const line = lineOf(allStretches, bucketMs);
    traces.push({x: line.x, y: line.y, type: "scatter", mode: "lines",
      line: {width: 1.5, color: translucent(referenceColor, 0.6), dash: frameSignalReferenceDash(index)},
      hovertemplate: "%{y:.1f} dBm, " + what + " of " + source.address + "<br>%{x|%Y-%m-%d %H:%M}<extra></extra>"});
  });

  sources.forEach((source, index) => {
    const color = frameSignalSourceColor(index);
    const what = source.type === "ACCESS_POINT" ? "the beacons" : "its other frames";

    const marks = chart.frames.filter((f) => f.transmitter === source.address).flatMap(marksOf);
    const comparedWith = (mark) => mark.frame.reference === null || mark.frame.reference === undefined
        ? null : mark.frame.reference;
    const isMismatch = (mark) => comparedWith(mark) !== null && Math.abs(mark.signal - comparedWith(mark)) >= threshold;

    const trace = (selected, marker, enlargement) => {
      return {
        x: selected.map((m) => new Date(m.frame.timestamp)),
        y: selected.map((m) => m.signal),
        customdata: selected.map((m) => [
          m.exact ? numeral(m.frames).format("0,0") + (m.frames === 1 ? " frame" : " frames")
              : "average of " + numeral(m.frames).format("0,0") + (m.frames === 1 ? " frame" : " frames"),
          describeFrame(m.frame).join("<br>"),
          comparedWith(m) === null ? "" : "<br>" + signed(m.signal - comparedWith(m)) + " dB to " + what
        ]),
        type: "scatter",
        mode: "markers",
        marker: {
          ...marker,
          size: selected.map((m) => Math.min(9 + 1.6 * Math.sqrt(m.frames), 20) + enlargement),
          opacity: 0.9
        },
        hovertemplate: "%{y:.0f} dBm, %{customdata[0]}<br>%{x|%Y-%m-%d %H:%M:%S}<br>"
            + "%{customdata[1]}%{customdata[2]}<extra></extra>"
      }
    }

    matches.push(trace(marks.filter((m) => !isMismatch(m)),
        {symbol: "circle", color: color, line: {width: 2, color: surface}}, 0));
    mismatches.push(trace(marks.filter(isMismatch),
        {symbol: "diamond", color: MISMATCH_COLOR, line: {width: 2, color: surface}}, 6));
  });

  // Frames of two transmitters often arrive at the same place in the chart. The diamonds are larger and drawn
  // first, so that a mark on top of one leaves its corners visible.
  return traces.concat(mismatches, matches);
}

export default function FrameSignalChart({chart, sources, threshold, bucketSeconds, timeRange, setTimeRange}) {

  const dark = Boolean(Store.get("dark_mode"));
  const investigation = useFrameSignalInvestigation();

  // Plotly draws the chart again whenever it gets new traces, also when only another part of the page changed.
  const finalData = useMemo(
      () => tracesOf(chart, sources, threshold, bucketSeconds * 1000, dark, investigation.describeFrame),
      [chart, sources, threshold, bucketSeconds, dark, investigation]);

  return <SimpleBarChart height={280}
                         customMarginLeft={60}
                         customMarginBottom={35}
                         ticksuffix=" dBm"
                         timeRange={timeRange}
                         setTimeRange={setTimeRange}
                         hovermode="closest"
                         finalData={finalData} />

}
