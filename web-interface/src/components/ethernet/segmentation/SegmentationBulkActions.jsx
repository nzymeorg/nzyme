import React, {useState} from "react";
import numeral from "numeral";
import {toast} from "react-toastify";
import {apiErrorMessage, filtersApplied} from "../../../util/Tools";

const SCOPE_CHECKED = "checked";
const SCOPE_MATCHING = "matching";
const SCOPE_ALL = "all";

const counted = (count, nouns) => numeral(count).format("0,0") + " " + (count === 1 ? nouns.one : nouns.many);

export const isBehindLastPage = (rows, total, page) => rows.length === 0 && total > 0 && page > 1;

/*
 * Nothing but checked rows is ever preselected, so that nobody applies an action to a whole list by confirming
 * too fast.
 */
function BulkActionDialog({action, nouns, checked, matchingCount, allCount, allCountFailed, filters, partOf,
                            matchedBy, allWhat, onClose, onComplete}) {

  const filtered = filtersApplied(filters);

  const [scope, setScope] = useState(checked.length > 0 ? SCOPE_CHECKED : null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const counts = {[SCOPE_CHECKED]: checked.length, [SCOPE_MATCHING]: matchingCount, [SCOPE_ALL]: allCount};
  const scopeCount = scope ? counts[scope] : null;
  const ready = scopeCount !== null && scopeCount > 0;

  const onSubmit = (e) => {
    e.preventDefault();

    setIsSubmitting(true);
    setErrorMessage(null);

    const onSuccess = (result) => {
      setIsSubmitting(false);
      onComplete(result);
    };

    const onError = (error) => {
      setIsSubmitting(false);

      const tried = action.confirm("the " + nouns.many);
      setErrorMessage(apiErrorMessage(error, "Could not " + tried.charAt(0).toLowerCase() + tried.slice(1) + "."));
    };

    if (scope === SCOPE_CHECKED) {
      action.onChecked(checked, onSuccess, onError);
    } else {
      // No filters stand for every row.
      action.onFiltered(scope === SCOPE_MATCHING ? filters : null, onSuccess, onError);
    }
  }

  const everyRow = (count) => {
    const rows = count === 1 ? "The only " + nouns.one : "All " + (count === null ? nouns.many : counted(count, nouns));
    return rows + (partOf ? " " + partOf : "");
  }

  const checkedLabel = checked.length === 1 ? "The checked " + nouns.one
    : "The " + (checked.length > 0 ? numeral(checked.length).format("0,0") + " " : "") + "checked " + nouns.many;

  const matchingLabel = everyRow(matchingCount)
    + (matchingCount === 1 ? " that matches " + matchedBy : " that match " + matchedBy + ", on every page");

  const allLabel = everyRow(allCount) + (allWhat ? " " + allWhat : "");

  const allHint = () => {
    if (allCountFailed) {
      return "Could not be counted.";
    }

    return allCount === null ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Counting ...</span> : null;
  }

  const option = (value, label, disabled, hint) => {
    return (
      <div className="form-check">
        <input className="form-check-input" type="radio" name="bulk-action-scope" id={"bulk-action-scope-" + value}
               checked={scope === value} disabled={disabled || isSubmitting} onChange={() => setScope(value)} />
        <label className="form-check-label" htmlFor={"bulk-action-scope-" + value}>
          {label}
          {hint ? <span className="text-muted"> {hint}</span> : null}
        </label>
      </div>
    )
  }

  return (
    <React.Fragment>
      <div className="modal-backdrop fade show"></div>
      <div className="modal fade show" style={{display: "block"}}>
        <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
          <div className="modal-content">
            <div className="modal-header">
              <h1 className="modal-title fs-5">{action.title}</h1>
              <button type="button" className="btn-close" onClick={onClose} disabled={isSubmitting}></button>
            </div>
            <div className="modal-body">
              <p>{action.description}</p>

              <label className="form-label">Apply to</label>
              {option(SCOPE_CHECKED, checkedLabel, checked.length === 0,
                checked.length === 0 ? "No rows checked." : null)}
              {filtered ? option(SCOPE_MATCHING, matchingLabel, false, null) : null}
              {option(SCOPE_ALL, allLabel, allCountFailed, allHint())}

              {errorMessage ? <div className="alert alert-danger mt-3 mb-0">{errorMessage}</div> : null}
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSubmitting}>Close</button>
              <button type="button" className={"btn " + (action.destructive ? "btn-danger" : "btn-primary")}
                      onClick={onSubmit} disabled={!ready || isSubmitting}>
                {isSubmitting ?
                  <span><i className="fa-solid fa-circle-notch fa-spin"></i> &nbsp;{action.running}</span>
                  : action.confirm(ready ? counted(scopeCount, nouns) : nouns.many)}
              </button>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}

export default function SegmentationBulkActions({nouns, actions, selection, total, filters, countAll, onDone,
                                                  partOf = null, matchedBy = "the filters", allWhat = null}) {

  const filtered = filtersApplied(filters);

  const [action, setAction] = useState(null);
  const [allCount, setAllCount] = useState(null);
  const [allCountFailed, setAllCountFailed] = useState(false);

  const onChosen = (e, chosen) => {
    e.preventDefault();
    setAction(chosen);

    if (filtered) {
      setAllCount(null);
      setAllCountFailed(false);
      countAll(setAllCount, () => setAllCountFailed(true));
    }
  }

  const onComplete = (result) => {
    if (result.affected > 0) {
      toast.success(action.done(counted(result.affected, nouns)));
    } else {
      toast.info("Nothing was changed.");
    }

    setAction(null);
    selection.clear();
    onDone(result);
  }

  return (
    <React.Fragment>
      <div className="d-flex align-items-center mb-2">
        <div className="dropdown">
          <a className="btn btn-sm btn-outline-secondary dropdown-toggle" href="#" role="button"
             data-bs-toggle="dropdown" aria-expanded="false">
            Actions
          </a>

          <ul className="dropdown-menu">
            {actions.map((a, i) => {
              const newGroup = i > 0 && a.group !== actions[i-1].group;

              return (
                <React.Fragment key={a.key}>
                  {newGroup ? <li><hr className="dropdown-divider" /></li> : null}
                  {a.group && (i === 0 || newGroup) ? <li><h6 className="dropdown-header">{a.group}</h6></li> : null}
                  <li>
                    <a className={"dropdown-item" + (a.destructive ? " text-danger" : "")} href="#"
                       onClick={(e) => onChosen(e, a)}>
                      {a.label}
                    </a>
                  </li>
                </React.Fragment>
              )
            })}
          </ul>
        </div>

        {selection.selected.length > 0 ?
          <span className="text-muted ms-2">{counted(selection.selected.length, nouns)} checked</span> : null}
      </div>

      {action ?
        <BulkActionDialog action={action}
                          nouns={nouns}
                          checked={selection.selected}
                          matchingCount={total}
                          allCount={filtered ? allCount : total}
                          allCountFailed={filtered && allCountFailed}
                          filters={filters}
                          partOf={partOf}
                          matchedBy={matchedBy}
                          allWhat={allWhat}
                          onClose={() => setAction(null)}
                          onComplete={onComplete} /> : null}
    </React.Fragment>
  )

}
