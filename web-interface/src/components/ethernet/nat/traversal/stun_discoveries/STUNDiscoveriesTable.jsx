import React, {useContext, useEffect, useState} from "react";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import {TapContext} from "../../../../../App";
import ColumnSorting from "../../../../shared/ColumnSorting";
import GenericWidgetLoadingSpinner from "../../../../widgets/GenericWidgetLoadingSpinner";
import NATService from "../../../../../services/ethernet/NATService";
import numeral from "numeral";
import Paginator from "../../../../misc/Paginator";
import moment from "moment";
import L4AddressList from "../../../shared/L4AddressList";
import L4Address from "../../../shared/L4Address";
import FilterValueIcon from "../../../../shared/filtering/FilterValueIcon";
import {STUN_DISCOVERY_FILTER_FIELDS} from "./STUNDiscoveriesFilterFields";
import InternalAddressOnlyWrapper from "../../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../../shared/context/macs/EthernetMacAddress";
import STUNDiscoveriesStatus from "./STUNDiscoveriesStatus";
import AutomaticL4SessionLink from "../../../shared/AutomaticL4SessionLink";

const natService = new NATService();

export default function STUNDiscoveriesTable({timeRange, filters, setFilters, revision, perPage}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [orderColumn, setOrderColumn] = useState("initiated_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [data, setData] = useState(null);

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const perPageSel = perPage ? perPage : 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setData(null);
    natService.findAllSTUNDiscoveries(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, selectedTaps, perPageSel, (page-1)*perPageSel, setData);
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
                            fields={STUN_DISCOVERY_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  if (!data) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (data.total === 0) {
    return <div className="mb-0 alert alert-info">No NAT discovery attempts were observed during selected time range.</div>
  }


  return (
    <React.Fragment>
      <strong>Total:</strong> {numeral(data.total).format("0,0")}

      <table className="table table-sm table-hover table-striped mb-4 mt-3">
        <thead>
        <tr>
          <th>{columnSorting("status")}</th>
          <th>ID</th>
          <th>Source MAC {columnSorting("source_mac")}</th>
          <th>Source Address {columnSorting("source_address")}</th>
          <th>Destination {columnSorting("destination_address")}</th>
          <th>Mapped Addresses {columnSorting("mapped_addresses")}</th>
          <th>Initiated At {columnSorting("initiated_at")}</th>
        </tr>
        </thead>
        <tbody>
        {data.discoveries.map((d, i) => {
          return (
            <tr key={i}>
              <td><STUNDiscoveriesStatus status={d.status} /></td>
              <td>
                <AutomaticL4SessionLink l4Type={d.transport} sessionId={d.session_key} startTime={d.first_seen} />
              </td>
              <td>
                <InternalAddressOnlyWrapper
                  address={d.source}
                  inner={d.source ? <EthernetMacAddress addressWithContext={d.source.mac}
                                                              filterElement={macFilter(d.source.mac, "source_mac")}
                                                              withAssetLink withAssetName /> : null} />
              </td>
              <td>
                <L4Address address={d.source}
                           hidePort={true}
                           filterElement={d.source ? <FilterValueIcon setFilters={setFilters}
                                                                      fields={STUN_DISCOVERY_FILTER_FIELDS}
                                                                      field="source_address"
                                                                      value={d.source.address} /> : null } />
              </td>
              <td>
                <L4Address address={d.destination}
                           hidePort={true}
                           filterElement={d.destination ? <FilterValueIcon setFilters={setFilters}
                                                                           fields={STUN_DISCOVERY_FILTER_FIELDS}
                                                                           field="destination_address"
                                                                           value={d.destination.address} /> : null } />
              </td>
              <td>
                <L4AddressList addresses={d.mapped_addresses}
                               setFilters={setFilters}
                               fields={STUN_DISCOVERY_FILTER_FIELDS}
                               field="mapped_address" />
              </td>
              <td title={moment(d.first_seen).fromNow()}>
                {moment(d.first_seen).format()}
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