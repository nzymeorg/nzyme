import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import DetectionAlertsService from "../../services/DetectionAlertsService";
import LoadingSpinner from "../misc/LoadingSpinner";
import ApiRoutes from "../../util/ApiRoutes";
import moment from "moment/moment";
import Subsystem from "../misc/Subsystem";
import AlertActiveIndicator from "./AlertActiveIndicator";
import AlertStatus from "./AlertStatus";
import LocationLink from "../shared/locations/LocationLink";
import L4Address from "../ethernet/shared/L4Address";
import EthernetMacAddress from "../shared/context/macs/EthernetMacAddress";
import numeral from "numeral";
import AlertDetails from "./alertdetails/AlertDetails";
import AlertActions from "./actions/AlertActions";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import AlertTimeline from "./AlertTimeline";
import RelatedMonitoredNetwork from "./RelatedMonitoredNetwork";
import WithPermission from "../misc/WithPermission";
import usePageTitle from "../../util/UsePageTitle";
import {toast} from "react-toastify";

const alertsService = new DetectionAlertsService();

function AlertDetailsPage() {

  const {uuid} = useParams();

  const [selectedOrganization, selectedTenant] = useSelectedTenant();

  const [alert, setAlert] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [isDeleted, setIsDeleted] = useState(false);

  const [revision, setRevision] = useState(0);
  const [actionsRevision, setActionsRevision] = useState(0);

  usePageTitle(alert ? `Alert: ${alert.details}` : "Alert Details");

  // The node does not find an alert of a tenant other than the selected one.
  useEffect(() => {
    let active = true;

    setAlert(null);
    setNotFound(false);
    alertsService.findAlert(uuid, (found) => {
      if (active) {
        setAlert(found);
      }
    }, (error) => {
      if (!active) {
        return;
      }

      if (error.response.status === 404) {
        setNotFound(true);
      } else {
        toast.error('REST call failed. (HTTP ' + error.response.status + ')');
      }
    });

    return () => {
      active = false;
    }
  }, [uuid, revision, selectedOrganization, selectedTenant]);

  const markAsResolved = (e) => {
    e.preventDefault();

    if (!confirm("Really mark alert as resolved? It will be re-triggered if the underlying cause is not resolved.")) {
      return;
    }

    alertsService.markAlertAsResolved(uuid, () => {
      toast.success("Alert marked as resolved.")
      setRevision(oldRev => oldRev+1);
    })
  }

  const deleteAlert = (e) => {
    e.preventDefault();

    if (!confirm("Really delete alert?")) {
      return;
    }

    alertsService.deleteAlert(uuid, () => {
      toast.success("Alert deleted.")
      setIsDeleted(true);
    });
  }

  if (isDeleted) {
    return <Navigate to={ApiRoutes.ALERTS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Alert not found. <Link to={ApiRoutes.ALERTS.INDEX}>Back to Alerts</Link>
      </div>
    )
  }

  if (!alert) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-8">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.ALERTS.INDEX}>Alerts</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{alert.id}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-4">
            <span className="float-end">
              <WithPermission permission="alerts_manage">
                <a className="btn btn-secondary" href="" onClick={markAsResolved}>Mark as Resolved</a>{' '}
                <a className="btn btn-danger" href="" onClick={deleteAlert}>Delete</a>{' '}
              </WithPermission>
              <Link className="btn btn-primary" to={ApiRoutes.ALERTS.INDEX}>Back</Link>
            </span>
          </div>

          <div className="col-md-12">
            <h1>
              Alert {alert.id.substr(0, 8)}
            </h1>
          </div>
        </div>

        <div className="row mt-2">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <AlertActiveIndicator active={alert.is_active} />{' '}&nbsp;
                <strong>{alert.details}</strong>
              </div>
            </div>
          </div>
        </div>

        <AlertActions alert={alert} onActionCompleted={() => setActionsRevision((previous) => previous + 1)} />

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Alert Type</h3>

                <dl className="mb-0">
                  <dt>Detection Type</dt>
                  <dd>{alert.detection_type}</dd>
                  <dt>Subsystem</dt>
                  <dd><Subsystem subsystem={alert.subsystem} /></dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Metadata</h3>

                <dl className="mb-0">
                  <dt>Status</dt>
                  <dd><AlertStatus alert={alert} /></dd>
                  <dt>Location</dt>
                  <dd><LocationLink id={alert.location_id} name={alert.location_name} /></dd>
                  <dt>Affected Device</dt>
                  <dd>
                    {!alert.affected_address && !alert.affected_mac ? <span className="text-muted">n/a</span> : null}
                    {alert.affected_address ?
                        <L4Address address={alert.affected_address} hidePort={true} withAssetName /> : null}
                    {alert.affected_address && alert.affected_mac ? <br /> : null}
                    {alert.affected_mac ?
                        <EthernetMacAddress addressWithContext={alert.affected_mac} withAssetLink withAssetName /> : null}
                  </dd>
                  <dt>Re-activations</dt>
                  <dd title="How often this alert was triggered again after it had become inactive.">
                    {numeral(alert.reactivation_count).format("0,0")}
                  </dd>
                  <dt>First Seen</dt>
                  <dd>{moment(alert.created_at).format()}</dd>
                  <dt>Last Seen</dt>
                  <dd>{moment(alert.last_seen).format()}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <AlertDetails alert={alert} />

        <RelatedMonitoredNetwork key={actionsRevision} networkId={alert.dot11_monitored_network_id} alert={alert} />

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <h3>Timeline</h3>

                <AlertTimeline alertUUID={alert.id} />
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}

export default AlertDetailsPage;