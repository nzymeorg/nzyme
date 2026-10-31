import React, {useState} from "react";
import TimeRangeSelector from "../shared/timerange/TimeRangeSelector";
import AppliedTimeRange from "../shared/timerange/AppliedTimeRange";

const MODES = [
  { key: "LAST_VISIT", label: "Since my last visit" },
  { key: "LAST_SESSION", label: "Since my last session" },
  { key: "TIME_RANGE", label: "Time range" }
];

export default function BriefingHeader(props) {

  const { since, setSince, timeRange, setTimeRange, loading, onMarkSeen } = props;

  const [rangeOpen, setRangeOpen] = useState(false);

  return (
    <div className="briefing-header">
      <div className="briefing-header-controls">
        <div className="btn-group btn-group-sm briefing-since" role="group" aria-label="Briefing window">
          {MODES.map((mode) => (
            <button key={mode.key}
                    type="button"
                    className={"btn " + (since === mode.key ? "btn-primary" : "btn-outline-secondary")}
                    aria-pressed={since === mode.key}
                    onClick={() => {
                      setSince(mode.key);
                      setRangeOpen(mode.key === "TIME_RANGE");
                    }}>
              {mode.label}
            </button>
          ))}
        </div>

        {since === "TIME_RANGE" ? (
          <button type="button"
                  className="btn btn-sm btn-outline-secondary briefing-range-toggle"
                  onClick={() => setRangeOpen(!rangeOpen)}
                  title="Change time range">
            <i className="fa-regular fa-clock" /> <AppliedTimeRange timeRange={timeRange} />
          </button>
        ) : null}

        <button type="button"
                className="btn btn-sm btn-outline-secondary"
                onClick={onMarkSeen}
                disabled={loading}
                title="Start the next briefing from now">
          <i className="fa-solid fa-check-double" /> Mark as seen
        </button>
      </div>

      {since === "TIME_RANGE" && rangeOpen ? (
        <div className="briefing-range-selector">
          <TimeRangeSelector timeRange={timeRange} setTimeRange={setTimeRange} urlKey="briefing" />
        </div>
      ) : null}
    </div>
  )

}
