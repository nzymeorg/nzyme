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
import Filters from "../../shared/filtering/Filters";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import {ICMP_UNREACHABLE_HOSTS_FILTER_FIELDS} from "./IcmpUnreachableHostsFilterFields";
import IcmpUnreachableHostsHistogram from "./IcmpUnreachableHostsHistogram";
import IcmpUnreachableHostBreakdownHistogram from "./IcmpUnreachableHostBreakdownHistogram";
import {Presets} from "../../shared/timerange/TimeRange";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";

const icmpService = new IcmpService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function IcmpUnreachableHostsPage() {

  usePageTitle("ICMP Unreachable Hosts");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;
  const urlQuery = useQuery();

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), ICMP_UNREACHABLE_HOSTS_FILTER_FIELDS)
  );
  const [hosts, setHosts] = useState(null);
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
    setHosts(null);
    icmpService.getUnreachableHosts(organizationId, tenantId, filters, selectedTaps, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setHosts);
  }, [organizationId, tenantId, filters, selectedTaps, timeRange, orderColumn, orderDirection, page, revision]);

  useEffect(() => {
    setHistogram(null);
    icmpService.getUnreachableHostsHistogram(organizationId, tenantId, filters, selectedTaps, timeRange, setHistogram);
  }, [organizationId, tenantId, filters, selectedTaps, timeRange, revision]);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={(c) => { setPage(1); setOrderColumn(c); }}
                          orderDirection={orderDirection}
                          setOrderDirection={(d) => { setPage(1); setOrderDirection(d); }} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={applyFilters} fields={ICMP_UNREACHABLE_HOSTS_FILTER_FIELDS} field={field} value={value} />
  }

  const address = (data, plain, field) => {
    return data
      ? <L4Address address={data} hidePort withAssetName filterElement={filterIcon(field, plain)} />
      : <React.Fragment><span className="machine-data">{plain}</span> {filterIcon(field, plain)}</React.Fragment>
  }

  const table = () => {
    if (!hosts) {
      return <GenericWidgetLoadingSpinner height={150} />
    }

    if (hosts.hosts.length === 0) {
      return <div className="alert alert-info mb-0">No host unreachables in the selected time range.</div>
    }

    return (
      <React.Fragment>
        <p className="mb-2 mt-0"><strong>Total:</strong> {numeral(hosts.total).format("0,0")}</p>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Source {columnSorting("source")}</th>
            <th>Unreachable Host {columnSorting("unreachable_host")}</th>
            <th>Protocol {columnSorting("protocol")}</th>
            <th>Reason {columnSorting("reason")}</th>
            <th>Reporting Router</th>
            <th>Packets {columnSorting("packet_count")}</th>
            <th>First Seen {columnSorting("first_seen")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
          </tr>
          </thead>
          <tbody>
          {hosts.hosts.map((u, i) => {
            return (
              <tr key={i}>
                <td>{address(u.source_address_data, u.source_address, "source")}</td>
                <td>{address(u.unreachable_host_data, u.unreachable_host, "unreachable_host")}</td>
                <td>
                  {u.protocol_name ? u.protocol_name : <span className="text-muted">n/a</span>}
                  {u.protocol !== null && u.protocol !== undefined ? <span> {filterIcon("protocol", String(u.protocol))}</span> : null}
                </td>
                <td>{u.reason}</td>
                <td>{address(u.reporting_router_data, u.reporting_router, "reporting_router")}</td>
                <td>{numeral(u.packet_count).format("0,0")}</td>
                <td title={moment(u.first_seen).format()}>{moment(u.first_seen).fromNow()}</td>
                <td title={moment(u.last_seen).format()}>{moment(u.last_seen).fromNow()}</td>
              </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={hosts.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
    )
  }

  const breakdown = (by, title) => {
    return (
      <div className="col-md-6">
        <div className="card">
          <div className="card-body">
            <CardTitleWithControls title={title} />
            <IcmpUnreachableHostBreakdownHistogram by={by} columnTitle={title} filters={filters}
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
          <SectionMenuBar items={ICMP_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.ICMP.UNREACHABLE_HOSTS} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-icmp-unreachable-hosts" className="btn btn-secondary float-end">Help</a>
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
                       fields={ICMP_UNREACHABLE_HOSTS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Host Unreachables Over Time"
                                     refreshAction={() => setRevision(new Date())} />

              <IcmpUnreachableHostsHistogram histogram={histogram}
                                             timeRange={timeRange}
                                             setTimeRange={applyTimeRange} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        {breakdown("UNREACHABLE_HOST", "Top Unreachable Hosts")}
        {breakdown("SOURCE", "Top Sources")}
      </div>

      <div className="row mt-3">
        {breakdown("PROTOCOL", "Protocol")}
        {breakdown("ROUTER", "Top Reporting Routers")}
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Unreachable Hosts"
                                     refreshAction={() => setRevision(new Date())} />

              {table()}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
