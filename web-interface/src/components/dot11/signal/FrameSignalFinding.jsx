import React from "react";
import numeral from "numeral";
import moment from "moment";
import {Absolute} from "../../shared/timerange/TimeRange";
import {useApplyTimeRange} from "../../shared/timerange/TimeRangeUrl";
import Paginator from "../../misc/Paginator";
import SignalStrength from "../../shared/SignalStrength";
import FrameSignalSourceAddress from "./FrameSignalSourceAddress";
import {useFrameSignalInvestigation} from "./FrameSignalInvestigation";
import {frameSignalChartKey} from "./FrameSignalChart";
import {frameSignalChannel} from "./FrameSignalChartNotes";

const frames = (value) => numeral(value).format("0,0") + (value === 1 ? " frame" : " frames");

const differenceOf = (value) => {
  if (Math.abs(value) < 0.5) {
    return "the same strength";
  }

  return numeral(Math.abs(value)).format("0") + " dB " + (value < 0 ? "weaker" : "stronger");
}

const TIME_SPAN_PADDING_MINUTES = 10;

const timeSpanOf = (analysis, timeRange) => {
  if (!analysis.first_frame || !analysis.last_frame) {
    return null;
  }

  const first = new Date(analysis.first_frame).getTime();
  const last = new Date(analysis.last_frame).getTime();
  const from = first - TIME_SPAN_PADDING_MINUTES * 60000;
  const to = last + TIME_SPAN_PADDING_MINUTES * 60000;

  let selected = null;
  if (timeRange.type === "relative") {
    selected = timeRange.minutes * 60000;
  } else if (timeRange.type === "absolute") {
    selected = new Date(timeRange.to).getTime() - new Date(timeRange.from).getTime();
  }

  if (selected !== null && to - from > selected / 4) {
    return null;
  }

  return {first: first, last: last, from: new Date(from), to: new Date(to)};
}

