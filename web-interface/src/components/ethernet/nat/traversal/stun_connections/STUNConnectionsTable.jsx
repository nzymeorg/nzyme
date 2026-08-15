import React, {useContext, useEffect, useState} from "react";
import NATService from "../../../../../services/ethernet/NATService";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import {TapContext} from "../../../../../App";
import ColumnSorting from "../../../../shared/ColumnSorting";
import FilterValueIcon from "../../../../shared/filtering/FilterValueIcon";
import GenericWidgetLoadingSpinner from "../../../../widgets/GenericWidgetLoadingSpinner";
import {STUN_CONNECTIONS_FILTER_FIELDS} from "./STUNConnectionsFilterFields";
import numeral from "numeral";
import STUNDiscoveriesStatus from "../stun_discoveries/STUNDiscoveriesStatus";
import AutomaticL4SessionLink from "../../../shared/AutomaticL4SessionLink";
import InternalAddressOnlyWrapper from "../../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../../shared/context/macs/EthernetMacAddress";
import L4Address from "../../../shared/L4Address";
import {STUN_DISCOVERY_FILTER_FIELDS} from "../stun_discoveries/STUNDiscoveriesFilterFields";
import L4AddressList from "../../../shared/L4AddressList";
import moment from "moment/moment";
import Paginator from "../../../../misc/Paginator";
import FullCopyShortenedId from "../../../../shared/FullCopyShortenedId";

const natService = new NATService();

export default function STUNConnectionsTable({timeRange, filters, setFilters, revision, perPage}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [orderColumn, setOrderColumn] = useState("first_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [data, setData] = useState(null);

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const perPageSel = perPage ? perPage : 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setData(null);
    natService.findAllSTUNConnections(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, selectedTaps, perPageSel, (page-1)*perPageSel, setData);
  }, [organizationId, tenantId, selectedTaps, timeRange, filters, orderColumn, orderDirection, page, revision]);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const macFilter = (address, fieldName) => {
    if (!address) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={STUN_CONNECTIONS_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  if (!data) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (data.total === 0) {
    return <div className="mb-0 alert alert-info">No NAT connection attempts were observed during selected time range.</div>
  }

  return (
    <React.Fragment>
      <strong>Total:</strong> {numeral(data.total).format("0,0")}

      <table className="table table-sm table-hover table-striped mb-4 mt-3">
        <thead>
        <tr>
          <th>ID</th>
          <th>Source MAC {columnSorting("source_mac")}</th>
          <th>Source Address {columnSorting("source_address")}</th>
          <th>Destination MAC {columnSorting("destination_mac")}</th>
          <th>Destination Address {columnSorting("destination_address")}</th>
          <th title="Mapped Addresses">Mapped</th>
          <th title="Peer Addresses">Peer</th>
          <th title="Relayed Addresses">Relayed</th>
          <th>Initiated At {columnSorting("first_seen")}</th>
          <th>Last Activity {columnSorting("last_activity")}</th>
        </tr>
        </thead>
        <tbody>
        {data.negotiations.map((n, i) => {
          return (
            <tr key={i}>
              <td>
                <a href="#">
                  <FullCopyShortenedId value={n.negotiation_key_sha256} />
                </a>
              </td>
              <td>
                <InternalAddressOnlyWrapper
                  address={n.source}
                  inner={n.source ? <EthernetMacAddress addressWithContext={n.source.mac}
                                                        filterElement={macFilter(n.source.mac, "source_mac")}
                                                        withAssetLink withAssetName /> : null} />
              </td>
              <td>
                <L4Address address={n.source}
                           hidePort={true}
                           filterElement={n.source ? <FilterValueIcon setFilters={setFilters}
                                                                      fields={STUN_CONNECTIONS_FILTER_FIELDS}
                                                                      field="source_address"
                                                                      value={n.source.address} /> : null } />
              </td>
              <td>
                <InternalAddressOnlyWrapper
                  address={n.destination}
                  inner={n.destination ? <EthernetMacAddress addressWithContext={n.destination.mac}
                                                        filterElement={macFilter(n.destination.mac, "destination_mac")}
                                                        withAssetLink withAssetName /> : null} />
              </td>
              <td>
                <L4Address address={n.destination}
                           hidePort={true}
                           filterElement={n.destination ? <FilterValueIcon setFilters={setFilters}
                                                                           fields={STUN_CONNECTIONS_FILTER_FIELDS}
                                                                           field="destination_address"
                                                                           value={n.destination.address} /> : null } />
              </td>
              <td>{numeral(n.mapped_addresses.length).format("0,0")}</td>
              <td>{numeral(n.peer_addresses.length).format("0,0")}</td>
              <td>{numeral(n.relayed_addresses.length).format("0,0")}</td>
              <td title={moment(n.first_seen).fromNow()}>
                {moment(n.first_seen).format()}
              </td>
              <td title={moment(n.last_activity).format()}>
                {moment(n.last_activity).fromNow()}
              </td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={data.total} perPage={perPageSel} setPage={setPage} page={page} />
    </React.Fragment>
  )

}