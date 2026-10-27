import {Link} from "react-router-dom";
import React from 'react'
import numeral from 'numeral'
import {byteAverageToMbit, sanitizeHtml} from "../../../../util/Tools";
import ApiRoutes from "../../../../util/ApiRoutes";

function ChannelRow (props) {
  const c = props.channel
  const tap = props.tap;
  const busName = props.busName;

  return (
    <tr>
      <td>{c.name}</td>
      <td>
        <Link to={ApiRoutes.SYSTEM.TAPS.METRICDETAILS(tap.uuid, "gauge", "channels." + sanitizeHtml(busName.toLowerCase()) + ".") + sanitizeHtml(c.name.toLowerCase()) + ".usage_percent"}>
          {numeral(c.watermark).format()} / {numeral(c.capacity).format()}
        </Link>
      </td>
      <td>
        <Link to={ApiRoutes.SYSTEM.TAPS.METRICDETAILS(tap.uuid, "gauge", "channels." + sanitizeHtml(busName.toLowerCase()) + ".") + sanitizeHtml(c.name.toLowerCase()) + ".throughput_messages"}>
          {numeral(c.throughput_messages.average / 10).format('0,0')} messages/sec
        </Link>
      </td>
      <td>
        <Link to={ApiRoutes.SYSTEM.TAPS.METRICDETAILS(tap.uuid, "gauge", "channels." + sanitizeHtml(busName.toLowerCase()) + ".") + sanitizeHtml(c.name.toLowerCase()) + ".throughput_bytes"}>
          {numeral(c.throughput_bytes.average / 10).format('0,0b')}/sec
          ({byteAverageToMbit(c.throughput_bytes.average)})
        </Link>
      </td>
      <td>
        <Link to={ApiRoutes.SYSTEM.TAPS.METRICDETAILS(tap.uuid, "gauge", "channels." + sanitizeHtml(busName.toLowerCase()) + ".") + sanitizeHtml(c.name.toLowerCase()) + ".errors"}>
          {numeral(c.errors.average / 10).format('0,0')} errors/sec
          ({numeral(c.errors.total).format()} since last restart)
        </Link>
      </td>
    </tr>
)
}

export default ChannelRow
