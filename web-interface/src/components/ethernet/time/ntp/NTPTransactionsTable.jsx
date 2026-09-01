import React, {useContext, useEffect, useState} from "react";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import numeral from "numeral";
import Paginator from "../../../misc/Paginator";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import L4Address from "../../shared/L4Address";
import moment from "moment";
import {formatSubMicro} from "../../../../util/Tools";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import {DHCP_FILTER_FIELDS} from "../../assets/dhcp/DHCPFilterFields";
import {NTP_FILTER_FIELDS} from "./NTPFilterFields";
import ApiRoutes from "../../../../util/ApiRoutes";
import TimeService from "../../../../services/ethernet/TimeService";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";

const timeService = new TimeService();

export default function NTPTransactionsTable({filters, setFilters, timeRange, revision, perPage=25 }) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [orderColumn, setOrderColumn] = useState("initiated_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [page, setPage] = useState(1);

  const [transactions, setTransactions] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setTransactions(null);

    timeService.findAllNTPTransactions(
      organizationId,
      tenantId,
      timeRange,
      filters,
      orderColumn,
      orderDirection,
      selectedTaps,
      perPage,
      (page - 1) * perPage,
      setTransactions
    );

  }, [selectedTaps, organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, page, revision]);

  if (transactions === null) {
    return <GenericWidgetLoadingSpinner height={500} />
  }

  if (transactions.total === 0) {
    return <div className="alert alert-info mb-0">No NTP transactions recorded.</div>
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  return (
    <>
      <div>
        <strong>Total: </strong> {numeral(transactions.total).format(0,0)}
      </div>

      <div className="table-responsive">
        <table className="table table-sm table-hover table-striped mt-2">
          <thead>
          <tr>
            <th>ID {columnSorting("transaction_key")}</th>
            <th>Complete {columnSorting("complete")}</th>
            <th>Client MAC {columnSorting("client_mac")}</th>
            <th>Client Address {columnSorting("client_address")}</th>
            <th>Server MAC {columnSorting("server_mac")}</th>
            <th>Server Address {columnSorting("server_address")}</th>
            <th>Stratum {columnSorting("stratum")}</th>
            <th>Clock Reference {columnSorting("reference_id")}</th>
            <th title="Round Trip Time">RTT {columnSorting("rtt_seconds")}</th>
            <th title="Server Processing Time">SPT {columnSorting("server_processing_seconds")}</th>
            <th>Root Delay {columnSorting("root_delay_seconds")}</th>
            <th title="Root Dispersion">Root Disp. {columnSorting("root_dispersion_seconds")}</th>
            <th>Initiated At {columnSorting("initiated_at")}</th>
          </tr>
          </thead>
          <tbody>
          {transactions.transactions.map((tx, i) => {
            let initiated_at = tx.timestamp_client_tap_receive ?
              tx.timestamp_client_tap_receive : tx.timestamp_server_tap_receive;

            return (
              <tr key={i}>
                <td>
                  <a href={ApiRoutes.ETHERNET.TIME.NTP.TRANSACTION_DETAILS(tx.transaction_key)} className="machine-data">
                    <FullCopyShortenedId value={tx.transaction_key} />
                  </a>

                  <FilterValueIcon setFilters={setFilters}
                                   fields={NTP_FILTER_FIELDS}
                                   field="transaction_key"
                                   value={tx.transaction_key} />
                </td>
                <td>{tx.complete ? <span className="text-success">Complete</span>
                  : <span className="text-warning">Incomplete</span>}</td>
                <td>
                  <InternalAddressOnlyWrapper
                    address={tx.client}
                    inner={<EthernetMacAddress
                      filterElement={tx.client && tx.client.mac ? <FilterValueIcon setFilters={setFilters}
                                                                  fields={NTP_FILTER_FIELDS}
                                                                  field="client_mac"
                                                                  value={tx.client.mac.address} /> : null}
                      addressWithContext={tx.client ? tx.client.mac : null}
                      assetId={tx.client && tx.client.asset_id ? tx.client.asset_id : null}
                      withAssetLink withAssetName />} />
                </td>
                <td>
                  <L4Address address={tx.client}
                             filterElement={tx.client ? <FilterValueIcon setFilters={setFilters}
                                                                         fields={NTP_FILTER_FIELDS}
                                                                         field="client_address"
                                                                         value={tx.client.address} /> : null} />
                </td>
                <td>
                  <InternalAddressOnlyWrapper
                    address={tx.server}
                    inner={<EthernetMacAddress
                      filterElement={tx.server && tx.server.mac ? <FilterValueIcon setFilters={setFilters}
                                                                                   fields={NTP_FILTER_FIELDS}
                                                                                   field="server_mac"
                                                                                   value={tx.server.mac.address} /> : null}
                      addressWithContext={tx.server ? tx.server.mac : null}
                      assetId={tx.server && tx.server.asset_id ? tx.server.asset_id : null}
                      withAssetLink withAssetName />} />
                </td>
                <td>
                  <L4Address address={tx.server}
                             filterElement={tx.server ? <FilterValueIcon setFilters={setFilters}
                                                                         fields={NTP_FILTER_FIELDS}
                                                                         field="server_address"
                                                                         value={tx.server.address} /> : null} />
                </td>
                <td>
                  {tx.stratum ? tx.stratum : <span className="text-muted">n/a</span>}
                  <FilterValueIcon setFilters={setFilters}
                                   fields={NTP_FILTER_FIELDS}
                                   field="stratum"
                                   value={tx.stratum} />
                </td>
                <td>
                  {tx.reference_id ? tx.reference_id : <span className="text-muted">n/a</span>}
                  <FilterValueIcon setFilters={setFilters}
                                   fields={NTP_FILTER_FIELDS}
                                   field="reference_id"
                                   value={tx.reference_id} />
                </td>
                <td>{tx.rtt_seconds ? <span>{formatSubMicro(tx.rtt_seconds)}</span>
                  : <span className="text-muted">n/a</span>}</td>
                <td>{tx.server_processing_seconds ? <span>{formatSubMicro(tx.server_processing_seconds)}</span>
                  : <span className="text-muted">n/a</span>}</td>
                <td>{tx.root_delay_seconds ? <span>{formatSubMicro(tx.root_delay_seconds)}</span>
                  : <span className="text-muted">n/a</span>}</td>
                <td>{tx.root_dispersion_seconds ? <span>{formatSubMicro(tx.root_dispersion_seconds)}</span>
                  : <span className="text-muted">n/a</span>}</td>
                <td title={moment(initiated_at).fromNow()}>
                  {moment(initiated_at).format()}
                </td>
              </tr>
            )
          })}
          </tbody>
        </table>
      </div>

      <Paginator itemCount={transactions.total} perPage={perPage} setPage={setPage} page={page} />
    </>
  )

}