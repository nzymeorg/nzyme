import React from "react";
import numeral from "numeral";
import {formatDuration} from "../../../shared/timelines/TimelineDuration";
import useSizeEm from "./useSizeEm";

// All in em of the widget body. They mirror the sizes in the stylesheet.
const GAP = 0.3;
const CELL_WIDTH = 9;
const CELL_HEIGHT = 2.64;
const ROW_HEIGHT = 1.75;
const PROBLEMS_GAP = 0.6;
// A square and the gap to the next one. Squares are as large as the panel allows, up to the first pitch.
const SQUARE_PITCHES = [2.6, 2.2, 1.9, 1.6, 1.35, 1.15];
const SQUARE_SHARE = 0.78;
const MINIMUM_SQUARE_PITCH = 0.9;
const SQUARES_PADDING = 0.55;
const LOCATION_LABELS_WIDTH = 14.6;

const STATE_CLASSES = {
  X: "dashboard-taps-offline", A: "dashboard-taps-alert", D: "dashboard-taps-degraded", O: "dashboard-taps-ok"
};
const CELL_CLASSES = {
  OFFLINE: "dashboard-taps-offline", ALERT: "dashboard-taps-alert", DEGRADED: "dashboard-taps-degraded",
  OK: "dashboard-taps-ok"
};

const reason = (tap, now) => {
  switch (tap.reason) {
    case "OFFLINE":
      return tap.last_report
        ? "Offline " + formatDuration(Math.max(0, Math.floor((now - new Date(tap.last_report).getTime())/60000)))
        : "Never reported";
    case "ALERTS":
      return numeral(tap.active_alerts).format("0,0") + (tap.active_alerts === 1 ? " alert" : " alerts");
    case "CAPTURES_STOPPED": {
      const stopped = tap.captures_total - tap.captures_running;

      return numeral(stopped).format("0,0") + (stopped === 1 ? " capture stopped" : " captures stopped");
    }
    case "DROPS":
      return numeral(tap.dropped).format("0,0") + " dropped";
    case "CLOCK_DRIFT":
      return "Clock off " + numeral(Math.abs(tap.clock_drift_ms)/1000).format("0,0") + " s";
    default:
      return "";
  }
}

const layout = (data, size) => {
  const problemsTotal = data.offline + data.degraded + data.alerting;
  const mostProblemRows = data.problems.length === 0 ? 0
    : Math.max(1, Math.min(data.problems.length, Math.floor(size.height*0.45/ROW_HEIGHT)));
  const problemsHeight = (rows) => rows === 0 ? 0 : rows*ROW_HEIGHT + PROBLEMS_GAP;

  if (data.cells) {
    const columns = Math.max(1, Math.floor((size.width + GAP)/(CELL_WIDTH + GAP)));
    const rows = Math.ceil(data.cells.length/columns);
    const height = rows*CELL_HEIGHT + Math.max(0, rows - 1)*GAP;

    for (let problemRows = mostProblemRows; problemRows >= Math.min(1, mostProblemRows); problemRows--) {
      if (height + problemsHeight(problemRows) <= size.height) {
        return {mode: "cells", columns, problemRows, problemsTotal};
      }
    }
  }

  const available = size.height - problemsHeight(mostProblemRows);

  // The largest squares that show every tap. If not even the smallest do, some locations become bars or are
  // summed up.
  let rows = null;
  for (const pitch of SQUARE_PITCHES) {
    rows = locationRows(data, size.width, available, pitch);
    if (rows.complete) {
      break;
    }
  }

  // A few squares too many for a line cost a location a second line and the panel a location. Squares that are a
  // little smaller keep every location on one line.
  if (!rows.complete) {
    const most = Math.max(...data.locations.map((location) => location.total));
    const labels = data.locations.length === 1 ? 0 : LOCATION_LABELS_WIDTH;
    const pitch = (size.width - labels)/(most - (1 - SQUARE_SHARE))*0.995;

    if (pitch >= MINIMUM_SQUARE_PITCH && pitch < rows.pitch) {
      const oneLine = locationRows(data, size.width, available, pitch);
      const withSquares = (candidate) => candidate.locations.filter((entry) => entry.squares).length;

      if (oneLine.complete || withSquares(oneLine) > withSquares(rows)) {
        rows = oneLine;
      }
    }
  }

  return {mode: "locations", ...rows, problemRows: mostProblemRows, problemsTotal};
}

