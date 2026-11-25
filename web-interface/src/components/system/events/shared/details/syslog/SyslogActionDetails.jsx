import React from "react";
import {addressWithPort} from "../../../../../../util/Tools";

export default function SyslogActionDetails(props) {

  const action = props.action;

  const config = action.configuration;

  const translateProtocol = (protocol) => {
    switch (protocol) {
      case "UDP_RFC5424": return "UDP Syslog (RFC 5424)";
      default: return protocol;
    }
  }

  return (
      <React.Fragment>
        <dl className="mb-0">
          <dt>Protocol</dt>
          <dd>{translateProtocol(config.protocol)}</dd>
          <dt>Syslog Hostname:</dt>
          <dd>{config.syslog_hostname}</dd>
          <dt>Destination</dt>
          <dd><span className="machine-data">{addressWithPort(config.host, config.port)}</span></dd>
        </dl>
      </React.Fragment>
  )

}