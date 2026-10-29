import {Link} from "react-router-dom";
import React from "react";

import moment from "moment";
import numeral from "numeral";
import Subsystem from "../misc/Subsystem";
import ApiRoutes from "../../util/ApiRoutes";
import RenderConditionally from "../misc/RenderConditionally";
import AlertStatus from "./AlertStatus";
import AlertSourceLink from "./AlertSourceLink";
import L4Address from "../ethernet/shared/L4Address";
import EthernetMacAddress from "../shared/context/macs/EthernetMacAddress";
import FilterValueIcon from "../shared/filtering/FilterValueIcon";
import {ALERT_FILTER_FIELDS} from "./AlertFilterFields";

function AlertsTableRow(props) {

  const alert = props.alert;
  const onSelect = props.onSelect;
  const isSelected = props.isSelected;
  const hideControls = props.hideControls ? props.hideControls : false;

  // Optional. Rows rendered outside the alerts page have no filters to add to.
  const setFilters = props.setFilters;

  const filter = (field, value) => {
    if (!setFilters) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters} fields={ALERT_FILTER_FIELDS} field={field} value={value} />
  }

  const affected = () => {
    if (!alert.affected_address && !alert.affected_mac) {
      return <span className="text-muted">n/a</span>
    }

    return (
        <React.Fragment>
          {alert.affected_address ?
              <L4Address address={alert.affected_address} hidePort={true} withAssetName
                         filterElement={filter("affected_address", alert.affected_address.address)} /> : null}
          {alert.affected_address && alert.affected_mac ? <br /> : null}
          {alert.affected_mac ?
              <EthernetMacAddress addressWithContext={alert.affected_mac} withAssetLink withAssetName
                                  filterElement={filter("affected_mac", alert.affected_mac.address)} /> : null}
        </React.Fragment>
    )
  }

  const status = alert.status ? alert.status : (alert.is_active ? "Active" : "Inactive");

  return (
      <tr>
        <RenderConditionally render={!hideControls}>
          <td>
            <input className="form-check-input"
                   type="checkbox"
                   checked={isSelected}
                   onChange={() => onSelect(alert.id)} />
          </td>
        </RenderConditionally>
        <td className="text-nowrap"><AlertStatus alert={alert} /> {filter("status", status)}</td>
        <td><Link to={ApiRoutes.ALERTS.DETAILS(alert.id)}>{alert.details}</Link></td>
        <td>{alert.detection_type} {filter("type", alert.detection_type)}</td>
        <td><Subsystem subsystem={alert.subsystem} /> {filter("subsystem", alert.subsystem)}</td>
        <td>{affected()}</td>
        <td>
          {alert.reactivation_count === undefined || alert.reactivation_count === null
              ? <span className="text-muted">n/a</span>
              : <span title="How often this alert was triggered again after it had become inactive.">
                  {numeral(alert.reactivation_count).format("0,0")}
                </span>}
        </td>
        <td title={moment(alert.created_at).format()}>{moment(alert.created_at).fromNow()}</td>
        <td title={moment(alert.last_seen).format()}>{moment(alert.last_seen).fromNow()}</td>
        <td><AlertSourceLink alert={alert} /></td>
      </tr>
  )

}

export default AlertsTableRow;
