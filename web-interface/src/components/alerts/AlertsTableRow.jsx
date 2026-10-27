import {Link} from "react-router-dom";
import React from "react";

import moment from "moment";
import Subsystem from "../misc/Subsystem";
import AlertActiveIndicator from "./AlertActiveIndicator";
import ApiRoutes from "../../util/ApiRoutes";
import RenderConditionally from "../misc/RenderConditionally";

function AlertsTableRow(props) {

  const alert = props.alert;
  const onSelect = props.onSelect;
  const isSelected = props.isSelected;
  const hideControls = props.hideControls ? props.hideControls : false;

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
        <td>
          <AlertActiveIndicator active={alert.is_active} />
        </td>
        <td><Link to={ApiRoutes.ALERTS.DETAILS(alert.id)}>{alert.details}</Link></td>
        <td>{alert.detection_type}</td>
        <td><Subsystem subsystem={alert.subsystem} /></td>
        <td>{moment(alert.created_at).format()}</td>
        <td>{moment(alert.last_seen).format()}</td>
      </tr>
  )

}

export default AlertsTableRow;