import React, {useState} from "react";
import Filters from "./Filters";

export default function FilterValueIcon(props) {

  const field = props.field;
  const value = props.value;

  const fields = props.fields;
  const setFilters = props.setFilters;

  const [show, setShow] = useState(false);

  const toggleModal = (e) => {
    e.preventDefault();

    setShow(!show);
  }

  if (!setFilters || value === null || value === undefined) {
    return null;
  }

  const modal = () => {
    if (!show) {
      return null;
    }

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
                           setFilters={setFilters}
                           hideAppliedFilters={true}
                           onMonitorsReady={() => {}}
                           preSelectedField={field}
                           preSelectedValue={value} />
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