import React from "react";
import numeral from "numeral";

function AlertActionMultiSelector(props) {

  const show = props.show;
  const selectedRows = props.selectedRows;
  const onDeleteSelected = props.onDeleteSelected;
  const onResolveSelected = props.onResolveSelected;

  // Optional. Actions on every alert that matches the current filters and time range, not only the selected rows.
  const matchingCount = props.matchingCount;
  const onDeleteMatching = props.onDeleteMatching;
  const onResolveMatching = props.onResolveMatching;

  if (!show) {
    return null;
  }

  const matchingActions = () => {
    if (!onDeleteMatching || !onResolveMatching) {
      return null;
    }

    const disabled = !matchingCount || matchingCount === 0 ? " disabled" : "";
    const count = numeral(matchingCount ? matchingCount : 0).format("0,0");

    return (
        <React.Fragment>
          <li><hr className="dropdown-divider" /></li>
          <li>
            <a className={"dropdown-item" + disabled} onClick={onResolveMatching} href="#">
              Mark all {count} matching as Resolved
            </a>
          </li>
          <li>
            <a className={"dropdown-item" + disabled} onClick={onDeleteMatching} href="#">
              Delete all {count} matching
            </a>
          </li>
        </React.Fragment>
    )
  }

  return (
      <div className="dropdown">
        <a className="btn btn-sm btn-outline-secondary dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
          {selectedRows.length} Selected ...
        </a>

        <ul className="dropdown-menu">
          <li>
            <a className={"dropdown-item" + (selectedRows.length === 0 ? " disabled" : "")}
               onClick={onResolveSelected}
               href="#">
              Mark as Resolved
            </a>
          </li>
          <li>
            <a className={"dropdown-item" + (selectedRows.length === 0 ? " disabled" : "")}
               onClick={onDeleteSelected}
               href="#">
              Delete
            </a>
          </li>
          {matchingActions()}
        </ul>
      </div>
  )

}

export default AlertActionMultiSelector;
