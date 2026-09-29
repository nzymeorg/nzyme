import React, {useState} from "react";
import ColumnSorting from "../../../shared/ColumnSorting";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import numeral from "numeral";
import moment from "moment/moment";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import Paginator from "../../../misc/Paginator";
import AssetName from "../../shared/AssetName";
import L4Address from "../../shared/L4Address";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import {ARP_FILTER_FIELDS} from "./ARPFilterFields";
import {marked} from "marked";
import createDOMPurify from "dompurify";

export default function ARPPacketsTable(props) {

  const packets = props.packets;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;
  const setFilters = props.setFilters;
  const setOrderColumn = props.setOrderColumn;
  const orderColumn = props.orderColumn;
  const setOrderDirection = props.setOrderDirection;
  const orderDirection = props.orderDirection;

  const [expandedRow, setExpandedRow] = useState(null);
  const DOMPurify = createDOMPurify(window);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const description = (i, p) => {
    if (expandedRow !== i) {
      return;
    }

    return <tr><td colSpan={8}>
      <div className="arp-description" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(marked.parse(p.description)) }} />
    </td></tr>
  }

  if (!packets) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (packets.packets.length === 0) {
    return <div className="mb-0 alert alert-info">No ARP packets were observed during selected time range.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(packets.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th style={{width: 200}}>Timestamp {columnSorting("timestamp")}</th>
            <th>ARP Operation {columnSorting("operation")}</th>
            <th>Sender MAC {columnSorting("arp_sender_mac")}</th>
            <th>Sender Address {columnSorting("arp_sender_address")}</th>
            <th>Target MAC {columnSorting("arp_target_mac")}</th>
            <th>Target Address {columnSorting("arp_target_address")}</th>
          </tr>
          </thead>
          <tbody>
          {packets.packets.map((p, i) => {
            return (
                <React.Fragment key={i}>
                  <tr>
                    <td>
                      <a href="#" onClick={(e) => {e.preventDefault(); setExpandedRow(i);}}>
                        {moment(p.timestamp).format()}
                      </a>
                    </td>
                    <td>
                      {p.operation}
                      <FilterValueIcon setFilters={setFilters}
                                       fields={ARP_FILTER_FIELDS}
                                       field="operation"
                                       value={p.operation} />
                    </td>
                    <td>
                      <EthernetMacAddress addressWithContext={p.arp_sender.mac}
                                          filterElement={<FilterValueIcon setFilters={setFilters}
                                                                          fields={ARP_FILTER_FIELDS}
                                                                          field="arp_sender_mac"
                                                                          value={p.arp_sender.mac.address} />}
                                          withAssetName withAssetLink />
                    </td>
                    <td>
                      <L4Address address={p.arp_sender}
                                 filterElement={<FilterValueIcon setFilters={setFilters}
                                                                 fields={ARP_FILTER_FIELDS}
                                                                 field="arp_sender_address"
                                                                 value={p.arp_sender.address} />}
                                 hideFlag />
                    </td>
                    <td>
                      <EthernetMacAddress addressWithContext={p.arp_target.mac}
                                          filterElement={<FilterValueIcon setFilters={setFilters}
                                                                          fields={ARP_FILTER_FIELDS}
                                                                          field="arp_target_mac"
                                                                          value={p.arp_target.mac.address} />}
                                          withAssetName withAssetLink />
                    </td>
                    <td>
                      <L4Address address={p.arp_target}
                                 filterElement={<FilterValueIcon setFilters={setFilters}
                                                                 fields={ARP_FILTER_FIELDS}
                                                                 field="arp_target_address"
                                                                 value={p.arp_target.address} />}
                                 hideFlag />
                    </td>
                  </tr>
                  {description(i, p)}
                </React.Fragment>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={packets.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  );

}