export default function FrameSignalFinding({analysis, timeRange, setTimeRange, page, setPage, perPage, selection,
                                             setSelection}) {

  const investigation = useFrameSignalInvestigation();

  const applyTimeRange = useApplyTimeRange(setTimeRange);

  const timeSpan = timeSpanOf(analysis, timeRange);
  const sourceIndex = (address) => analysis.sources.findIndex((source) => source.address === address);

  const accessPoints = analysis.sources.filter((source) => source.type === "ACCESS_POINT").length;
  const mixed = accessPoints > 0 && accessPoints < analysis.sources.length;

  const comparedWithTitle = () => {
    if (mixed) {
      return "Signal Strength of Beacons or Other Frames";
    }

    return accessPoints > 0 ? "Signal Strength of Beacons" : "Signal Strength of Other Frames";
  }

  const toggle = (key) => {
    setSelection(selection.includes(key) ? selection.filter((k) => k !== key) : selection.concat([key]));
  }

  const inTheNameOf = (source) => {
    if (analysis.sources.length < 2) {
      return null;
    }

    return (
        <React.Fragment>
          {" "}in the name of <FrameSignalSourceAddress source={source} index={sourceIndex(source.address)} />
        </React.Fragment>
    )
  }

  const verdict = (source) => {
    const accessPoint = source.type === "ACCESS_POINT";
    const other = accessPoint ? "its beacons" : "its other frames";
    const notCompared = analysis.is_truncated ? 0 : source.frame_count - source.compared_frame_count;

    if (source.compared_frame_count === 0) {
      return (
          <p className="mb-2">
            <i className="fa-solid fa-circle-question text-muted me-2" />
            <strong>The {frames(source.frame_count)}{inTheNameOf(source)} could not be compared.</strong>{" "}
            {accessPoint
                ? `The taps recorded too few beacons of this access point on the same channel in the `
                    + `${analysis.access_point_reference_minutes} minutes before each frame.`
                : `The taps recorded no other frames of this client on the same channel within `
                    + `${analysis.client_comparison_minutes} minutes of a frame.`}
          </p>
      )
    }

    const compared = source.compared_frame_count;
    const mismatches = source.mismatch_frame_count;

    const comparedFrames = (notCompared > 0 ? "compared " : "") + (compared === 1 ? "frame" : "frames");
    const number = (value) => numeral(value).format("0,0");

    const result = () => {
      if (compared === 1) {
        return <React.Fragment>The {comparedFrames}{inTheNameOf(source)} is {mismatches === 0 ? "not " : ""}a mismatch.</React.Fragment>
      }

      if (mismatches === 0) {
        return <React.Fragment>None of the {number(compared)} {comparedFrames}{inTheNameOf(source)} is a mismatch.</React.Fragment>
      }

      if (mismatches === compared) {
        return <React.Fragment>All {number(compared)} {comparedFrames}{inTheNameOf(source)} are mismatches.</React.Fragment>
      }

      return (
          <React.Fragment>
            {number(mismatches)} of the {number(compared)} {comparedFrames}{inTheNameOf(source)}{" "}
            {mismatches === 1 ? "is a mismatch" : "are mismatches"}.
          </React.Fragment>
      )
    }

    const observed = () => {
      if (mismatches > 0) {
        return (compared === 1 ? "It was " : "The frame furthest off was ")
            + `${differenceOf(source.largest_difference)} than ${other}. Another device likely sent `
            + `${mismatches === 1 ? "it" : "them"}.`;
      }

      if (compared === 1) {
        return Math.abs(source.difference) < 0.5
            ? `It arrived at the same strength as ${other}.` : `It was ${differenceOf(source.difference)} than ${other}.`;
      }

      return (Math.abs(source.difference) < 0.5
          ? `On average, the frames arrived at the same strength as ${other}. `
          : `On average, the frames were ${differenceOf(source.difference)} than ${other}. `)
          + (Math.abs(source.largest_difference) < 0.5
              ? "No frame differed." : `The frame furthest off was ${differenceOf(source.largest_difference)}.`);
    }

    return (
        <p className="mb-2">
          <span className={mismatches === 0 ? "text-success" : "text-danger"}>
            <i className={"fa-solid me-2 " + (mismatches === 0 ? "fa-circle-check" : "fa-triangle-exclamation")} />
            <strong>{result()}</strong>
          </span>{" "}
          {observed()}
          {notCompared > 0 ?
              ` ${number(notCompared)} of the ${number(source.frame_count)} frames could not be compared, because the `
              + `taps recorded too little of this ${accessPoint ? "access point" : "client"} around `
              + `${notCompared === 1 ? "it" : "them"}.`
              : ""}
        </p>
    )
  }

  return (
      <React.Fragment>
        {analysis.sources.map((source) => {
          return <React.Fragment key={source.address}>{verdict(source)}</React.Fragment>
        })}

        <p className="text-muted mb-0">
          A frame is a mismatch at a difference of {analysis.threshold} dB or more, the threshold of the Signal
          Strength Mismatch detection.
          {analysis.is_truncated ?
              ` The finding covers the latest ${numeral(analysis.max_frame_records).format("0,0")} frame records. `
              + `There are more in the selected time range.`
              : ""}
        </p>

        <p className="mb-2 mt-3">
          <strong>Total:</strong> {numeral(analysis.finding_count).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Chart</th>
            <th>Tap</th>
            <th>Channel</th>
            {analysis.sources.length > 1 ? <th>Transmitter</th> : null}
            <th>{investigation.framesTitle}</th>
            <th>Signal Strength of {investigation.framesTitle}</th>
            <th>{comparedWithTitle()}</th>
            <th>Difference</th>
            <th>Mismatches</th>
          </tr>
          </thead>
          <tbody>
          {analysis.findings.map((row) => {
            const key = frameSignalChartKey(row);
            const selected = selection.includes(key);
            const full = !selected && selection.length >= analysis.max_charts;
            const source = analysis.sources[sourceIndex(row.transmitter)];

            return (
                <tr key={key + row.transmitter}>
                  <td>
                    <input type="checkbox"
                           className="form-check-input"
                           checked={selected}
                           disabled={full}
                           title={full ? `Up to ${analysis.max_charts} taps and channels can be charted at once.`
                               : "Chart this tap and channel"}
                           onChange={() => toggle(key)} />
                  </td>
                  <td>{row.tap_name ? row.tap_name : "Unknown Tap"}</td>
                  <td>{frameSignalChannel(row.frequency)}</td>
                  {analysis.sources.length > 1 ?
                      <td><FrameSignalSourceAddress source={source} index={sourceIndex(row.transmitter)} /></td> : null}
                  <td>{numeral(row.frame_count).format("0,0")}</td>
                  <td>
                    <SignalStrength strength={row.signal} />
                    {Math.round(row.signal_min) !== Math.round(row.signal_max) ?
                        <span className="text-muted">
                          {" "}({numeral(row.signal_min).format("0")} dBm to {numeral(row.signal_max).format("0")} dBm)
                        </span> : null}
                  </td>
                  <td>
                    {row.compared_with === null ? <span className="text-muted">Nothing recorded</span>
                        : <React.Fragment>
                          <SignalStrength strength={row.compared_with} />
                          {mixed ?
                              <span className="text-muted">
                                {" "}({source.type === "ACCESS_POINT" ? "beacons" : "other frames"})
                              </span> : null}
                        </React.Fragment>}
                  </td>
                  <td>
                    {row.difference === null ? <span className="text-muted">None</span>
                        : (Math.abs(row.difference) < 0.5 ? "None" : differenceOf(row.difference))}
                  </td>
                  <td>
                    {row.compared_frame_count === 0 ? <span className="text-muted">Not compared</span>
                        : (row.mismatch_frame_count === 0 ? "None"
                            : <span className="text-danger">{frames(row.mismatch_frame_count)}</span>)}
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={analysis.finding_count} perPage={perPage} setPage={setPage} page={page} />

        <p className="mb-0 mt-3">
          Selected rows are charted below. {selection.length} of {analysis.max_charts}{" "}
          {selection.length === 1 ? "is" : "are"} selected.
        </p>

        {timeSpan ?
            <p className="mb-0 mt-3">
              {timeSpan.first === timeSpan.last
                  ? `The frames were recorded at ${moment(timeSpan.first).format("YYYY-MM-DD HH:mm:ss")}.`
                  : `The frames were recorded between ${moment(timeSpan.first).format("YYYY-MM-DD HH:mm:ss")} and `
                      + `${moment(timeSpan.last).format("YYYY-MM-DD HH:mm:ss")}.`}{" "}
              <a href="#" onClick={(e) => { e.preventDefault(); applyTimeRange(Absolute(timeSpan.from, timeSpan.to)); }}>
                Show This Time Only
              </a>
            </p> : null}
      </React.Fragment>
  )

}
