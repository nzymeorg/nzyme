import {Link} from "react-router-dom";
import React from 'react'
import numeral from 'numeral'
import ApiRoutes from "../../../../util/ApiRoutes";
import {sanitizeHtml} from "../../../../util/Tools";

function CaptureRow (props) {
  const c = props.capture
  const tap = props.tap;

  const captureType = (capture_type) => {
    switch (capture_type) {
      case "WiFi": return "WiFi Acquisition";
      case "WiFiEngagement": return "WiFi Engagement";
      default: return capture_type;
    }
  }

  return (
    <tr>
      <td>{c.interface_name}</td>
      <td>{captureType(c.capture_type)}</td>
      <td>
        <Link to={ApiRoutes.SYSTEM.TAPS.METRICDETAILS(tap.uuid, "gauge", "captures." + sanitizeHtml(c.interface_name) + ".received")}>
         {numeral(c.received).format('0,0')}
       </Link>
      </td>
      <td>
        <Link to={ApiRoutes.SYSTEM.TAPS.METRICDETAILS(tap.uuid, "gauge", "captures." + sanitizeHtml(c.interface_name) + ".dropped_if")}>
          {numeral(c.dropped_interface).format('0,0')}
        </Link>
      </td>
      <td>
        <Link to={ApiRoutes.SYSTEM.TAPS.METRICDETAILS(tap.uuid, "gauge", "captures." + sanitizeHtml(c.interface_name) + ".dropped_buffer")}>
          {numeral(c.dropped_buffer).format('0,0')}
        </Link>
      </td>
    </tr>
  )
}

export default CaptureRow
