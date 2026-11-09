import React, {useId, useState} from "react";
import {useLocation} from "react-router-dom";
import Filters from "./Filters";
import AppliedFilterList from "./AppliedFilterList";
import {queryParametersToFilters} from "./FilterQueryParameters";

const MODE = {
  REPLACE: "replace",
  ADD: "add"
};

export default function FilterValueIcon(props) {

  const field = props.field;
  const value = props.value;

  const fields = props.fields;
  const setFilters = props.setFilters;

  const location = useLocation();
  const inputId = useId();

  const [show, setShow] = useState(false);
  const [mode, setMode] = useState(MODE.REPLACE);

  const toggleModal = (e) => {
    e.preventDefault();

    if (!show) {
      setMode(MODE.REPLACE);
    }

    setShow(!show);
  }

  const applyFilters = (newFilters) => {
    setFilters(newFilters);
    setShow(false);
  }

  if (!setFilters || value === null || value === undefined) {
    return null;
  }

  const currentFilters = () => {
    const params = new URLSearchParams(location.search);

    // Absent while a monitor is applied, so the dialog then offers replacing only.
    if (!params.has("filters")) {
      return null;
    }

    const parsed = queryParametersToFilters(params.get("filters"), fields);

    return parsed && Object.keys(parsed).length > 0 ? parsed : null;
  }

  const modeSelector = (existing) => {
    if (!existing) {
      return null;
    }

    return (
        <div className="filter-value-mode mt-3">
          <div className="form-check">
            <input className="form-check-input" type="radio" id={inputId + "-replace"}
                   checked={mode === MODE.REPLACE} onChange={() => setMode(MODE.REPLACE)} />
            <label className="form-check-label" htmlFor={inputId + "-replace"}>
              Replace the current filters
            </label>
          </div>
          <div className="form-check">
            <input className="form-check-input" type="radio" id={inputId + "-add"}
                   checked={mode === MODE.ADD} onChange={() => setMode(MODE.ADD)} />
            <label className="form-check-label" htmlFor={inputId + "-add"}>
              Add to the current filters
            </label>
          </div>

          <AppliedFilterList filters={existing} hideHeadline={true} />
        </div>
    )
  }

  const modal = () => {
    if (!show) {
      return null;
    }

    const existing = currentFilters();

    return (
        <React.Fragment>
          <div className="modal-backdrop fade show"></div>
          <div className="modal filters-modal fade show" style={{display: "block"}}>
            <div className="modal-dialog modal-dialog-centered modal-dialog-scrollable">
              <div className="modal-content">
                <div className="modal-header">
                  <h1 className="modal-title fs-5">Add Filter</h1>
                  <button type="button" className="btn-close" onClick={toggleModal}></button>
                </div>
                <div className="modal-body">
                  <Filters fields={fields}
                           filters={mode === MODE.ADD && existing ? existing : {}}
                           setFilters={applyFilters}
                           hideAppliedFilters={true}
                           onMonitorsReady={() => {}}
                           preSelectedField={field}
                           preSelectedValue={value} />

                  {modeSelector(existing)}
                </div>
              </div>
            </div>
          </div>
        </React.Fragment>
    )
  }

  return (
      <React.Fragment>
        <i className="fa-brands fa-searchengin filter-value hide-narrow" title="Filter Value" onClick={toggleModal}></i>

        {modal()}
      </React.Fragment>
  )

}
