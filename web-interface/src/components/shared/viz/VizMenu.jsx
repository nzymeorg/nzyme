import React from "react";
import {Link} from "react-router-dom";

export default function VizMenu({label, items, note = null, block = false, up = false}) {

  return (
      <div className={(up ? "dropup" : "dropdown") + (block ? " flex-fill" : " d-inline-block")}>
        <button type="button" className={"btn btn-sm btn-outline-secondary dropdown-toggle" + (block ? " w-100" : "")}
                data-bs-toggle="dropdown" aria-expanded="false">
          {label}
        </button>

        <ul className="dropdown-menu dropdown-menu-end">
          {items.map((item) => (
              item.to
                  ? <li key={item.label} className="d-flex align-items-center">
                      <Link to={item.to} className="dropdown-item">{item.label}</Link>
                      <Link to={item.to} target="_blank" rel="noreferrer" className="px-3 text-muted"
                            title="Open in new tab" aria-label={item.label + " in new tab"}>
                        <i className="fa-solid fa-arrow-up-right-from-square" />
                      </Link>
                    </li>
                  : <li key={item.label}>
                      <button type="button" className="dropdown-item" onClick={item.onClick}>{item.label}</button>
                    </li>
          ))}
          {note ? <li><span className="dropdown-item-text small text-muted">{note}</span></li> : null}
        </ul>
      </div>
  )

}
