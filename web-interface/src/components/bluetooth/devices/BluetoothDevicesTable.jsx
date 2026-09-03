import React, {useContext, useEffect, useState} from 'react';
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import SignalStrength from "../../shared/SignalStrength";
import {TapContext} from "../../../App";
import GroupedParameterList from "../../shared/GroupedParameterList";
import BluetoothMacAddress from "../../shared/context/macs/BluetoothMacAddress";
import ApiRoutes from "../../../util/ApiRoutes";
import {transformTag, transformTransport} from "../BluetoothTools";
import ColumnSorting from "../../shared/ColumnSorting";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import BluetoothService from "../../../services/BluetoothService";
import numeral from "numeral";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {NTP_FILTER_FIELDS} from "../../ethernet/time/ntp/NTPFilterFields";
import {BLUETOOTH_DEVICES_FILTER_FIELDS} from "../BluetoothDevicesFilterFields";

const btService = new BluetoothService();

export default function BluetoothDevicesTable({timeRange, filters, setFilters}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [devices, setDevices] = useState(null);

  const [orderColumn, setOrderColumn] = useState("average_rssi");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [page, setPage] = useState(1);
  const perPage = 50;

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setDevices(null);
    btService.findAllDevices(
      setDevices,
      organizationId,
      tenantId,
      timeRange,
      filters,
      orderColumn,
      orderDirection,
      selectedTaps,
      perPage,
      (page-1)*perPage);
  }, [selectedTaps, timeRange, filters, organizationId, tenantId, orderColumn, orderDirection, page])

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  if (!devices) {
    return <LoadingSpinner />
  }

  if (devices.count === 0) {
    return (
        <div className="alert alert-info mb-2">
          No Bluetooth devices recorded in selected time frame.
        </div>
    )
  }

  return (
      <React.Fragment>
        <div>
          <strong>Total: </strong> {numeral(devices.total).format(0,0)}
        </div>

        <table className="table table-sm table-hover table-striped mt-2">
          <thead>
          <tr>
            <th>Address {columnSorting("mac")}</th>
            <th>OUI</th>
            <th>Manufacturer</th>
            <th>Signal Strength {columnSorting("average_rssi")}</th>
            <th>Type {columnSorting("tags")}</th>
            <th>Transport {columnSorting("transports")}</th>
            <th>Name {columnSorting("names")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
          </tr>
          </thead>
          <tbody>
          {devices.devices.map((d, i) => {
            return (
                <tr key={i}>
                  <td>
                    <BluetoothMacAddress addressWithContext={d.mac}
                                         filterElement={d.mac && d.mac.address ? <FilterValueIcon setFilters={setFilters}
                                                                                                  fields={BLUETOOTH_DEVICES_FILTER_FIELDS}
                                                                                                  field="mac"
                                                                                                  value={d.mac.address} /> : null}
                                         href={ApiRoutes.BLUETOOTH.DEVICES.DETAILS((d.mac.address))} />
                  </td>
                  <td>
                    <GroupedParameterList list={d.ouis}
                                          setFilters={setFilters}
                                          fieldName="ouis"
                                          fields={BLUETOOTH_DEVICES_FILTER_FIELDS} />
                  </td>
                  <td>
                    <GroupedParameterList list={d.companies}
                                          setFilters={setFilters}
                                          fieldName="manufacturer_names"
                                          fields={BLUETOOTH_DEVICES_FILTER_FIELDS} />
                  </td>
                  <td><SignalStrength strength={d.average_rssi} selectedTapCount={selectedTaps.length}/></td>
                  <td>
                    <GroupedParameterList list={d.tags}
                                          valueTransform={transformTag}
                                          setFilters={setFilters}
                                          fieldName="tags"
                                          fields={BLUETOOTH_DEVICES_FILTER_FIELDS} />
                  </td>
                  <td>
                    <GroupedParameterList list={d.transports}
                                          valueTransform={transformTransport}
                                          setFilters={setFilters}
                                          fieldName="transports"
                                          fields={BLUETOOTH_DEVICES_FILTER_FIELDS} />
                  </td>
                  <td>
                    <GroupedParameterList list={d.names}
                                          setFilters={setFilters}
                                          fieldName="names"
                                          fields={BLUETOOTH_DEVICES_FILTER_FIELDS} />
                  </td>
                  <td>{moment(d.last_seen).fromNow()}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={devices.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}