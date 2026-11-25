import React from "react";
import ColumnSorting from "../../../shared/ColumnSorting";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import numeral from "numeral";
import moment from "moment/moment";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import Paginator from "../../../misc/Paginator";
import L4Address from "../../shared/L4Address";
import IPAddress from "../../shared/IPAddress";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import {NDP_FILTER_FIELDS, NDP_MESSAGE_TYPES} from "./NDPFilterFields";

export default function NDPMessagesTable({messages, page, setPage, perPage, setFilters,
                                           orderColumn, setOrderColumn, orderDirection, setOrderDirection}) {

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={NDP_FILTER_FIELDS} field={field} value={value} />
  }

  const flags = (m) => {
    if (m.message_type !== "NEIGHBOR_ADVERTISEMENT") {
      return <span className="text-muted">n/a</span>
    }

    const set = [];
    if (m.router) set.push("Router");
    if (m.solicited) set.push("Solicited");
    if (m.overrides) set.push("Override");

    return set.length === 0 ? <span className="text-muted">None</span> : set.join(", ");
  }

  if (!messages) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (messages.messages.length === 0) {
    return <div className="mb-0 alert alert-info">No NDP messages were observed during the selected time range.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(messages.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th>Last Seen {columnSorting("last_seen")}</th>
            <th>Message {columnSorting("message_type")}</th>
            <th>Source MAC {columnSorting("source_mac")}</th>
            <th>Source Address {columnSorting("source_address")}</th>
            <th>Target Address {columnSorting("target_address")}</th>
            <th>Flags</th>
            <th>Hop Limit {columnSorting("hop_limit")}</th>
            <th>Packets {columnSorting("packet_count")}</th>
          </tr>
          </thead>
          <tbody>
          {messages.messages.map((m, i) => {
            return (
                <tr key={i}>
                  <td title={moment(m.last_seen).format()}>{moment(m.last_seen).fromNow()}</td>
                  <td>
                    {NDP_MESSAGE_TYPES[m.message_type] || m.message_type}{' '}
                    {filterIcon("message_type", m.message_type)}
                  </td>
                  <td>
                    {m.source.mac
                        ? <EthernetMacAddress addressWithContext={m.source.mac}
                                              filterElement={filterIcon("source_mac", m.source.mac.address)}
                                              withAssetName withAssetLink />
                        : <span className="text-muted">n/a</span>}
                  </td>
                  <td>
                    <L4Address address={m.source} filterElement={filterIcon("source_address", m.source.address)} hideFlag />
                  </td>
                  <td>
                    {m.target
                        ? <L4Address address={m.target}
                                     filterElement={filterIcon("target_address", m.target.address)}
                                     hideFlag />
                        : <span title="This message has no target. It was sent to this address."><IPAddress ip={m.destination_address} /></span>}
                  </td>
                  <td>{flags(m)}</td>
                  <td>
                    {m.hop_limit === 255
                        ? m.hop_limit
                        : <span className="text-danger" title="Not sent from the local link.">{m.hop_limit}</span>}
                  </td>
                  <td>{numeral(m.packet_count).format("0,0")}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={messages.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  );

}
