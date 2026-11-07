import React, {useContext} from 'react';
import {Link} from "react-router-dom";
import {userHasPermission} from "../../util/Tools";
import {UserContext} from "../../App";

export default function SectionMenuBar({items, activeRoute}) {

  const user = useContext(UserContext);

  const visible = (item) => !item.with_permission || userHasPermission(user, item.with_permission);

  return (
      <ul className="nav nav-tabs section-menu-bar">
        {items.map((item, i) => {
          if (!visible(item)) {
            return null;
          }

          if (item.children) {
            const children = item.children.filter(visible);
            const active = children.some((child) => child.href === activeRoute);

            return (
                <li key={i} className="nav-item dropdown">
                  <a className={"nav-link dropdown-toggle " + (active ? "active" : "")} href="#" role="button"
                     data-bs-toggle="dropdown" aria-expanded="false" onClick={(e) => e.preventDefault()}>
                    {item.name}
                  </a>
                  <ul className="dropdown-menu">
                    {children.map((child, j) => (
                        <li key={j}>
                          <Link className={"dropdown-item " + (child.href === activeRoute ? "active" : "")}
                                to={child.href}>
                            {child.name}
                          </Link>
                        </li>
                    ))}
                  </ul>
                </li>
            )
          }

          return (
              <li key={i} className="nav-item">
                <Link className={"nav-link " + (item.href === activeRoute ? "active" : "")} to={item.href}>
                  {item.name}
                </Link>
              </li>
          )
        })}
      </ul>
  )

}
