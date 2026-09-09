import React, {useEffect, useState} from "react";
import usePageTitle from "../../util/UsePageTitle";
import {Navigate, useParams} from "react-router-dom";
import ApiRoutes from "../../util/ApiRoutes";
import LoadingSpinner from "../misc/LoadingSpinner";
import MonitorsService from "../../services/MonitorsService";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import MonitorStatusIndicator from "./shared/MonitorStatusIndicator";
import MonitorType from "./shared/MonitorType";
import numeral from "numeral";
import moment from "moment";
import MonitorTapsTable from "./shared/MonitorTapsTable";
import TapsService from "../../services/TapsService";
import AppliedFilterList from "../shared/filtering/AppliedFilterList";
import reconstructFromNodeData from "../shared/filtering/FilterReconstructor";
import {toast} from "react-toastify";
import monitorTypeToFilterFields from "./shared/MonitorFilterFields";
import monitorTypeToSearchLink from "./shared/MonitorReplay";
import WithPermission from "../misc/WithPermission";
import MonitorDetectionsTimelineTable from "./MonitorDetectionsTimelineTable";
import {capitalizeFirstLetterAndLowercase} from "../../util/Tools";

const monitorsService = new MonitorsService();
const tapsService = new TapsService();

export default function MonitorDetailsPage() {

  const {id} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [monitor, setMonitor] = useState(null);
  const [taps, setTaps] = useState(null);

  const [redirect, setRedirect] = useState(false);

  usePageTitle(monitor ? `Monitor: ${monitor.name}` : "Monitor Details");

  useEffect(() => {
    monitorsService.findOne(id, organizationId, tenantId, setMonitor);
  }, [id])

  useEffect(() => {
    if (monitor) {
      tapsService.findAllTapsHighLevel(organizationId, tenantId, (r) => setTaps(r.data.taps));
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
            <li className="breadcrumb-item active">{monitor.name}</li>
          </ol>
        )
      case "BLUETOOTH_DEVICE":
        return (
          <ol className="breadcrumb">
            <li className="breadcrumb-item">Bluetooth</li>
            <li className="breadcrumb-item">Monitoring</li>
            <li className="breadcrumb-item"><a href={ApiRoutes.BLUETOOTH.MONITORING.MONITORS.INDEX}>Monitors</a></li>
            <li className="breadcrumb-item active">{monitor.name}</li>
          </ol>
        )
    }
  }
  const backLink = () => {
    switch (monitor.type) {
      case "DOT11_BSSID":
      case "DOT11_CLIENT_CONNECTED":
      case "DOT11_CLIENT_DISCONNECTED":
        return ApiRoutes.DOT11.MONITORING.MONITORS.INDEX
      case "BLUETOOTH_DEVICE":
        return ApiRoutes.BLUETOOTH.MONITORING.MONITORS.INDEX;
    }
  }

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this monitor?")) {
      return;
    }

    monitorsService.deleteMonitor(monitor.uuid, () => {
      toast.success('Monitor deleted.');
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={backLink()} />
  }

  if (!monitor || taps === null) {
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
              <a className="btn btn-secondary" href={backLink()}>Back</a>{' '}
              <a className="btn btn-secondary" href={monitorTypeToSearchLink(monitor)}>Replay</a>{' '}
              <a className="btn btn-danger" href="#" onClick={onDelete}>Delete</a>{' '}
              <a className="btn btn-primary" href={ApiRoutes.ALERTS.MONITORS.EDIT(monitor.uuid)}>Edit</a>
            </span>
        </div>
      </div>

      <div className="row">
        <div className="col-md-12">
          <h1>
            <MonitorStatusIndicator monitor={monitor} /> Monitor &quot;{monitor.name}&quot;
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Type</dt>
                <dd><MonitorType type={monitor.type} /></dd>
                <dt>Description</dt>
                <dd>{monitor.description ? monitor.description : <span className="text-muted">n/a</span> }</dd>
                <dt>Interval</dt>
                <dd>{numeral(monitor.interval).format("0,0")} {monitor.interval === 1 ? "Minute" : "Minutes"}</dd>
                <dt>Trigger Condition</dt>
                <dd>More than {numeral(monitor.trigger_condition).format("0,0")} {monitor.trigger_condition === 1 ? "result" : "results"}</dd>
                <dt>Lookback</dt>
                <dd>{numeral(monitor.lookback).format("0,0")} {monitor.lookback === 1 ? "Minute" : "Minutes"}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>Created at</dt>
                <dd title={moment(monitor.created_at).format()}>{moment(monitor.created_at).fromNow()}</dd>
                <dt>Updated at</dt>
                <dd title={moment(monitor.updated_at).format()}>{moment(monitor.updated_at).fromNow()}</dd>
                <dt>Last Run</dt>
                <dd>{monitor.last_run ? moment(monitor.last_run).format() : <span>Monitor did not run yet</span>}</dd>
                <dt>Status</dt>
                <dd>{capitalizeFirstLetterAndLowercase(monitor.status)}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Taps" />

              <MonitorTapsTable allTaps={taps} selectedTaps={monitor.taps} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <AppliedFilterList
                filters={reconstructFromNodeData(JSON.parse(monitor.filters), monitorTypeToFilterFields(monitor.type))}
                hideHeadline={true} />
            </div>
          </div>
        </div>
      </div>

      <WithPermission permission="alerts_view">
        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Detections Timeline" />

                <MonitorDetectionsTimelineTable monitor={monitor} />
              </div>
            </div>
          </div>
        </div>
      </WithPermission>

    </React.Fragment>
  )

}