import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ColumnSorting from "../../../shared/ColumnSorting";
import numeral from "numeral";
import RTSPService from "../../../../services/ethernet/RTSPService";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import moment from "moment";
import L4Address from "../../shared/L4Address";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import {RTSP_FILTER_FIELDS} from "./RTSPFilterFields";
import RTSPStreamActiveIndicator from "./RTSPStreamActiveIndicator";
import {formatDurationMs} from "../../../../util/Tools";
import FullCopy from "../../../shared/FullCopy";
import ApiRoutes from "../../../../util/ApiRoutes";

const rtspService = new RTSPService();

export default function RTSPStreamsTable(props) {

  const [organizationId, tenantId] = useSelectedTenant();

  const timeRange = props.timeRange;
  const filters = props.filters;
  const setFilters = props.setFilters;
  const revision = props.revision;

  const [orderColumn, setOrderColumn] = useState("setup_established_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [data, setData] = useState(null);

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const perPage = props.perPage ? props.perPage : 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setData(null);
    rtspService.findAllStreams(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, selectedTaps, perPage, (page-1)*perPage, setData);
  }, [organizationId, tenantId, selectedTaps, timeRange, filters, orderColumn, orderDirection, page, revision]);

  const macFilter = (address, fieldName) => {
    if (!address) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={RTSP_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  if (!data) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (data.streams.length === 0) {
    return <div className="mb-0 alert alert-info">No RTSP streams were observed during selected time range.</div>
  }

  return (
    <React.Fragment>
      <strong>Total:</strong> {numeral(data.total).format("0,0")}

      <table className="table table-sm table-hover table-striped mb-4 mt-3">
        <thead>
        <tr>
          <th>&nbsp;</th>
          <th>ID</th>
          <th>Type {columnSorting("type")}</th>
          <th>Client Address {columnSorting("setup_source_address")}</th>
          <th>Client MAC {columnSorting("setup_source_mac")}</th>
          <th>Server Address {columnSorting("setup_destination_address")}</th>
          <th>Server MAC {columnSorting("setup_destination_mac")}</th>
          <th className="hide-narrow">RX {columnSorting("stream_bytes_rx")}</th>
          <th className="hide-narrow">TX {columnSorting("stream_bytes_tx")}</th>
          <th>Duration {columnSorting("duration")}</th>
          <th>Established At {columnSorting("setup_established_at")}</th>
          <th className="hide-narrow">Last Activity {columnSorting("setup_most_recent_segment_time")}</th>
        </tr>
        </thead>
        <tbody>
        {data.streams.map((stream, i) => {
          return (
            <tr key={i}>
              <td style={{width: 25}}>
                <RTSPStreamActiveIndicator stream={stream} />
              </td>
              <td>
                <a href={ApiRoutes.ETHERNET.STREAMS.RTSP.DETAILS(stream.setup_tcp_session_key)}>
                  <FullCopyShortenedId value={stream.setup_tcp_session_key} />
                </a>
              </td>
              <td>
                {stream.stream_l4_type}

                <FilterValueIcon setFilters={setFilters}
                                 fields={RTSP_FILTER_FIELDS}
                                 field="type"
                                 value={stream.stream_l4_type} />
              </td>
              <td>
                <L4Address address={stream.stream_source}
                           hidePort={true}
                           filterElement={stream.stream_source ? <FilterValueIcon setFilters={setFilters}
                                                                            fields={RTSP_FILTER_FIELDS}
                                                                            field="stream_source_address"
                                                                            value={stream.stream_source.address} /> : null } />
              </td>
              <td>
                <InternalAddressOnlyWrapper
                    address={stream.stream_source}
                    inner={stream.stream_source ? <EthernetMacAddress addressWithContext={stream.stream_source.mac}
                                                                filterElement={macFilter(stream.stream_source.mac, "stream_source_mac")}
                                                                withAssetLink withAssetName /> : null} />
              </td>
              <td>
                <L4Address address={stream.stream_destination}
                           hidePort={true}
                           filterElement={stream.stream_destination ? <FilterValueIcon setFilters={setFilters}
                                                                                  fields={RTSP_FILTER_FIELDS}
                                                                                  field="stream_destination_address"
                                                                                  value={stream.stream_destination.address} /> : null } />
              </td>
              <td>
                <InternalAddressOnlyWrapper
                    address={stream.stream_destination}
                    inner={stream.stream_destination ? <EthernetMacAddress addressWithContext={stream.stream_destination.mac}
                                                                      filterElement={macFilter(stream.stream_destination.mac, "stream_destination_mac")}
                                                                      withAssetLink withAssetName /> : null} />
              </td>
              <td className="hide-narrow">
                {numeral(stream.stream_bytes_rx).format("0b")}

                <FilterValueIcon setFilters={setFilters}
                                 fields={RTSP_FILTER_FIELDS}
                                 field="bytes_rx_count"
                                 value={stream.stream_bytes_rx} />
              </td>
              <td className="hide-narrow">
                {numeral(stream.stream_bytes_tx).format("0b")}

                <FilterValueIcon setFilters={setFilters}
                                 fields={RTSP_FILTER_FIELDS}
                                 field="bytes_tx_count"
                                 value={stream.stream_bytes_tx} />
              </td>
              <td>
                <FullCopy shortValue={formatDurationMs(stream.duration_ms)} fullValue={stream.duration_ms} />

                <FilterValueIcon setFilters={setFilters}
                                 fields={RTSP_FILTER_FIELDS}
                                 field="duration_ms"
                                 value={stream.duration_ms} />
              </td>
              <td title={moment(stream.setup_established_at).format()}>{moment(stream.setup_established_at).fromNow()}</td>
              <td className="hide-narrow"
                  title={stream.last_activity ? moment(stream.last_activity).format() : "Underlying connection data may be missing."}>
                {stream.last_activity ? moment(stream.last_activity).fromNow() : "Unknown"}
              </td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={data.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}