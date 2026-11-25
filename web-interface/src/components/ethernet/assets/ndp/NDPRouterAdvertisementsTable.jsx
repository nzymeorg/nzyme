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
import {NDP_ROUTER_ADVERTISEMENT_FILTER_FIELDS} from "./NDPFilterFields";

const PREFERENCES = {HIGH: "High", MEDIUM: "Medium", LOW: "Low"};

export default function NDPRouterAdvertisementsTable({routerAdvertisements, page, setPage, perPage, setFilters,
                                                       orderColumn, setOrderColumn, orderDirection,
                                                       setOrderDirection}) {

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters}
                            fields={NDP_ROUTER_ADVERTISEMENT_FILTER_FIELDS}
                            field={field}
                            value={value} />
  }

  const list = (entries, render) => {
    if (!entries || entries.length === 0) {
      return <span className="text-muted">None</span>
    }

    return entries.map((entry, i) => <div key={i}>{render(entry)}</div>);
  }

  const addressConfiguration = (ra) => {
    if (ra.managed) {
      return "DHCPv6";
    }

    return ra.other_configuration ? "SLAAC, other settings from DHCPv6" : "SLAAC";
  }

  if (!routerAdvertisements) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (routerAdvertisements.router_advertisements.length === 0) {
    return (
        <div className="mb-0 alert alert-info">
          No router advertisements were observed during the selected time range.
        </div>
    )
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(routerAdvertisements.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th>Last Seen {columnSorting("last_seen")}</th>
            <th>Router MAC {columnSorting("source_mac")}</th>
            <th>Router Address {columnSorting("source_address")}</th>
            <th>Prefixes</th>
            <th>DNS Servers</th>
            <th>Addresses From</th>
            <th>Preference {columnSorting("preference")}</th>
            <th>Lifetime {columnSorting("router_lifetime")}</th>
            <th>Hop Limit {columnSorting("hop_limit")}</th>
            <th>Packets {columnSorting("packet_count")}</th>
          </tr>
          </thead>
          <tbody>
          {routerAdvertisements.router_advertisements.map((ra, i) => {
            return (
                <tr key={i}>
                  <td title={moment(ra.last_seen).format()}>{moment(ra.last_seen).fromNow()}</td>
                  <td>
                    {ra.router.mac
                        ? <EthernetMacAddress addressWithContext={ra.router.mac}
                                              filterElement={filterIcon("source_mac", ra.router.mac.address)}
                                              withAssetName withAssetLink />
                        : <span className="text-muted">n/a</span>}
                  </td>
                  <td>
                    <L4Address address={ra.router}
                               filterElement={filterIcon("source_address", ra.router.address)}
                               hideFlag />
                  </td>
                  <td>
                    {list(ra.prefixes, (p) => (
                        <span className="machine-data" title={p.autonomous ? "Hosts form addresses from it." : null}>
                          {p.prefix}/{p.prefix_length}
                        </span>
                    ))}
                  </td>
                  <td>{list(ra.dns_servers, (server) => <IPAddress ip={server} />)}</td>
                  <td>{addressConfiguration(ra)}</td>
                  <td>{PREFERENCES[ra.preference] || ra.preference} {filterIcon("preference", ra.preference)}</td>
                  <td>
                    {ra.router_lifetime === 0
                        ? <span title="Not a default router.">0 s</span>
                        : numeral(ra.router_lifetime).format("0,0") + " s"}
                  </td>
                  <td>
                    {ra.hop_limit === 255
                        ? ra.hop_limit
                        : <span className="text-danger" title="Not sent from the local link.">{ra.hop_limit}</span>}
                  </td>
                  <td>{numeral(ra.packet_count).format("0,0")}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={routerAdvertisements.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  );

}
