import React from "react";
import {useLocation} from "react-router-dom";

function SidebarSubmenu (props) {

  const location = useLocation();
  const show = props.show;

  if (!show) {
    return null;
  }

  let className = 'nav-link'
  let expanded = ''
  if ((location.pathname === '/' && props.subhref === '/') || (props.subhref !== '/' && location.pathname.startsWith(props.subhref))) {
    className += ' nav-link-active'
    expanded = 'show'
  }

  const id = props.subhref.replace(/[^a-zA-Z ]/g, '')

  return (
        <li className="nav-item nav-item-submenu">
            <ul className="submenu">
                <li className="nav-item-submenu">
                    <a href={'#submenu-' + id} data-bs-toggle="collapse" className={'submenu-headline ' + className}>
                        <span className="nav-icon">
                            {props.icon}
                        </span>

                        {props.title}

                        <i className="float-end fa-solid fa-angle-down" />
                    </a>

                    <ul className={'collapse nav flex-column ms-1 nav-submenu ' + expanded} id={'submenu-' + id}>
                        {props.children}
                    </ul>
                </li>
            </ul>
        </li>
  )
}

export default SidebarSubmenu
