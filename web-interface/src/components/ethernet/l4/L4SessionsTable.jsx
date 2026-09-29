import React, {useContext, useEffect, useState} from "react";
import ColumnSorting from "../../shared/ColumnSorting";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import numeral from "numeral";
import Paginator from "../../misc/Paginator";
import moment from "moment";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import InternalAddressOnlyWrapper from "../shared/InternalAddressOnlyWrapper";
import L4SessionState from "./L4SessionState";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {L4_SESSIONS_FILTER_FIELDS} from "./L4SessionFilterFields";
import ApiRoutes from "../../../util/ApiRoutes";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import L4Service from "../../../services/ethernet/L4Service";
import {formatDurationMs} from "../../../util/Tools";
import FullCopy from "../../shared/FullCopy";
import L4SessionTags from "./L4SessionTags";

const l4Service = new L4Service();

export default function L4SessionsTable(props) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;
  const [organizationId, tenantId] = useSelectedTenant();

  const filters = props.filters;
  const setFilters = props.setFilters;
  const revision = props.revision;
  const timeRange = props.timeRange;

  const [sessions, setSessions] = useState(null);

  const [orderColumn, setOrderColumn] = useState("most_recent_segment_time");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const perPage = props.perPage ? props.perPage : 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setSessions(null);
    l4Service.findAllSessions(
      organizationId,
      tenantId,
      selectedTaps,
      filters,
      timeRange,
      orderColumn,
      orderDirection,
      perPage,
      (page-1)*perPage,
      setSessions
    )
  }, [organizationId, tenantId, selectedTaps, filters, timeRange, page, perPage, orderColumn, orderDirection, revision]);

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
                            fields={L4_SESSIONS_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  const sessionLink = (sessionId, startTime, type) => {
    switch (type) {
      case "TCP":
        return <a href={ApiRoutes.ETHERNET.L4.TCP.SESSION_DETAILS(sessionId, startTime)} className="machine-data">
          <FullCopyShortenedId value={sessionId} />
        </a>
      case "UDP":
        return <a href={ApiRoutes.ETHERNET.L4.UDP.SESSION_DETAILS(sessionId, startTime)} className="machine-data">
          <FullCopyShortenedId value={sessionId} />
        </a>
    }
  }

  if (!sessions) {
    return <GenericWidgetLoadingSpinner height={700} />
  }

  if (sessions.sessions.length === 0) {
    return <div className="mb-0 alert alert-info">No sessions were observed during selected time range.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(sessions.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th>Session {columnSorting("session_key")}</th>
            <th>State {columnSorting("state")}</th>
            <th>Type {columnSorting("l4_type")}</th>
            <th>Source MAC {columnSorting("source_mac")}</th>
            <th>Source Address {columnSorting("source_address")}</th>
            <th>Destination MAC {columnSorting("destination_mac")}</th>
            <th>Destination Address {columnSorting("destination_address")}</th>
            <th>Tags</th>
            <th className="hide-narrow">Fingerprint {columnSorting("fingerprint")}</th>
            <th>RX {columnSorting("bytes_rx_count")}</th>
            <th>TX {columnSorting("bytes_tx_count")}</th>
            <th>Duration {columnSorting("duration")}</th>
            <th>Last Activity {columnSorting("most_recent_segment_time")}</th>
          </tr>
          </thead>
          <tbody>
          {sessions.sessions.map((s, i) => {
            return (
                <tr key={i}>
                  <td>
                    {sessionLink(s.session_key, s.start_time, s.l4_type)}

                    <FilterValueIcon setFilters={setFilters}
                                     fields={L4_SESSIONS_FILTER_FIELDS}
                                     field="session_key"
                                     value={s.session_key} />
                  </td>
                  <td>
                    <L4SessionState state={s.state} />
                    <FilterValueIcon setFilters={setFilters}
                                     fields={L4_SESSIONS_FILTER_FIELDS}
                                     field="state"
                                     value={s.state} />
                  </td>
                  <td>
                    {s.l4_type}
                    <FilterValueIcon setFilters={setFilters}
                                     fields={L4_SESSIONS_FILTER_FIELDS}
                                     field="l4_type"
                                     value={s.l4_type} />
                  </td>
                  <td>
                    <InternalAddressOnlyWrapper
                        address={s.source}
                        inner={<EthernetMacAddress
                            filterElement={macFilter(s.source.mac, "source_mac")}
                            addressWithContext={s.source.mac}
                            assetId={s.source && s.source.asset_id ? s.source.asset_id : null}
                            withAssetLink withAssetName />} />
                  </td>
                  <td>
                    <L4Address address={s.source}
                               filterElement={<FilterValueIcon setFilters={setFilters}
                                                               fields={L4_SESSIONS_FILTER_FIELDS}
                                                               field="source_address"
                                                               value={s.source.address} />}/>
                  </td>
                  <td>
                    <InternalAddressOnlyWrapper
                        address={s.destination}
                        inner={<EthernetMacAddress
                            filterElement={macFilter(s.destination.mac, "destination_mac")}
                            addressWithContext={s.destination.mac}
                            withAssetLink withAssetName/>} />
                  </td>
                  <td>
                    <L4Address address={s.destination}
                               filterElement={<FilterValueIcon setFilters={setFilters}
                                                               fields={L4_SESSIONS_FILTER_FIELDS}
                                                               field="destination_address"
                                                               value={s.destination.address} />}/>
                  </td>
                  <td><L4SessionTags tags={s.tags} setFilters={setFilters} /></td>
                  <td className="hide-narrow">
                    <FullCopy shortValue={s.fingerprint ? <span className="machine-data">{s.fingerprint.substr(0,8)}</span> : null} fullValue={s.fingerprint} />

                    <FilterValueIcon setFilters={setFilters}
                                     fields={L4_SESSIONS_FILTER_FIELDS}
                                     field="fingerprint"
                                     value={s.fingerprint} />
                  </td>
                  <td>
                    {numeral(s.bytes_rx_count).format("0b")}
                    <FilterValueIcon setFilters={setFilters}
                                     fields={L4_SESSIONS_FILTER_FIELDS}
                                     field="bytes_rx_count"
                                     value={s.bytes_rx_count} />
                  </td>
                  <td>
                    {numeral(s.bytes_tx_count).format("0b")}
                    <FilterValueIcon setFilters={setFilters}
                                     fields={L4_SESSIONS_FILTER_FIELDS}
                                     field="bytes_tx_count"
                                     value={s.bytes_tx_count} />
                  </td>
                  <td>
                    <FullCopy shortValue={formatDurationMs(s.duration_ms)} fullValue={s.duration_ms} />

                    <FilterValueIcon setFilters={setFilters}
                                     fields={L4_SESSIONS_FILTER_FIELDS}
                                     field="duration"
                                     value={s.duration_ms} />
                  </td>
                  <td title={moment(s.most_recent_segment_time).fromNow()}>
                    {moment(s.most_recent_segment_time).format()}
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={sessions.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )
}