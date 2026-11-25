import React, {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import L4Service from "../../../../services/ethernet/L4Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";

const l4Service = new L4Service();

export default function L4TapSettings({organizationId, tenantId, revision}) {

  const [settings, setSettings] = useState(null);

  useEffect(() => {
    setSettings(null);
    l4Service.getTapSettings(organizationId, tenantId, setSettings);
  }, [organizationId, tenantId, revision]);

  if (!settings) {
    return <LoadingSpinner />
  }

  if (settings.taps_different.length === 0) {
    return <div className="alert alert-info mb-0">All taps have the same values as the settings.</div>
  }

  return (
    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        <th>Tap</th>
        <th>Tap Configuration</th>
      </tr>
      </thead>
      <tbody>
      {settings.taps_different.map((tap) => (
        <tr key={tap.uuid}>
          <td><Link to={ApiRoutes.SYSTEM.TAPS.DETAILS(tap.uuid)}>{tap.name}</Link></td>
          <td>
            {tap.cgnat_is_internal_differs
              ? <div><code>cgnat_is_internal = {String(tap.cgnat_is_internal)}</code></div> : null}
            {tap.internal_networks_differ
              ? <div><code>internal_networks = {JSON.stringify(tap.internal_networks)}</code></div> : null}
          </td>
        </tr>
      ))}
      </tbody>
    </table>
  )

}
