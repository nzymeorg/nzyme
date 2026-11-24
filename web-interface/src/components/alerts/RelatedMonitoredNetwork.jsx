import React, {useContext, useEffect, useState} from "react";
import RelatedMonitoredNetworkData from "./RelatedMonitoredNetworkData";
import Dot11Service from "../../services/Dot11Service";
import MonitoredNetworkAlertStatusTable from "../dot11/monitoring/MonitoredNetworkAlertStatusTable";
import {UserContext} from "../../App";
import {userHasPermission} from "../../util/Tools";
import {toast} from "react-toastify";

const dot11Service = new Dot11Service();

function RelatedMonitoredNetwork(props) {

  const networkId = props.networkId;
  const alert = props.alert;

  const user = useContext(UserContext);

  const [ssid, setSSID] = useState(null);
  const [ssidDeleted, setSSIDDeleted] = useState(false); // In case the monitored network doesn't exist.

  useEffect(() => {
    if (userHasPermission(user, "dot11_monitoring_manage") && networkId) {
      dot11Service.findMonitoredSSID(networkId, setSSID, function() {
        /* noop */
      }, function(error) {
        if (error.response.status === 404) {
          setSSIDDeleted(true);
        } else {
          toast.error('REST call failed. (HTTP ' + error.response.status + ')')
        }
      });
    }
  }, [networkId]);

  if (!networkId) {
    return null;
  }

  if (!userHasPermission(user, "dot11_monitoring_manage")) {
    return (
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Related Network Monitor Data</h3>

                <div className="alert alert-info mb-0">
                  Your user cannot access network monitor configuration.
                </div>
              </div>
            </div>
          </div>
        </div>
    )
  }

  if (ssidDeleted) {
    return (
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Related Network Monitor Data</h3>

                <div className="alert alert-info mb-0">
                  Monitored network has been deleted since alert was raised.
                </div>
              </div>
            </div>
          </div>
        </div>
    )
  }

  return (
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Related Network Monitor Data</h3>

              <RelatedMonitoredNetworkData ssid={ssid} />

              {alert.is_active ? <MonitoredNetworkAlertStatusTable ssid={ssid} renderControls={false} />
                  : "Real-time network monitoring information is only displayed for currently active alerts." }
            </div>
          </div>
        </div>
      </div>
  )

}

export default RelatedMonitoredNetwork;