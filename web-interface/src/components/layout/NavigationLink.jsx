import React from "react";
import {Link, useLocation} from "react-router-dom";

function NavigationLink(props) {

  const href = props.href;
  const icon = props.icon;
  const title = props.title;
  const pathMatch = props.pathMatch ? props.pathMatch : href;

  const location = useLocation();

  const show = props.show === undefined ? true : props.show;

  if (!show) {
    return null;
  }

  const inactiveClassName = "nav-link";
  let className = inactiveClassName;
  let liClassName = "";
  if ((location.pathname === "/" && href === "/")
      || (href !== '/' && location.pathname.startsWith(pathMatch))) {
    className += ' nav-link-active'
    liClassName = 'nav-item-active'
  }

  // Handle special cases.
  if (title === "Organization" && location.pathname.includes("/events")) {
    // Org events are nested under organization for super admin access. Don't mark both Events and Organization as active.
    className = inactiveClassName;
    liClassName = "";
  }

  return (
    <li className={"nav-item " + liClassName}>
      <Link to={href} className={className}>
        <span className="nav-icon">{icon}</span>
        {title}
      </Link>
    </li>
  )

}

export default NavigationLink