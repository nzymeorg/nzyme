import React from "react";

export default function VizOptionSelect({option}) {

  return (
      <div className="d-flex align-items-center me-3 mb-2">
        <label htmlFor={option.id} className="me-2 small text-muted mb-0">{option.label}</label>
        <select id={option.id} className="form-select form-select-sm w-auto" value={option.value}
                onChange={(e) => option.onChange(e.target.value)}>
          {option.options.map(([v, title, disabled]) => <option key={v} value={v} disabled={Boolean(disabled)}>{title}</option>)}
        </select>
        {option.after ? <span className="ms-2 small text-muted text-nowrap">{option.after}</span> : null}
      </div>
  )

}
