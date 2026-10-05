import React from "react";
import TransparentContextSource from "../../shared/context/transparent/TransparentContextSource";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ColumnSorting from "../../shared/ColumnSorting";
import numeral from "numeral";
import Paginator from "../../misc/Paginator";
import WithPermission from "../../misc/WithPermission";

export default function AssetIpAddressesTable(props) {

  const addresses = props.addresses;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;
  const setOrderColumn = props.setOrderColumn;
  const orderColumn = props.orderColumn;
  const setOrderDirection = props.setOrderDirection;
  const orderDirection = props.orderDirection;
  const onDeleteIpAddress = props.onDeleteIpAddress;

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const ipAddressNetworkName = (address) => {
    if (!address || !address.context || address.context.length === 0) {
      return <span className="text-muted">n/a</span>;
    }

    for (const c of address.context) {
      if (c.name && c.name.trim().length > 0) {
        return (
          <>
          <span>
            <span className="context-name context-name-network">{c.name}</span>{' '}
            {address.context.length > 1 ? <span className="italic text-muted">[+{address.context.length-1} more]</span> : null}
          </span>
          </>
        )
      }
    }

    return <span className="text-muted">n/a</span>
  }

  if (addresses === null) {
    return <LoadingSpinner />
  }

  if (addresses.addresses.length === 0) {
    return <div className="alert alert-info mb-0">No IP addresses found.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(addresses.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Address {columnSorting("address")}</th>
            <th>Network</th>
            <th>Source {columnSorting("source")}</th>
            <th>First Seen {columnSorting("first_seen")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
            <th>&nbsp;</th>
          </tr>
          </thead>
          <tbody>
          {addresses.addresses.map((address, i) => {
            return (
                <tr key={i}>
                  <td>{address.address}</td>
                  <td>{ipAddressNetworkName(address)}</td>
                  <td><TransparentContextSource source={address.source}/></td>
                  <td title={moment(address.first_seen).format()}>
                    {moment(address.first_seen).fromNow()}
                  </td>
                  <td title={moment(address.last_seen).format()}>
                    {moment(address.last_seen).fromNow()}
                  </td>
                  <td>
                    <WithPermission permission="ethernet_assets_manage">
                      <a href="#" onClick={(e) => onDeleteIpAddress(e, address.id)}>
                        <i className="fa fa-trash-alt text-danger" title="Delete IP Address"></i>
                      </a>
                    </WithPermission>
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={addresses.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}