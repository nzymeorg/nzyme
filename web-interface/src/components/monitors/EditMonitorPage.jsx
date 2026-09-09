import React, {useEffect, useState} from "react";
import {Navigate, useParams} from "react-router-dom";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../util/UsePageTitle";
import ApiRoutes from "../../util/ApiRoutes";
import LoadingSpinner from "../misc/LoadingSpinner";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import MonitorsService from "../../services/MonitorsService";
import TapsService from "../../services/TapsService";
import MonitorTapsTable from "./shared/MonitorTapsTable";
import AppliedFilterList from "../shared/filtering/AppliedFilterList";
import reconstructFromNodeData from "../shared/filtering/FilterReconstructor";
import {onNumberInputKeyDown} from "../../util/Tools";
import monitorTypeToFilterFields from "./shared/MonitorFilterFields";
import {toast} from "react-toastify";

const monitorsService = new MonitorsService();
const tapsService = new TapsService();

export default function EditMonitorPage() {

  const {id} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [monitor, setMonitor] = useState(null);

  const [availableTaps, setAvailableTaps] = useState(null);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [triggerCondition, setTriggerCondition] = useState(0);
  const [interval, setInterval] = useState(1);
  const [lookback, setLookback] = useState(1);
  const [taps, setTaps] = useState(null);

  const [isSubmitting, setIsSubmitting] = useState(false);

  const [redirect, setRedirect] = useState(false);

  usePageTitle(monitor ? `Edit Monitor: ${monitor.name}` : "Monitor Details");

  useEffect(() => {
    monitorsService.findOne(id, organizationId, tenantId, setMonitor);
  }, [id])

  useEffect(() => {
    if (monitor) {
      tapsService.findAllTapsHighLevel(organizationId, tenantId, (r) => setAvailableTaps(r.data.taps));

      setName(monitor.name);
      setDescription(monitor.description);
      setTriggerCondition(monitor.trigger_condition);
      setInterval(monitor.interval);
      setLookback(monitor.lookback);
      setTaps(monitor.taps);
    }
  }, [monitor]);

  const breadcrumbs = () => {
    switch (monitor.type) {
      case "DOT11_BSSID":
      case "DOT11_CLIENT_CONNECTED":
      case "DOT11_CLIENT_DISCONNECTED":
        return (
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><a href={ApiRoutes.DOT11.OVERVIEW}>WiFi</a></li>
            <li className="breadcrumb-item"><a href={ApiRoutes.DOT11.MONITORING.INDEX}>Monitoring</a></li>
            <li className="breadcrumb-item"><a href={ApiRoutes.DOT11.MONITORING.MONITORS.INDEX}>Monitors</a></li>
            <li className="breadcrumb-item"><a href={ApiRoutes.ALERTS.MONITORS.DETAILS(monitor.uuid)}>{monitor.name}</a></li>
            <li className="breadcrumb-item active">Edit</li>
          </ol>
        )
      case "BLUETOOTH_DEVICE":
        return (
          <ol className="breadcrumb">
            <li className="breadcrumb-item">Bluetooth</li>
            <li className="breadcrumb-item">Monitoring</li>
            <li className="breadcrumb-item"><a href={ApiRoutes.BLUETOOTH.MONITORING.MONITORS.INDEX}>Monitors</a></li>
            <li className="breadcrumb-item"><a href={ApiRoutes.ALERTS.MONITORS.DETAILS(monitor.uuid)}>{monitor.name}</a></li>
            <li className="breadcrumb-item active">Edit</li>
          </ol>
        )
    }
  }

  const formReady = () => {
    return name && name.trim().length > 0 && triggerCondition >= 0 && interval > 0
  }

  const onSubmit = (e) => {
    e.preventDefault();
    setIsSubmitting(true);

    monitorsService.updateMonitorMetadata(monitor.uuid, name, description, triggerCondition, interval, lookback, () => {
      toast.success("Monitor updated.")
      setRedirect(true);
    }, () => {
      toast.error("Could not update monitor.")
      setIsSubmitting(false);
    })
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ALERTS.MONITORS.DETAILS(monitor.uuid)} />
  }

  if (!monitor || availableTaps === null) {
    return <LoadingSpinner />;
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-7">
          <nav aria-label="breadcrumb">
            {breadcrumbs()}
          </nav>
        </div>

        <div className="col-md-5">
            <span className="float-end">
              <a className="btn btn-secondary" href={ApiRoutes.ALERTS.MONITORS.DETAILS(monitor.uuid)}>Back</a>{' '}
            </span>
        </div>
      </div>

      <div className="row">
        <div className="col-md-12">
          <h1>
            Edit Monitor &quot;{monitor.name}&quot;
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Edit Monitor" />

              <div className="mb-3">
                <label htmlFor="monitor-name" className="form-label">Name</label>
                <input type="text"
                       className="form-control"
                       id="monitor-rule-name"
                       value={name}
                       onChange={e => setName(e.target.value)} />
                <div className="form-text">
                  The name of this monitor. Give it a name that helps to quickly identify what it is supposed to
                  alert on.
                </div>
              </div>

              <div className="mb-3">
                <label htmlFor="monitor-description" className="form-label">Description <small>Optional</small></label>
                <textarea className="form-control"
                          id="description"
                          value={description}
                          onChange={e => setDescription(e.target.value)} />
                <div className="form-text">An optional description that provides more detail.</div>
              </div>

              <h3 className="mt-4 mb-2">Taps</h3>

              <MonitorTapsTable allTaps={availableTaps}
                                selectedTaps={monitor.taps}
                                updateTaps={taps} />

              <h3 className="mt-4 mb-2">Filters</h3>

              <AppliedFilterList
                filters={reconstructFromNodeData(JSON.parse(monitor.filters), monitorTypeToFilterFields(monitor.type))}
                hideHeadline={true} />

              <div className="alert alert-info mt-3">
                <strong>To change taps or filters,</strong> apply this monitor as a search, make your changes, then
                overwrite the monitor to save.
              </div>

              <h3 className="mt-4 mb-2">Trigger</h3>

              <div className="mb-3">
                <label htmlFor="monitor-condition" className="form-label">Trigger Condition: Result Count</label>
                <div className="input-group">
                  <input type="number"
                         min={0}
                         onKeyDown={onNumberInputKeyDown}
                         className="form-control"
                         id="monitor-condition"
                         value={triggerCondition}
                         onChange={e => setTriggerCondition(e.target.value)}/>
                  <span className="input-group-text">Results</span>
                </div>
                <div className="form-text">
                  If the number of results exceeds the configured threshold, it triggers a detection event.
                </div>
              </div>

              <div className="mb-3">
                <label htmlFor="monitor-interval" className="form-label">Interval</label>
                <div className="input-group">
                  <input type="number"
                         min={1}
                         onKeyDown={onNumberInputKeyDown}
                         className="form-control"
                         id="monitor-interval"
                         value={interval}
                         onChange={e => setInterval(e.target.value)}/>
                  <span className="input-group-text">Minutes</span>
                </div>
                <div className="form-text">How often to execute the monitor. Default: Run every minute.</div>
              </div>

              <div className="mb-3">
                <label htmlFor="monitor-lookback" className="form-label">Lookback</label>
                <div className="input-group">
                  <input type="number"
                         min={1}
                         onKeyDown={onNumberInputKeyDown}
                         className="form-control"
                         id="monitor-lookback"
                         value={lookback}
                         onChange={e => setLookback(e.target.value)}/>
                  <span className="input-group-text">Minutes</span>
                </div>
                <div className="form-text">
                  Time window for each check, measured back from the current time. Default: 1 Minute.
                </div>
              </div>

              <button className="btn btn-primary"
                      disabled={!formReady() || isSubmitting}
                      type="submit"
                      onClick={onSubmit}>
                { isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> &nbsp;Updating ...</span> : "Update Monitor" }
              </button>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}