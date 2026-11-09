import React from "react";

function LimitSelector(props) {

  const limit = props.limit;
  const setLimit = props.setLimit;
  const style = props.style;

  const onSelect = (e, limit) => {
    e.preventDefault();
    setLimit(limit);
  }

  // Histograms with a fixed set of rows have no limit to select.
  if (!setLimit) {
    return null;
  }

  return (
      <div className="btn-group" style={style}>
        <button className="btn btn-outline-secondary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown">
          Limit: {limit}
        </button>
        <ul className="dropdown-menu">
          <li><a className="dropdown-item" href="#" onClick={(e) => onSelect(e, 10)}>Limit 10</a></li>
          <li><a className="dropdown-item" href="#" onClick={(e) => onSelect(e, 25)}>Limit 25</a></li>
          <li><a className="dropdown-item" href="#" onClick={(e) => onSelect(e, 50)}>Limit 50</a></li>
          <li><a className="dropdown-item" href="#" onClick={(e) => onSelect(e, 100)}>Limit 100</a></li>
        </ul>
      </div>
  )

}

export const DEFAULT_LIMIT = 10;
export default LimitSelector;