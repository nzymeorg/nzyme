import React, {forwardRef, useEffect, useState} from "react";
import {Absolute, Named, Relative} from "./TimeRange";
import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import {useApplyTimeRange} from "./TimeRangeUrl";

export {deserializeTimeRange, timeRangeFromURLOrDefault} from "./TimeRangeUrl";

export default function TimeRangeSelector(props) {

  const setTimeRange = props.setTimeRange;
  const urlKey = props.urlKey || "timerange";
  const doNotPersistTimeRange = props.doNotPersistTimeRange || false;

  const applyTimeRange = useApplyTimeRange(setTimeRange, urlKey, doNotPersistTimeRange);

  const [relativeUnit, setRelativeUnit] = useState("Minutes");
  const [relativeValue, setRelativeValue] = useState(5);

  const [absoluteStart, setAbsoluteStart] = useState(new Date(new Date().setDate(new Date().getDate() - 1)));
  const [absoluteEnd, setAbsoluteEnd] = useState(new Date());
  const [absoluteErrorMessage, setAbsoluteErrorMessage] = useState(null);
  const [absoluteSubmitEnabled, setAbsoluteSubmitEnabled] = useState(true);

  useEffect(() => {
    if (absoluteStart.getTime() < absoluteEnd.getTime()) {
      setAbsoluteErrorMessage(null);
      setAbsoluteSubmitEnabled(true);
    } else {
      setAbsoluteErrorMessage("\"From\" date must be before \"To\" date.");
      setAbsoluteSubmitEnabled(false);
    }
  }, [absoluteStart, absoluteEnd]);

  const AbsoluteInput = forwardRef(({ value, onClick }, ref) => (
    <input type="text" className="form-control" onClick={onClick} ref={ref} defaultValue={value} />
  ));

  const updateRelativeValue = (e) => {
    if (parseInt(e.target.value, 10) > 0) {
      setRelativeValue(e.target.value);
    }
  }

  const presetOption = (range, name) => {
    return <a href="#" onClick={(e) => { e.preventDefault(); applyTimeRange(range) }}>{name}</a>
  }

  const submitRelative = (e) => {
    e.preventDefault();

    switch (relativeUnit) {
      case "Minutes":
        applyTimeRange(Relative(relativeValue, "Last " + relativeValue + " Minutes"));
        break;
      case "Hours":
        applyTimeRange(Relative(relativeValue*60, "Last " + relativeValue + " Hours"));
        break;
      case "Days":
        applyTimeRange(Relative(relativeValue*60*24, "Last " + relativeValue + " Days"));
        break;
    }
  }

  const submitAbsolute = (e) => {
    e.preventDefault();
    applyTimeRange(Absolute(absoluteStart, absoluteEnd));
  }

  const absoluteErrorMessageBox = () => {
    if (absoluteErrorMessage) {
      return <div className="alert alert-danger mt-2 mb-0">{absoluteErrorMessage}</div>
    }
    return null;
  }

  return (
    <div className="timerange-selector">

      <div className="accordion" id="timerange-selector-accordion">
        <div className="accordion-item">
          <h2 className="accordion-header" id="trs-presets-heading">
            <button className="accordion-button" type="button" data-bs-toggle="collapse"
                    data-bs-target="#trs-presets-collapse" aria-expanded="true" aria-controls="trs-presets-collapse">
              <i className="accordion-icon fa-regular fa-star"></i> Presets
            </button>
          </h2>
          <div id="trs-presets-collapse" className="accordion-collapse collapse show"
               aria-labelledby="trs-presets-heading"
               data-bs-parent="#timerange-selector-accordion">
            <div className="accordion-body">
              <div className="row">
                <div className="col-4">
                  <h3>Relative</h3>
                  <ul>
                    <li>{presetOption(Relative(1, "Last Minute"), "Last Minute")}</li>
                    <li>{presetOption(Relative(5, "Last 5 Minutes"), "Last 5 Minutes")}</li>
                    <li>{presetOption(Relative(15, "Last 15 Minutes"), "Last 15 Minutes")}</li>
                    <li>{presetOption(Relative(30, "Last 30 Minutes"), "Last 30 Minutes")}</li>
                    <li>{presetOption(Relative(60, "Last 60 Minutes"), "Last 60 Minutes")}</li>
                    <li>{presetOption(Relative(240, "Last 4 Hours"), "Last 4 Hours")}</li>
                    <li>{presetOption(Relative(1440, "Last 24 Hours"), "Last 24 Hours")}</li>
                    <li>{presetOption(Relative(2880, "Last 2 Days"), "Last 2 Days")}</li>
                    <li>{presetOption(Relative(10080, "Last 7 Days"), "Last 7 Days")}</li>
                  </ul>
                </div>
                <div className="col-4">
                  <h3>Quick Selectors</h3>
                  <ul>
                    <li>{presetOption(Named("today"), "Today")}</li>
                    <li>{presetOption(Named("yesterday"), "Yesterday")}</li>
                    <li>{presetOption(Named("week_to_date"), "Week to date")}</li>
                    <li>{presetOption(Named("month_to_date"), "Month to date")}</li>
                  </ul>
                </div>
                <div className="col-4">
                  <h3>Other</h3>
                  <ul>
                    <li>{presetOption(Named("all_time"), "All Time")}</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="accordion-item">
          <h2 className="accordion-header" id="trs-relative-heading">
            <button className="accordion-button collapsed" type="button" data-bs-toggle="collapse"
                    data-bs-target="#trs-relative-collapse" aria-expanded="false" aria-controls="trs-relative-collapse">
              <i className="accordion-icon fa-solid fa-clock-rotate-left"></i> Relative
            </button>
          </h2>
          <div id="trs-relative-collapse" className="accordion-collapse collapse" aria-labelledby="trs-relative-heading"
               data-bs-parent="#timerange-selector-accordion">
            <div className="accordion-body">
              <div className="row">
                <div className="col-12">
                  <label className="form-label">Distance:</label><br/>
                  <div className="input-group mb-3">
                    <input type="number" className="form-control" id="relative-minutes" min={1}
                           value={relativeValue} onChange={updateRelativeValue}/>
                    <button className="btn btn-outline-secondary dropdown-toggle" type="button"
                            data-bs-toggle="dropdown" aria-expanded="false">{relativeUnit}
                    </button>
                    <ul className="dropdown-menu dropdown-menu-end">
                      <li><a className="dropdown-item" href="#" onClick={(e) => {
                        e.preventDefault();
                        setRelativeUnit("Minutes")
                      }}>Minutes</a></li>
                      <li><a className="dropdown-item" href="#" onClick={(e) => {
                        e.preventDefault();
                        setRelativeUnit("Hours")
                      }}>Hours</a></li>
                      <li><a className="dropdown-item" href="#" onClick={(e) => {
                        e.preventDefault();
                        setRelativeUnit("Days")
                      }}>Days</a></li>
                    </ul>
                  </div>

                  <button type="button" className="btn btn-sm btn-outline-primary" onClick={submitRelative}>
                    Apply Relative Time Range
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="accordion-item">
          <h2 className="accordion-header" id="trs-absolute-heading">
            <button className="accordion-button collapsed" type="button" data-bs-toggle="collapse"
                    data-bs-target="#trs-absolute-collapse" aria-expanded="false" aria-controls="trs-absolute-collapse">
              <i className="accordion-icon fa-regular fa-calendar"></i> Absolute
            </button>
          </h2>
          <div id="trs-absolute-collapse" className="accordion-collapse collapse" aria-labelledby="trs-absolute-heading"
               data-bs-parent="#timerange-selector-accordion">
            <div className="accordion-body">
              <div className="row">
                <div className="col-6">
                  <label className="form-label">From Date:</label><br />
                  <DatePicker showTimeSelect={true} dateFormat="YYYY-MM-dd HH:mm:SS" timeFormat="HH:mm:SS"
                              timeIntervals={1} timeCaption="Time" customInput={<AbsoluteInput />}
                              selected={absoluteStart} onChange={(date) => setAbsoluteStart(date)} />
                </div>
                <div className="col-6">
                  <label className="form-label">To Date:</label><br/>
                  <DatePicker showTimeSelect={true} dateFormat="YYYY-MM-dd HH:mm:SS" timeFormat="HH:mm:SS"
                              timeIntervals={1} timeCaption="Time" customInput={<AbsoluteInput />}
                              selected={absoluteEnd} onChange={(date) => setAbsoluteEnd(date)} />
                </div>
              </div>

              <div className="row mt-3">
                <div className="col-12">
                  <button type="button" className="btn btn-sm btn-outline-primary"
                          onClick={submitAbsolute} disabled={!absoluteSubmitEnabled}>
                    Apply Absolute Time Range
                  </button>

                  {absoluteErrorMessageBox()}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>
  )

}