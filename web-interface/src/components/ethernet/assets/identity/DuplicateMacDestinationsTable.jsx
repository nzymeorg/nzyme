import React, {useState} from "react";
import numeral from "numeral";
import L4Address from "../../shared/L4Address";
import ColumnSorting from "../../../shared/ColumnSorting";
import PivotLink from "../../../shared/PivotLink";
import ApiRoutes from "../../../../util/ApiRoutes";
import {filteredLink, macFilter} from "../details/AssetProfileHelpers";

// Sorted here: the finding stores at most the 25 busiest destinations per stack.
export default function DuplicateMacDestinationsTable({destinations, total, mac, fingerprint}) {

  const sessionsLink = (d) => filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, {
    ...macFilter("source_mac", mac),
    fingerprint: [{field: "fingerprint", operator: "equals", value: fingerprint}],
    destination_address: [{field: "destination_address", operator: "equals", value: d.address.address}],
    destination_port: [{field: "destination_port", operator: "equals_numeric", value: String(d.address.port)}]
  });

  const [orderColumn, setOrderColumn] = useState("sessions");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const valueOf = (d) => {
    switch (orderColumn) {
      case "address": return d.address.address;
      case "port": return d.address.port;
      case "bytes": return d.bytes;
      default: return d.sessions;
    }
  }

  const rows = [...destinations].sort((a, b) => {
    const x = valueOf(a);
    const y = valueOf(b);
    const result = typeof x === "string" ? x.localeCompare(y) : x - y;
    return orderDirection === "ASC" ? result : -result;
  });

  if (rows.length === 0) {
    return <span className="text-muted">None</span>
  }

  return (
    <React.Fragment>
      {total > rows.length ?
        <p className="text-muted mb-2">
          The {numeral(rows.length).format("0,0")} busiest of {numeral(total).format("0,0")} destinations, ranked by
          sessions. The others are not stored on the finding.
        </p> : null}

    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        <th>Address {columnSorting("address")}</th>
        <th>Port {columnSorting("port")}</th>
        <th>Sessions {columnSorting("sessions")}</th>
        <th>Bytes {columnSorting("bytes")}</th>
      </tr>
      </thead>
      <tbody>
      {rows.map((d) => {
        return (
          <tr key={d.address.address + ":" + d.address.port}>
            <td><L4Address address={d.address} hidePort /></td>
            <td className="machine-data">{d.address.port}</td>
            <td>
              {numeral(d.sessions).format("0,0")}
              <PivotLink to={sessionsLink(d)} title="Sessions to this destination" className="ms-2 small" icon={true} />
            </td>
            <td title={numeral(d.bytes).format("0,0") + " bytes"}>{numeral(d.bytes).format("0b")}</td>
          </tr>
        )
      })}
      </tbody>
    </table>
    </React.Fragment>
  )

}
