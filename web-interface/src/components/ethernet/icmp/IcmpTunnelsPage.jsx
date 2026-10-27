import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
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
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import Filters from "../../shared/filtering/Filters";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import {ICMP_TUNNELS_FILTER_FIELDS} from "./IcmpTunnelsFilterFields";
import IcmpTunnelsHistogram from "./IcmpTunnelsHistogram";
import IcmpTunnelBreakdownHistogram from "./IcmpTunnelBreakdownHistogram";
import {Presets} from "../../shared/timerange/TimeRange";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";

const icmpService = new IcmpService();


export default function IcmpTunnelsPage() {

  usePageTitle("ICMP Tunnels");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [filters, setFilters] = useUrlFilters(ICMP_TUNNELS_FILTER_FIELDS);
  const [tunnels, setTunnels] = useState(null);
  const [orderColumn, setOrderColumn] = useState("max_payload_bytes");
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
    setTunnels(null);
    icmpService.getTunnels(organizationId, tenantId, filters, selectedTaps, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setTunnels);
  }, [organizationId, tenantId, filters, selectedTaps, timeRange, orderColumn, orderDirection, page, revision]);

  useEffect(() => {
    setHistogram(null);
    icmpService.getTunnelsHistogram(organizationId, tenantId, filters, selectedTaps, timeRange, setHistogram);
  }, [organizationId, tenantId, filters, selectedTaps, timeRange, revision]);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={(c) => { setPage(1); setOrderColumn(c); }}
                          orderDirection={orderDirection}
                          setOrderDirection={(d) => { setPage(1); setOrderDirection(d); }} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={applyFilters} fields={ICMP_TUNNELS_FILTER_FIELDS} field={field} value={value} />
  }

  const address = (data, plain, field) => {
    return data
      ? <L4Address address={data} hidePort withAssetName filterElement={filterIcon(field, plain)} />
      : <React.Fragment><span className="machine-data">{plain}</span> {filterIcon(field, plain)}</React.Fragment>
  }

  const table = () => {
    if (!tunnels) {
      return <GenericWidgetLoadingSpinner height={150} />
    }

    if (tunnels.tunnels.length === 0) {
      return <div className="alert alert-info mb-0">No suspected ICMP tunnels in the selected time range.</div>
    }

    return (
      <React.Fragment>
        <p className="mb-2 mt-0"><strong>Total:</strong> {numeral(tunnels.total).format("0,0")}</p>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>ID</th>
            <th>Source {columnSorting("source_address")}</th>
            <th>Source MAC</th>
            <th>Peer {columnSorting("destination_address")}</th>
            <th>Max Payload {columnSorting("max_payload_bytes")}</th>
            <th>Packets {columnSorting("packet_count")}</th>
            <th>First Seen {columnSorting("first_seen")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
          </tr>
          </thead>
          <tbody>
          {tunnels.tunnels.map((t) => {
            return (
              <tr key={t.uuid}>
                <td><Link to={ApiRoutes.ETHERNET.ICMP.TUNNELS.DETAILS(t.uuid)}><FullCopyShortenedId value={t.uuid} /></Link> {filterIcon("uuid", t.uuid)}</td>
                <td>{address(t.source_address_data, t.source_address, "source_address")}</td>
                <td>{t.source_mac
                  ? <React.Fragment><EthernetMacAddress addressWithContext={t.source_mac} withAssetLink withAssetName /> {filterIcon("source_mac", t.source_mac.address)}</React.Fragment>
                  : <span className="text-muted">n/a</span>}</td>
                <td>{address(t.destination_address_data, t.destination_address, "destination_address")}</td>
                <td className="machine-data">{numeral(t.max_payload_bytes).format("0,0")} B</td>
                <td>{numeral(t.packet_count).format("0,0")}</td>
                <td title={moment(t.first_seen).format()}>{moment(t.first_seen).fromNow()}</td>
                <td title={moment(t.last_seen).format()}>{moment(t.last_seen).fromNow()}</td>
              </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={tunnels.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ICMP_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-icmp-tunnels" className="btn btn-secondary float-end">Help</a>
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
                       fields={ICMP_TUNNELS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Suspected ICMP Tunnels Over Time"
                                     refreshAction={() => setRevision(new Date())} />

              <IcmpTunnelsHistogram histogram={histogram}
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
              <CardTitleWithControls title="Top Peers" />

              <IcmpTunnelBreakdownHistogram by="PEER" columnTitle="Peer" valueTitle="Hosts" filters={filters}
                                            setFilters={applyFilters} taps={selectedTaps}
                                            timeRange={timeRange} revision={revision} />
            </div>
          </div>
        </div>
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Top Sources" />

              <IcmpTunnelBreakdownHistogram by="SOURCE" columnTitle="Source" valueTitle="Peers" filters={filters}
                                            setFilters={applyFilters} taps={selectedTaps}
                                            timeRange={timeRange} revision={revision} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Suspected ICMP Tunnels"
                                     refreshAction={() => setRevision(new Date())} />

              {table()}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
