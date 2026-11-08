import React, {useState} from "react";
import moment from "moment";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ColumnSorting from "../../../shared/ColumnSorting";
import {dimensionTitle, formatCount} from "./AssetProfileHelpers";
import TcpFingerprint from "../identity/TcpFingerprint";

const IDENTITY_DIMENSIONS = [
  "DHCP_VENDOR_CLASS", "DHCP_FINGERPRINT", "DHCP_PARAMETER_REQUEST_LIST", "DHCP_CLIENT_IDENTIFIER",
  "TCP_STACK", "TCP_FINGERPRINT", "TCP_SIGNATURE", "TCP_FINGERPRINT_FORWARDED", "TCP_SERVER_FINGERPRINT",
  "TCP_SERVER_SIGNATURE",
  "SOURCE_PORT_RANGE", "TCP_TIMESTAMP_RATE", "IP_ID_BEHAVIOR", "JA4", "TLS_VERSION", "NTP_CLIENT_VERSION"
];

const isTcpStackDimension = (dimension) => dimension.startsWith("TCP_") && dimension !== "TCP_TIMESTAMP_RATE";

export default function AssetIdentityCard({attributes, onRefresh}) {

  const [orderColumn, setOrderColumn] = useState("attribute");
  const [orderDirection, setOrderDirection] = useState("ASC");

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const sortedRows = () => {
    const result = [];

    IDENTITY_DIMENSIONS.forEach((dimension) => {
      (attributes.attributes[dimension] || []).forEach((v) => {
        result.push({
          dimension: dimension,
          attribute: dimensionTitle(dimension),
          value: v.value,
          count: v.count,
          first_seen: moment(v.first_seen).valueOf(),
          last_seen: moment(v.last_seen).valueOf()
        });
      });
    });

    const direction = orderDirection === "ASC" ? 1 : -1;
    result.sort((a, b) => {
      const x = a[orderColumn];
      const y = b[orderColumn];
      const cmp = typeof x === "string" ? x.localeCompare(y) : x - y;
      return (cmp !== 0 ? cmp * direction : a.attribute.localeCompare(b.attribute)) || a.value.localeCompare(b.value);
    });

    return result;
  }

  const rows = () => {
    return sortedRows().map((row) => (
      <tr key={row.attribute + "-" + row.value}>
        <td>{row.attribute}</td>
        <td>
          {isTcpStackDimension(row.dimension)
            ? <TcpFingerprint value={row.value} />
            : <span className="machine-data">{row.value}</span>}
        </td>
        <td>{formatCount(row.count)}</td>
        <td title={moment(row.first_seen).format()}>{moment(row.first_seen).fromNow()}</td>
        <td title={moment(row.last_seen).format()}>{moment(row.last_seen).fromNow()}</td>
      </tr>
    ));
  }

  const [expanded, setExpanded] = useState(false);

  const title = (
    <React.Fragment>
      <CardTitleWithControls title="Identity"
                             refreshAction={onRefresh}
                             helpLink="https://go.nzyme.org/ethernet-assets-identity-attributes" />

      <p className="text-muted">
        Fingerprints and client software presented by this asset, kept long-term. Hover over a value for the meaning
        of its fields.
      </p>
    </React.Fragment>
  );

  if (!expanded) {
    const count = attributes
      ? IDENTITY_DIMENSIONS.reduce((sum, dimension) => sum + (attributes.attributes[dimension] || []).length, 0)
      : null;

    return (
      <React.Fragment>
        {title}
        <button className="btn btn-sm btn-outline-secondary" onClick={(e) => { e.preventDefault(); setExpanded(true); }}>
          Show identity attributes{count !== null ? " (" + formatCount(count) + ")" : ""}
        </button>
      </React.Fragment>
    )
  }

  if (!attributes) {
    return (
      <React.Fragment>
        {title}
        <LoadingSpinner />
      </React.Fragment>
    )
  }

  const content = rows();

  return (
    <React.Fragment>
      {title}

      <button className="btn btn-sm btn-outline-secondary mb-3" onClick={(e) => { e.preventDefault(); setExpanded(false); }}>
        Hide identity attributes
      </button>

      {content.length === 0
        ? <div className="alert alert-info mb-0">No identity attributes recorded yet. They are collected from DHCP, TCP, TLS and NTP traffic as it is observed.</div>
        : <table className="table table-sm table-hover table-striped mb-0">
            <thead>
            <tr>
              <th>Attribute {columnSorting("attribute")}</th>
              <th>Value {columnSorting("value")}</th>
              <th>Seen {columnSorting("count")}</th>
              <th>First Seen {columnSorting("first_seen")}</th>
              <th>Last Seen {columnSorting("last_seen")}</th>
            </tr>
            </thead>
            <tbody>
            {content}
            </tbody>
          </table>}
    </React.Fragment>
  )

}
