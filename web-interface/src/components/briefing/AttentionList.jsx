import React, {useState} from "react";
import {Link} from "react-router-dom";
import {briefingLinkLabel, resolveBriefingLink} from "./briefingLinks";
import {severityMeta, subsystemName} from "./briefingFormat";

const INITIAL_ITEMS = 8;

export default function AttentionList({items, window, showSubsystem}) {

  const [showAll, setShowAll] = useState(false);

  const visible = showAll ? items : items.slice(0, INITIAL_ITEMS);

  return (
    <div className="briefing-attention">
      {items.length === 0 ? (
        <div className="briefing-empty">
          <i className="fa-solid fa-circle-check" />
          <div>
            <strong>All quiet.</strong> No new detection alerts, every tap is reporting, and nothing in the
            monitoring configuration needs fixing.
          </div>
        </div>
      ) : (
        <ol className="briefing-attention-list">
          {visible.map((item, i) => <AttentionRow key={i} item={item} window={window} index={i} showSubsystem={showSubsystem} />)}
        </ol>
      )}

      {items.length > INITIAL_ITEMS ? (
        <button type="button" className="btn btn-sm btn-outline-secondary briefing-show-more" onClick={() => setShowAll(!showAll)}>
          {showAll ? "Show fewer" : "Show all " + items.length}
        </button>
      ) : null}
    </div>
  )

}

function AttentionRow({item, window, index, showSubsystem}) {

  const meta = severityMeta(item.severity);
  const href = resolveBriefingLink(item.link, window);

  const body = (
    <React.Fragment>
      <span className="briefing-attention-glyph" title={meta.label}>
        <i className={meta.icon} />
      </span>
      <span className="briefing-attention-body">
        <span className="briefing-attention-title">
          {showSubsystem && item.subsystem ? <span className="briefing-attention-subsystem">{subsystemName(item.subsystem)}</span> : null}
          {item.title}
          {href && !item.text ? <Link to={href} className="briefing-row-link">{briefingLinkLabel(item.link)} <i className="fa-solid fa-angle-right" /></Link> : null}
        </span>
        {item.text ? (
          <span className="briefing-attention-text">
            {item.text}
            {href ? <Link to={href} className="briefing-row-link">{briefingLinkLabel(item.link)} <i className="fa-solid fa-angle-right" /></Link> : null}
          </span>
        ) : null}
      </span>
    </React.Fragment>
  );

  return (
    <li className={"briefing-attention-row sev-" + item.severity.toLowerCase()} style={{animationDelay: (index * 35) + "ms"}}>
      <div className="briefing-attention-link">{body}</div>
    </li>
  )

}
