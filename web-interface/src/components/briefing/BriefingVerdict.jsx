import React from "react";
import {describeWindow, plural, subsystemName} from "./briefingFormat";

export default function BriefingVerdict({briefing, type, onRefresh, loading}) {

  const attention = briefing.attention || [];
  const window = briefing.window;

  const red = attention.filter((a) => a.severity === "RED").length;
  const orange = attention.filter((a) => a.severity === "ORANGE").length;
  // One row can stand for several alerts of one type.
  const alertRows = attention.filter((a) => a.severity === "ALERT");
  const alerts = alertRows.reduce((sum, a) => sum + (a.count || 1), 0);
  const urgent = red + orange + alerts;

  const alertsBySubsystem = () => {
    const sums = {};
    alertRows.forEach((a) => {
      const key = a.subsystem || "OTHER";
      sums[key] = (sums[key] || 0) + (a.count || 1);
    });
    return Object.entries(sums)
      .sort(([, a], [, b]) => b - a)
      .map(([key, count]) => count + " " + (key === "OTHER" ? "other" : subsystemName(key)));
  }

  const verdict = () => {
    if (attention.length === 0) {
      return <span>Nothing needs your attention.</span>;
    }

    if (urgent === 0) {
      return <span>{attention.length} {plural(attention.length, "thing", "things")} worth a look, nothing urgent.</span>;
    }

    const parts = [];
    if (red > 0) {
      parts.push(red + " red health " + plural(red, "indicator", "indicators"));
    }
    if (orange > 0) {
      parts.push(orange + " orange health " + plural(orange, "indicator", "indicators"));
    }
    if (alerts > 0) {
      const breakdown = type === "MAIN" ? alertsBySubsystem() : [];
      const split = breakdown.length > 1
        ? ", " + breakdown.slice(0, -1).join(", ") + " and " + breakdown[breakdown.length - 1] + ","
        : "";
      parts.push(alerts + " detection " + plural(alerts, "alert", "alerts") + split);
    }

    const sentence = parts.length === 1 ? parts[0] : parts.slice(0, -1).join(", ") + " and " + parts[parts.length - 1];

    return <span className={red > 0 ? "briefing-verdict-red" : (orange > 0 ? "briefing-verdict-orange" : "briefing-verdict-alert")}>{sentence} {urgent === 1 ? "needs" : "need"} your attention.</span>;
  }

  return (
    <div className="briefing-verdict">
      {onRefresh ? (
        <div className="briefing-verdict-controls">
          <button className="btn card-title-option"
                  title="Refresh"
                  disabled={loading}
                  onClick={(e) => {
                    e.preventDefault();
                    onRefresh();
                  }}>
            <i className={"fa-solid fa-rotate" + (loading ? " fa-spin" : "")}></i>
          </button>
        </div>
      ) : null}
      <h2 className="briefing-verdict-line">{verdict()}</h2>
      <p className="briefing-verdict-detail">{describeWindow(window)}</p>
    </div>
  )

}
