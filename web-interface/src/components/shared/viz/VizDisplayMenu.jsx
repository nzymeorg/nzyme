import React from "react";

export default function VizDisplayMenu({options}) {

  if (!options || options.length === 0) {
    return null;
  }

  return (
      <div className="dropdown d-inline-block">
        <button type="button" className="btn btn-sm btn-outline-secondary dropdown-toggle" data-bs-toggle="dropdown"
                data-bs-auto-close="outside" aria-expanded="false">
          Display
        </button>

        <div className="dropdown-menu dropdown-menu-end p-3">
          {options.map((o, i) => (
              <div key={o.id} className={"d-flex align-items-center justify-content-between" + (i < options.length - 1 ? " mb-2" : "")}>
                <label htmlFor={o.id} className="me-3 small text-muted mb-0 text-nowrap">{o.label}</label>
                <select id={o.id} className="form-select form-select-sm" style={{width: "250px"}} value={o.value}
                        onChange={(e) => o.onChange(e.target.value)}>
                  {o.options.map(([v, title]) => <option key={v} value={v}>{title}</option>)}
                </select>
              </div>
          ))}
        </div>
      </div>
  )

}
