import React from "react";

export default function PolicyEntries({entries, emptyLabel, emptyTitle = undefined, onRemove}) {

  if (entries.length === 0) {
    return (
      <div className="policy-entries-empty mt-2" title={emptyTitle}>
        {emptyLabel}
      </div>
    )
  }

  return (
    <div className="policy-entries mt-2">
      {entries.map((entry, i) => {
        return (
          <div className="policy-entry" key={i}>
            <span className="policy-entry-name">{entry.name}</span>
            {entry.detail ? <span className="policy-entry-detail" title={entry.detail}>{entry.detail}</span> : null}
            <a href="#" className="applied-filter-remove policy-entry-remove" title="Remove"
               onClick={(e) => onRemove(e, i)}>
              <i className="fa-solid fa-xmark" />
            </a>
          </div>
        )
      })}
    </div>
  )

}
