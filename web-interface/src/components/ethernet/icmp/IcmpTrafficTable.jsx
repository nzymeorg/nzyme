import React from "react";
import numeral from "numeral";
import moment from "moment";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import L4Address from "../shared/L4Address";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {ICMP_FILTER_FIELDS} from "./IcmpFilterFields";

export default function IcmpTrafficTable({packets, page, setPage, perPage, orderColumn, setOrderColumn,
                                          orderDirection, setOrderDirection, setFilters}) {

  if (!packets) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={ICMP_FILTER_FIELDS} field={field} value={value} />
  }

  if (packets.packets.length === 0) {
    return <div className="alert alert-info mb-0">No ICMP traffic was observed during the selected time range.</div>
  }

  const address = (data, plain, field) => {
    if (data) {
      return <L4Address address={data} hidePort withAssetName filterElement={filterIcon(field, plain)} />
    }

    return <React.Fragment><span className="machine-data">{plain}</span> {filterIcon(field, plain)}</React.Fragment>
  }

  const details = (p) => {
    if (p.icmp_type === 5 && p.redirect_gateway) {
      return <span>Use gateway <span className="machine-data">{p.redirect_gateway}</span>
        {p.quoted_destination_address ? <span> for <span className="machine-data">{p.quoted_destination_address}</span></span> : null}</span>
    }

    if (p.is_error && p.quoted_destination_address) {
      return <span>
        {p.quoted_protocol === 6 ? "TCP" : p.quoted_protocol === 17 ? "UDP" : p.quoted_protocol === 1 ? "ICMP" : "protocol " + p.quoted_protocol}{' '}
        <span className="machine-data">{p.quoted_source_address}{p.quoted_source_port ? ":" + p.quoted_source_port : ""}</span>
        {' '}to{' '}
        <span className="machine-data">{p.quoted_destination_address}{p.quoted_destination_port ? ":" + p.quoted_destination_port : ""}</span>
      </span>
    }

    if (p.is_error) {
      return <span className="text-muted">No quoted packet</span>
    }

    return <span className="text-muted">n/a</span>
  }

  return (
    <React.Fragment>
      <p className="mb-2 mt-0">
        <strong>Total:</strong> {numeral(packets.total).format("0,0")}
      </p>

      <table className="table table-sm table-hover table-striped mb-4 mt-3">
        <thead>
        <tr>
          <th>Last Seen {columnSorting("last_seen")}</th>
          <th>Type {columnSorting("icmp_type")}</th>
          <th>Source {columnSorting("source_address")}</th>
          <th>Destination {columnSorting("destination_address")}</th>
          <th>Packets {columnSorting("packet_count")}</th>
          <th>Details</th>
        </tr>
        </thead>
        <tbody>
        {packets.packets.map((p, i) => {
          return (
            <tr key={i}>
              <td title={moment(p.last_seen).format()}>{moment(p.last_seen).fromNow()}</td>
              <td>
                <span>{p.icmp_type_name}</span>
                {p.icmp_code !== 0 ? <span className="text-muted"> (code {p.icmp_code})</span> : null}{' '}
                {filterIcon("icmp_type", p.icmp_type)}
              </td>
              <td>{address(p.source_address_data, p.source_address, "source_address")}</td>
              <td>{address(p.destination_address_data, p.destination_address, "destination_address")}</td>
              <td>{numeral(p.packet_count).format("0,0")}</td>
              <td>{details(p)}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={packets.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
