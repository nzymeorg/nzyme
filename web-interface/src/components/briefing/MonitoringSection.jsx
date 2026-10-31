import React from "react";
import {Link} from "react-router-dom";
import {briefingLinkLabel, resolveBriefingLink} from "./briefingLinks";
import {subsystemName} from "./briefingFormat";

const LEVEL = {
  OK:   { icon: "fa-solid fa-check", label: "Fine" },
  WARN: { icon: "fa-solid fa-triangle-exclamation", label: "Needs fixing" },
  INFO: { icon: "fa-solid fa-circle-info", label: "Worth knowing" }
}

const HEALTH_NAMES = {
  crypto_sync: "Crypto sync", db_clock: "DB clock", node_clock: "Node clock", tap_clock: "Tap clock",
  node_offline: "Node offline", tls_exp: "TLS expiration", tap_offline: "Tap offline", tap_tpx: "Tap throughput",
  tap_drop: "Tap drop", tap_buffer: "Tap buffer", tap_error: "Tap error", tasks_queue_task_stuck: "Task stuck",
  tasks_queue_task_failure: "Task failure", message_bus_message_failure: "Message failure",
  message_bus_message_stuck: "Message stuck", tap_file_permissions: "Tap file permissions",
  node_file_permissions: "Node file permissions", connect: "Connect"
}

export default function MonitoringSection({monitoring}) {

  return (
    <div className="briefing-monitoring">
      <ChecksList checks={monitoring.checks} />
    </div>
  )

}

function ChecksList({checks}) {

  return (
    <ul className="briefing-checks">
      {checks.map((check) => {
        const level = LEVEL[check.level] || LEVEL.INFO;
        const href = resolveBriefingLink(check.link);
        const content = (
          <React.Fragment>
            <span className={"briefing-check-glyph level-" + check.level.toLowerCase()} title={level.label}>
              <i className={level.icon} />
            </span>
            <span className="briefing-check-body">
              <span className="briefing-check-title">
                {check.subsystem ? <span className="briefing-attention-subsystem">{subsystemName(check.subsystem)}</span> : null}
                {check.title}
                {href && !check.detail ? <Link to={href} className="briefing-row-link">{briefingLinkLabel(check.link)} <i className="fa-solid fa-angle-right" /></Link> : null}
              </span>
              {check.detail ? (
                <span className="briefing-check-detail">
                  {check.detail}
                  {href ? <Link to={href} className="briefing-row-link">{briefingLinkLabel(check.link)} <i className="fa-solid fa-angle-right" /></Link> : null}
                </span>
              ) : null}
            </span>
          </React.Fragment>
        );

        return (
          <li key={check.key} className={"briefing-check level-" + check.level.toLowerCase()}>
            <div>{content}</div>
          </li>
        )
      })}
    </ul>
  )

}

export function ClusterHealth({indicators}) {

  const entries = Object.entries(indicators || {});
  const count = (level) => entries.filter(([, l]) => l === level).length;
  const green = count("GREEN");
  const orange = count("ORANGE");
  const red = count("RED");
  const notGreen = entries.filter(([, level]) => level !== "GREEN")
    .sort(([, a], [, b]) => (a === "RED" ? 0 : 1) - (b === "RED" ? 0 : 1));

  return (
    <div className="briefing-health">
      <div className="briefing-health-counts">
        <span className="briefing-health-count level-green"><i aria-hidden="true" />{green} green</span>
        <span className={"briefing-health-count level-orange" + (orange === 0 ? " none" : "")}><i aria-hidden="true" />{orange} orange</span>
        <span className={"briefing-health-count level-red" + (red === 0 ? " none" : "")}><i aria-hidden="true" />{red} red</span>
      </div>
      {notGreen.length === 0 ? (
        <p className="briefing-health-allgreen">All indicators are green.</p>
      ) : (
        <ul className="briefing-health-list">
          {notGreen.map(([id, level]) => (
            <li key={id} className={"level-" + level.toLowerCase()}>
              <i aria-hidden="true" />
              <span className="briefing-health-name">{HEALTH_NAMES[id] || id}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )

}
