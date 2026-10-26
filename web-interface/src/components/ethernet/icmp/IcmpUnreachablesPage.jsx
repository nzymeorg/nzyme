import React, {useContext, useEffect, useState} from "react";
import {useLocation} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";
import IcmpService from "../../../services/ethernet/IcmpService";
import {ICMP_MENU_ITEMS} from "./IcmpMenuItems";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import Filters from "../../shared/filtering/Filters";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import {ICMP_UNREACHABLES_FILTER_FIELDS} from "./IcmpUnreachablesFilterFields";
import IcmpUnreachablesHistogram from "./IcmpUnreachablesHistogram";
import IcmpUnreachableBreakdownHistogram from "./IcmpUnreachableBreakdownHistogram";
import IcmpUnreachableHostPortHistogram from "./IcmpUnreachableHostPortHistogram";
import {Presets} from "../../shared/timerange/TimeRange";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";

const icmpService = new IcmpService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function IcmpUnreachablesPage() {

  usePageTitle("ICMP Unreachable Ports");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;
  const urlQuery = useQuery();

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), ICMP_UNREACHABLES_FILTER_FIELDS)
  );
  const [unreachables, setUnreachables] = useState(null);
  const [orderColumn, setOrderColumn] = useState("packet_count");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [page, setPage] = useState(1);
  const perPage = 25;
  const [revision, setRevision] = useState(new Date());

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);

  const applyTimeRange = (newTimeRange) => {
    setPage(1);
    setTimeRange(newTimeRange);
  }
  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);
    return () => { disableTapSelector(tapContext); }
  }, [tapContext]);

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
  }

  useEffect(() => {
    setUnreachables(null);
    icmpService.getUnreachables(organizationId, tenantId, filters, selectedTaps, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setUnreachables);
  }, [organizationId, tenantId, filters, selectedTaps, timeRange, orderColumn, orderDirection, page, revision]);

  useEffect(() => {
    setHistogram(null);
    icmpService.getUnreachablesHistogram(organizationId, tenantId, filters, selectedTaps, timeRange, setHistogram);
  }, [organizationId, tenantId, filters, selectedTaps, timeRange, revision]);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={(c) => { setPage(1); setOrderColumn(c); }}
                          orderDirection={orderDirection}
                          setOrderDirection={(d) => { setPage(1); setOrderDirection(d); }} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={applyFilters} fields={ICMP_UNREACHABLES_FILTER_FIELDS} field={field} value={value} />
  }

  const address = (data, plain, field) => {
    return data
      ? <L4Address address={data} hidePort withAssetName filterElement={filterIcon(field, plain)} />
      : <React.Fragment><span className="machine-data">{plain}</span> {filterIcon(field, plain)}</React.Fragment>
  }

  const table = () => {
    if (!unreachables) {
      return <GenericWidgetLoadingSpinner height={150} />
    }

    if (unreachables.unreachables.length === 0) {
      return <div className="alert alert-info mb-0">No port unreachables in the selected time range.</div>
    }

    return (
      <React.Fragment>
        <p className="mb-2 mt-0"><strong>Total:</strong> {numeral(unreachables.total).format("0,0")}</p>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Host {columnSorting("host")}</th>
            <th>Host MAC</th>
            <th>Source {columnSorting("source")}</th>
            <th>Protocol {columnSorting("protocol")}</th>
            <th>Port {columnSorting("port")}</th>
            <th>Packets {columnSorting("packet_count")}</th>
            <th>First Seen {columnSorting("first_seen")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
          </tr>
          </thead>
          <tbody>
          {unreachables.unreachables.map((u, i) => {
            return (
              <tr key={i}>
                <td>{address(u.host_address_data, u.host_address, "host")}</td>
                <td>{u.host_mac
                  ? <EthernetMacAddress addressWithContext={u.host_mac} withAssetLink withAssetName />
                  : <span className="text-muted">n/a</span>}</td>
                <td>{address(u.source_address_data, u.source_address, "source")}</td>
                <td>
                  {u.protocol_name ? u.protocol_name : <span className="text-muted">n/a</span>}
                  {u.protocol !== null && u.protocol !== undefined ? <span> {filterIcon("protocol", String(u.protocol))}</span> : null}
                </td>
                <td className="machine-data">
                  {u.port}{u.service ? <span className="text-muted"> ({u.service})</span> : null}{' '}
                  {filterIcon("port", String(u.port))}
                </td>
                <td>{numeral(u.packet_count).format("0,0")}</td>
                <td title={moment(u.first_seen).format()}>{moment(u.first_seen).fromNow()}</td>
                <td title={moment(u.last_seen).format()}>{moment(u.last_seen).fromNow()}</td>
              </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={unreachables.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
    )
  }

  const breakdown = (by, title) => {
    return (
      <div className="col-md-6">
        <div className="card">
          <div className="card-body">
            <CardTitleWithControls title={title} />
            <IcmpUnreachableBreakdownHistogram by={by} columnTitle={title} filters={filters}
                                               setFilters={applyFilters} taps={selectedTaps}
                                               timeRange={timeRange} revision={revision} />
          </div>
        </div>
      </div>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ICMP_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.ICMP.UNREACHABLES} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-icmp-unreachables" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true} />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={ICMP_UNREACHABLES_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Port Unreachables Over Time"
                                     refreshAction={() => setRevision(new Date())} />

              <IcmpUnreachablesHistogram histogram={histogram}
                                         timeRange={timeRange}
                                         setTimeRange={applyTimeRange} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Top Host and Port" />
              <IcmpUnreachableHostPortHistogram filters={filters} setFilters={applyFilters} taps={selectedTaps}
                                                timeRange={timeRange} revision={revision} />
            </div>
          </div>
        </div>
        {breakdown("PORT", "Top Ports")}
      </div>

      <div className="row mt-3">
        {breakdown("HOST", "Top Hosts")}
        {breakdown("SOURCE", "Top Sources")}
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Unreachable Ports"
                                     refreshAction={() => setRevision(new Date())} />

              {table()}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
