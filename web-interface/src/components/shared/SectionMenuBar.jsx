import React, {useContext} from 'react';
import {Link} from "react-router-dom";
import {userHasPermission} from "../../util/Tools";
import {UserContext} from "../../App";

export default function SectionMenuBar({items, activeRoute}) {

  const user = useContext(UserContext);

  return (
      <ul className="nav nav-tabs section-menu-bar">
        {items.map((item, i) => {
          if (item.with_permission && !userHasPermission(user, item.with_permission)) {
            return null;
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