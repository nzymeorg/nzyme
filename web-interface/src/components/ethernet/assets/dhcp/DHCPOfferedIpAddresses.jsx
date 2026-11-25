import React from "react";
import IPAddress from "../../shared/IPAddress";

export default function DHCPOfferedIpAddresses(props) {

  const ips = props.ips;

  if (ips == null || ips.length === 0) {
    return <span className="text-muted">n/a</span>
  }

  return (
      <React.Fragment>
        {ips.map((ip, i) => {
          return (
              <span key={i}><IPAddress ip={ip} full={true} />{i < ips.length-1 ? ", " : null}</span>
          )
        })}
      </React.Fragment>
  )

}