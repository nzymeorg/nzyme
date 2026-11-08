import React from "react";
import numeral from "numeral";

/*
 * Dropdown over a findings table. Each action has a label, a handler for the selected rows and optionally a
 * handler for every record that matches the current filters and time range, not only the rows of one page.
 */
export default function FindingsBulkActions({selectedCount, matchingCount, actions, noun}) {

  const matching = actions.filter((a) => a.onMatching);
  const selectedDisabled = selectedCount === 0 ? " disabled" : "";
  const matchingDisabled = !matchingCount ? " disabled" : "";

  return (
    <div className="dropdown">
      <a className="btn btn-sm btn-outline-secondary dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown"
         aria-expanded="false">
        Actions
      </a>

      <ul className="dropdown-menu">
        <li>
          <h6 className="dropdown-header">
            {selectedCount === 0 ? "No rows selected" : numeral(selectedCount).format("0,0") + " selected " + (selectedCount === 1 ? noun : noun + "s")}
          </h6>
        </li>
        {actions.map((a) => (
          <li key={a.label}>
            <a className={"dropdown-item" + selectedDisabled} onClick={a.onSelected} href="#">
              {a.label} selected
            </a>
          </li>
        ))}

        {matching.length > 0 ?
          <React.Fragment>
            <li><hr className="dropdown-divider" /></li>
            <li>
              <h6 className="dropdown-header">
                All {numeral(matchingCount ? matchingCount : 0).format("0,0")} {noun}s matching the filters and time range,
                on every page
              </h6>
            </li>
            {matching.map((a) => (
              <li key={"matching-" + a.label}>
                <a className={"dropdown-item" + matchingDisabled} onClick={a.onMatching} href="#">
                  {a.label} all matching
                </a>
              </li>
            ))}
          </React.Fragment> : null}
      </ul>
    </div>
  )

}
