import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ColumnSorting from "../../../shared/ColumnSorting";
import numeral from "numeral";
import RTSPService from "../../../../services/ethernet/RTSPService";

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
          <th>State</th>
        </tr>
        </thead>
        <tbody>
        {data.streams.map((stream, i) => {
          return (
            <tr key={i}>
              <td>{stream.state}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={data.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}