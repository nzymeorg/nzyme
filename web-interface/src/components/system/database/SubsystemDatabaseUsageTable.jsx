import React from "react";

import LoadingSpinner from "../../misc/LoadingSpinner";
import Subsystem from "../../misc/Subsystem";
import {formatBytes, formatRows} from "./DatabaseUsageStatus";

const ORDER = ["DOT11", "ETHERNET", "BLUETOOTH", "UAV", "GENERIC", "NZYME_SYS"];

export default function SubsystemDatabaseUsageTable({sizes}) {

  if (!sizes) {
    return <LoadingSpinner />
  }

  const subsystems = ORDER.filter((s) => sizes[s]);

  return (
    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        <th>Subsystem</th>
        <th>Data Size</th>
        <th>Rows</th>
      </tr>
      </thead>
      <tbody>
      {subsystems.map((subsystem) => {
        return (
          <tr key={subsystem}>
            <td><Subsystem subsystem={subsystem} /></td>
            <td>{formatBytes(sizes[subsystem])}</td>
            <td>{formatRows(sizes[subsystem])}</td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
