import React from "react";
import {Link} from "react-router-dom";

export default function PivotLink({to, children, title, button = null, className = "", icon = false}) {
  const label = typeof children === "string" ? children : title;
  const newTab = label ? label + " in new tab" : "Open in new tab";

  if (icon) {
    return (
      <span className={"text-nowrap " + className}>
        <Link to={to} title="Pivot" aria-label={label} className="pivot-link-icon">
          <i className="fa-solid fa-person-walking-dashed-line-arrow-right" />
        </Link>
        <Link to={to} target="_blank" rel="noreferrer" className="pivot-link-external-icon"
              title="Pivot in new window" aria-label={newTab}>
          <i className="fa-solid fa-arrow-up-right-from-square" />
        </Link>
      </span>
    );
  }

  if (button) {
    return (
      <div className={"btn-group " + className} role="group">
        <Link to={to} className={button} title={title}>{children}</Link>
        <Link to={to} target="_blank" rel="noreferrer" className={button + " pivot-link-external-btn"}
              title="Open in new tab" aria-label={newTab}>
          <i className="fa-solid fa-arrow-up-right-from-square" />
        </Link>
      </div>
    );
  }

  return (
    <span className={"text-nowrap " + className}>
      <Link to={to} title={title}>{children}</Link>
      <Link to={to} target="_blank" rel="noreferrer" className="pivot-link-external"
            title="Open in new tab" aria-label={newTab}>
        <i className="fa-solid fa-arrow-up-right-from-square" />
      </Link>
    </span>
  );
}
