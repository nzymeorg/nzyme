import React from "react";

const IPINFO = <a href="https://ipinfo.io" target="_blank" rel="noreferrer">IPinfo</a>;

const geoIp = (provided) => {
  if (provided.includes("geoip_ipinfostandard")) {
    return {
      description: "City, coordinates, country and ASN of IP addresses.",
      source: <span>{IPINFO} Standard</span>,
      provided: true
    }
  }

  if (provided.includes("geoip_ipinfofree")) {
    return {
      description: "Country and ASN of IP addresses.",
      source: <span>{IPINFO} Free</span>,
      provided: true
    }
  }

  return {
    description: "Geographic location and ASN of IP addresses.",
    source: IPINFO,
    provided: provided.some((type) => type.startsWith("geoip_"))
  }
}

const dataTypes = (provided) => [
  {
    name: "MAC Address Vendors",
    description: "Vendor names of MAC addresses.",
    source: "IEEE",
    provided: provided.includes("ouis")
  },
  {
    name: "Bluetooth",
    description: "Vendor and service names of Bluetooth devices.",
    source: "Bluetooth SIG",
    provided: provided.includes("bluetooth")
  },
  {name: "GeoIP", ...geoIp(provided)},
  {
    name: "UAV",
    description: "Make, model and classification of unmanned aerial vehicles.",
    source: "Nzyme",
    provided: provided.includes("uav")
  },
  {
    name: "Environment",
    description: "Weather conditions and weather alerts of locations in the United States.",
    source: "U.S. National Weather Service",
    provided: provided.includes("environment")
  }
];

export default function ProvidedData({status}) {

  if (status.connection_summary === "disabled") {
    return <p className="mb-0">No data is provided because Nzyme Connect is disabled.</p>
  }

  if (status.connection_summary === "never_connected") {
    return <p className="mb-0">The provided data is known after the first connection to Nzyme Connect.</p>
  }

  return (
    <React.Fragment>
      <p>
        Nzyme Connect provides the following data to this cluster. Data types are enabled and disabled in your
        account at <a href="https://connect.nzyme.org/" target="_blank" rel="noreferrer">https://connect.nzyme.org/</a>.
      </p>

      {status.connection_summary === "fail" ? <p>
        This information may be outdated because the cluster is currently not connected to Nzyme Connect.
      </p> : null}

      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Data Type</th>
          <th>Description</th>
          <th>Source</th>
          <th>Status</th>
        </tr>
        </thead>
        <tbody>
        {dataTypes(status.provided_services || []).map((type) => (
          <tr key={type.name}>
            <td>{type.name}</td>
            <td>{type.description}</td>
            <td>{type.source}</td>
            <td>
              {type.provided
                ? <span className="text-success">Provided</span>
                : <span className="text-muted">Disabled in Connect</span>}
            </td>
          </tr>
        ))}
        </tbody>
      </table>
    </React.Fragment>
  )

}
