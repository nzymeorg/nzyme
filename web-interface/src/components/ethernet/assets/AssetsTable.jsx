import React from "react";
import numeral from "numeral";
import ColumnSorting from "../../shared/ColumnSorting";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import moment from "moment";
import AssetHostnames from "./AssetHostnames";
import AssetIpAddresses from "./AssetIpAddresses";
import Paginator from "../../misc/Paginator";
import {truncate} from "../../../util/Tools";
import ApiRoutes from "../../../util/ApiRoutes";
import AssetActiveIndicator from "./AssetActiveIndicator";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {ASSET_FILTER_FIELDS} from "./AssetFilterFields";

export default function AssetsTable(props) {

  const assets = props.assets;
  const setFilters = props.setFilters;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;
  const orderColumn = props.orderColumn;
  const setOrderColumn = props.setOrderColumn;
  const orderDirection = props.orderDirection;
  const setOrderDirection = props.setOrderDirection;

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  if (assets === null) {
    return <GenericWidgetLoadingSpinner height={1000} />
  }

  if (assets.assets.length === 0) {
    return <div className="alert alert-info mb-0">No Ethernet Assets discovered yet.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(assets.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th style={{width: 170}}>MAC Address {columnSorting("mac")}</th>
            <th>OUI</th>
            <th>Active</th>
            <th>Name</th>
            <th>Hostname</th>
            <th>IP Address</th>
            <th>First Seen {columnSorting("first_seen")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
          </tr>
          </thead>
          <tbody>
          {assets.assets.map((a, i) => {
            return (
                <tr key={i}>
                  <td>
                    <EthernetMacAddress addressWithContext={a.mac}
                                        href={ApiRoutes.ETHERNET.ASSETS.DETAILS(a.uuid)}
                                        hideActiveIndicator={true} />
                  </td>
                  <td>{a.oui ? truncate(a.oui, 30, false) : <span className="text-muted">Unknown</span>}</td>
                  <td><AssetActiveIndicator active={a.is_active} /></td>
                  <td>{a.name ? <span className="context-name">{a.name}</span> : <span className="text-muted">None</span>}</td>
                  <td>
                    <AssetHostnames hostnames={a.hostnames}
                                    filterElement={a.hostnames && a.hostnames.length > 0 ?
                                      <FilterValueIcon setFilters={setFilters}
                                                       fields={ASSET_FILTER_FIELDS}
                                                       field="hostname"
                                                       value={a.hostnames[0]} />
                                      : null } />
                  </td>
                  <td>
                    <AssetIpAddresses addresses={a.ip_addresses}
                                      filterElement={a.ip_addresses && a.ip_addresses.length > 0 ?
                                        <FilterValueIcon setFilters={setFilters}
                                                         fields={ASSET_FILTER_FIELDS}
                                                         field="ip_address"
                                                         value={a.ip_addresses[0]} />
                                        : null } />
                  </td>
                  <td title={moment(a.first_seen).format()}>{moment(a.first_seen).fromNow()}</td>
                  <td title={moment(a.last_seen).format()}>{moment(a.last_seen).fromNow()}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={assets.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}