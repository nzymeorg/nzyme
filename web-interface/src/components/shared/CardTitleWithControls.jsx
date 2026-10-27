import {Link} from "react-router-dom";
import React, {useState} from "react";
import TimeRangeSelector from "./timerange/TimeRangeSelector";
import AppliedTimeRange from "./timerange/AppliedTimeRange";

function CardTitleWithControls(props) {

  const title = props.title;
  const disabled = props.disabled;

  const slim = props.slim;

  const setTimeRange = props.setTimeRange;
  const timeRange = props.timeRange;
  const urlKey = props.urlKey;

  const helpLink = props.helpLink;
  const internalLink = props.internalLink;
  const refreshAction = props.refreshAction;

  const smallTextParam = props.smallText;
  const hideTimeRange = props.hideTimeRange;
  const fixedAppliedTimeRange = props.fixedAppliedTimeRange;

  const doNotPersistTimeRange = props.doNotPersistTimeRange || false;

  const [timeRangeDialogOpened, setTimeRangeDialogOpened] = useState(false);

  const smallText = () => {
    if (!smallTextParam) {
      return null;
    }
    return <small>{smallTextParam}</small>
  }

  const timeRangeButton = () => {
    if (setTimeRange && timeRange) {
      return (
        <button className="btn card-title-option"
                title="Toggle Time Range Selector"
                disabled={disabled}
                onClick={(e) => {
                  e.preventDefault();
                  setTimeRangeDialogOpened(!timeRangeDialogOpened);
                }}>
          <i className="fa-regular fa-clock"></i>
        </button>
      )
    }
  }

  const helpLinkButton = () => {
    if (!helpLink) {
      return null;
    }

    if (disabled) {
      return (
        <button className="btn card-title-option"
                title="Help"
                disabled={true}
                onClick={(e) => {
                  e.preventDefault();
                }}>
          <i className="fa-solid fa-question"></i>
        </button>
      )
    }

    return (
      <a href={helpLink} className="card-title-option" title="Help" target="_blank">
        <i className="fa-solid fa-question"></i>
      </a>
    )
  }

  const internalLinkButton = () => {
    if (!internalLink) {
      return null;
    }

    if (disabled) {
      return (
        <button className="btn card-title-option"
                title="Navigate"
                disabled={true}
                onClick={(e) => {
                  e.preventDefault();
                }}>
          <i className="fa-solid fa-diamond-turn-right"></i>
        </button>
      )
    }

    return (
      <Link to={internalLink} className="card-title-option" title="Navigate">
        <i className="fa-solid fa-diamond-turn-right"></i>
      </Link>
    )
  }

  const refreshButton = () => {
    if (refreshAction) {
      return (
        <button className="btn card-title-option"
                title="Refresh"
                disabled={disabled}
                onClick={(e) => {
                  e.preventDefault();
                  refreshAction();
                }}>
          <i className="fa-solid fa-rotate"></i>
        </button>
      )
    }
  }

  const timeRangeDialog = () => {
    if (!timeRangeDialogOpened) {
      return null;
    }

    return (
      <div className="mb-3">
        <TimeRangeSelector timeRange={timeRange}
                           setTimeRange={setTimeRange}
                           urlKey={urlKey}
                           doNotPersistTimeRange={doNotPersistTimeRange} />
      </div>
    );
  }

  const appliedTimeRange = () => {
    if (hideTimeRange || (!timeRange && !fixedAppliedTimeRange)) {
      return null;
    }

    return (
      <div className="row">
        <div className="col-12">
          <AppliedTimeRange timeRange={timeRange ? timeRange : fixedAppliedTimeRange}/>
        </div>
      </div>
    )
  }

  return (
    <div className="card-title" style={slim ? {marginBottom: 0} : {}}>
      <div className="row">
        <div className="col-10">
          <h3>{title} {smallText()}</h3>
        </div>

        <div className="col-2 text-end">
          {timeRangeButton()}
          {internalLinkButton()}
          {helpLinkButton()}
          {refreshButton()}
        </div>

        {timeRangeDialog()}
      </div>

      {appliedTimeRange()}

      {slim ? null : <hr />}
    </div>
  )
}

export default CardTitleWithControls;