const locationRows = (data, width, available, pitch) => {
  // A single location needs no label: the panel title and its count say the same.
  const single = data.locations.length === 1;
  const labels = single ? 0 : LOCATION_LABELS_WIDTH;
  const perLine = Math.max(1, Math.floor((width - labels + pitch*(1 - SQUARE_SHARE))/pitch));

  const locations = [];
  let used = 0;
  let complete = true;
  for (let i = 0; i < data.locations.length; i++) {
    const location = data.locations[i];
    const restHeight = i < data.locations.length - 1 ? ROW_HEIGHT : 0;
    const squaresHeight = Math.max(single ? 0 : ROW_HEIGHT,
      SQUARES_PADDING + Math.ceil(location.total/perLine)*pitch);

    if (used + squaresHeight + restHeight <= available) {
      locations.push({location, squares: true});
      used += squaresHeight;
    } else if (used + ROW_HEIGHT + restHeight <= available) {
      // Too many taps for squares in the space that is left: proportions instead.
      locations.push({location, squares: false});
      used += ROW_HEIGHT;
      complete = false;
    } else {
      complete = false;
      break;
    }
  }

  return {locations, complete, single, pitch};
}

function Rest({locations}) {
  if (locations.length === 0) {
    return null;
  }

  const taps = locations.reduce((sum, location) => sum + location.total, 0);
  const offline = locations.reduce((sum, location) => sum + (location.total - location.online), 0);

  return (
    <div className="dashboard-taps-rest">
      +{numeral(locations.length).format("0,0")} {locations.length === 1 ? "location" : "locations"},{' '}
      {offline === 0 ? "all " + numeral(taps).format("0,0") + " taps online"
        : numeral(offline).format("0,0") + " of " + numeral(taps).format("0,0") + " taps offline"}
    </div>
  )
}

function Location({location, squares, single}) {
  const states = location.states.split("");

  return (
    <div className={"dashboard-taps-location" + (single ? " dashboard-taps-location-single" : "")}>
      {single ? null :
        <React.Fragment>
          <span className="dashboard-taps-location-name">{location.name ? location.name : "No location"}</span>
          <span className="dashboard-taps-location-count">
            {numeral(location.online).format("0,0")}/{numeral(location.total).format("0,0")}
          </span>
        </React.Fragment>}
      {squares ?
        <span className="dashboard-taps-squares">
          {states.map((state, i) => <i key={i} className={STATE_CLASSES[state]}></i>)}
        </span>
        :
        <span className="dashboard-taps-bar">
          {["X", "A", "D"].map((state) => {
            const count = states.filter((s) => s === state).length;

            return count === 0 ? null : <i key={state} className={STATE_CLASSES[state]} style={{flex: count}}></i>
          })}
          <i className={STATE_CLASSES.O} style={{flex: states.filter((s) => s === "O").length}}></i>
        </span>}
    </div>
  )
}

export default function TapsWidget({data, now}) {

  const [ref, size] = useSizeEm();

  if (data.total === 0) {
    return <div className="dashboard-widget-note">No taps</div>
  }

  const plan = size.height > 0 ? layout(data, size) : null;

  let problems = [];
  let hiddenProblems = 0;
  if (plan && plan.problemRows > 0) {
    const named = plan.problemsTotal > plan.problemRows ? plan.problemRows - 1 : plan.problemRows;
    problems = data.problems.slice(0, named);
    hiddenProblems = plan.problemsTotal - problems.length;
  }

  return (
    <div className="dashboard-taps" ref={ref}>
      {plan ?
        <div className="dashboard-taps-inner"
             style={plan.mode === "cells" ? undefined : {
               "--dashboard-taps-square": (plan.pitch*SQUARE_SHARE).toFixed(3) + "em",
               "--dashboard-taps-gap": (plan.pitch*(1 - SQUARE_SHARE)).toFixed(3) + "em"
             }}>
          {plan.mode === "cells" ?
            <div className="dashboard-taps-cells"
                 style={{gridTemplateColumns: `repeat(${plan.columns}, minmax(0, 1fr))`}}>
              {data.cells.map((cell, i) => {
                return (
                  <div key={i} className={"dashboard-taps-cell " + CELL_CLASSES[cell.state]}>
                    <span>{cell.name}</span>
                  </div>
                )
              })}
            </div>
            :
            <React.Fragment>
              {plan.locations.map((entry, i) => {
                return <Location key={i} location={entry.location} squares={entry.squares} single={plan.single} />
              })}
              <Rest locations={data.locations.slice(plan.locations.length)} />
            </React.Fragment>}

          {plan.problemRows > 0 ?
            <div className="dashboard-taps-problems">
              {problems.map((tap, i) => {
                return (
                  <div className="dashboard-taps-problem" key={i}>
                    <span className="dashboard-taps-problem-name">{tap.name}</span>
                    <span className="dashboard-taps-problem-location">{tap.location_name}</span>
                    <span className={"dashboard-taps-problem-reason "
                      + (tap.state === "DEGRADED" ? "dashboard-text-warn" : "dashboard-text-alert")}>
                      {reason(tap, now)}
                    </span>
                  </div>
                )
              })}
              {hiddenProblems > 0 ?
                <div className="dashboard-taps-problem">
                  <span className="dashboard-taps-problem-location dashboard-taps-problem-more">
                    +{numeral(hiddenProblems).format("0,0")} more
                  </span>
                </div> : null}
            </div> : null}
        </div> : null}
    </div>
  )

}
