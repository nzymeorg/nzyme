import React, {useContext, useEffect, useState} from "react";
import {Link, useLocation} from "react-router-dom";
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
import L4AddressList from "../shared/L4AddressList";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import Filters from "../../shared/filtering/Filters";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import {ICMP_REDIRECTS_FILTER_FIELDS} from "./IcmpRedirectsFilterFields";
import {Presets} from "../../shared/timerange/TimeRange";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";

const icmpService = new IcmpService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function IcmpRedirectsPage() {

  usePageTitle("ICMP Redirects");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;
  const urlQuery = useQuery();

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), ICMP_REDIRECTS_FILTER_FIELDS)
  );
  const [redirects, setRedirects] = useState(null);
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [page, setPage] = useState(1);
  const perPage = 25;
  const [revision, setRevision] = useState(new Date());
  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);

  useEffect(() => {
    enableTapSelector(tapContext);
    return () => { disableTapSelector(tapContext); }
  }, [tapContext]);

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
  }

  useEffect(() => {
    setRedirects(null);
    icmpService.findAllRedirects(organizationId, tenantId, filters, selectedTaps, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setRedirects);
  }, [organizationId, tenantId, filters, selectedTaps, timeRange, orderColumn, orderDirection, page, revision]);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={(c) => { setPage(1); setOrderColumn(c); }}
                          orderDirection={orderDirection}
                          setOrderDirection={(d) => { setPage(1); setOrderDirection(d); }} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={applyFilters} fields={ICMP_REDIRECTS_FILTER_FIELDS} field={field} value={value} />
  }

  const address = (data, plain, field) => {
    return data
      ? <L4Address address={data} hidePort withAssetName filterElement={filterIcon(field, plain)} />
      : <React.Fragment><span className="machine-data">{plain}</span> {filterIcon(field, plain)}</React.Fragment>
  }

  const table = () => {
    if (!redirects) {
      return <GenericWidgetLoadingSpinner height={150} />
    }

    if (redirects.redirects.length === 0) {
      return <div className="alert alert-info mb-0">No ICMP redirects in the selected time range.</div>
    }

    return (
      <React.Fragment>
        <p className="mb-2 mt-0"><strong>Total:</strong> {numeral(redirects.total).format("0,0")}</p>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>ID</th>
            <th>Sender {columnSorting("sender_address")}</th>
            <th>Sender MAC</th>
            <th>Told Host {columnSorting("target_address")}</th>
            <th>To Use Gateway {columnSorting("gateway_address")}</th>
            <th>For Destinations</th>
            <th>Packets {columnSorting("packet_count")}</th>
            <th>First Seen {columnSorting("first_seen")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
          </tr>
          </thead>
          <tbody>
          {redirects.redirects.map((r) => {
            return (
              <tr key={r.uuid}>
                <td><Link to={ApiRoutes.ETHERNET.ICMP.REDIRECTS.DETAILS(r.uuid)}><FullCopyShortenedId value={r.uuid} /></Link> {filterIcon("uuid", r.uuid)}</td>
                <td>{address(r.sender_address_data, r.sender_address, "sender_address")}</td>
                <td>{r.sender_mac
                  ? <React.Fragment><EthernetMacAddress addressWithContext={r.sender_mac} withAssetLink withAssetName /> {filterIcon("sender_mac", r.sender_mac.address)}</React.Fragment>
                  : <span className="text-muted">n/a</span>}</td>
                <td>{address(r.target_address_data, r.target_address, "target_address")}</td>
                <td>{address(r.gateway_address_data, r.gateway_address, "gateway_address")}</td>
                <td>
                  <L4AddressList addresses={r.redirected_destinations} count={5} asList />
                </td>
                <td>{numeral(r.packet_count).format("0,0")}</td>
                <td title={moment(r.first_seen).format()}>{moment(r.first_seen).fromNow()}</td>
                <td title={moment(r.last_seen).format()}>{moment(r.last_seen).fromNow()}</td>
              </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={redirects.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ICMP_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-icmp-redirects" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     doNotPersistTimeRange={true} />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={ICMP_REDIRECTS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Detected ICMP Redirects"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                An ICMP redirect tells a host to send traffic for a destination through a different gateway. On
                most networks nothing sends these legitimately.  A legitimate case is a
                segment with two routers where one points hosts at the other.
              </p>

              {table()}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
