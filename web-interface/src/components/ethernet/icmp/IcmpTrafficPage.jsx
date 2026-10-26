import React, {useContext, useEffect, useState} from "react";
import {useLocation} from "react-router-dom";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import {Presets} from "../../shared/timerange/TimeRange";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeSelector";
import IcmpService from "../../../services/ethernet/IcmpService";
import {ICMP_MENU_ITEMS} from "./IcmpMenuItems";
import {ICMP_FILTER_FIELDS} from "./IcmpFilterFields";
import IcmpPacketsChart from "./IcmpPacketsChart";
import IcmpTopAddressesHistogram from "./IcmpTopAddressesHistogram";
import IcmpTypesHistogram from "./IcmpTypesHistogram";
import IcmpTrafficTable from "./IcmpTrafficTable";

const icmpService = new IcmpService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function IcmpTrafficPage() {

  usePageTitle("ICMP Traffic");

  const [organizationId, tenantId] = useSelectedTenant();
  const urlQuery = useQuery();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24));
  const [filters, setFilters] = useState(queryParametersToFilters(urlQuery.get("filters"), ICMP_FILTER_FIELDS));

  const [statistics, setStatistics] = useState(null);
  const [packets, setPackets] = useState(null);
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [page, setPage] = useState(1);

  const applyTimeRange = (newTimeRange) => {
    setPage(1);
    setTimeRange(newTimeRange);
  }
  const perPage = 50;
  const [revision, setRevision] = useState(new Date());

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
  }

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setStatistics(null);
    icmpService.getStatistics(timeRange, filters, selectedTaps, setStatistics);
  }, [organizationId, tenantId, selectedTaps, timeRange, filters, revision]);

  useEffect(() => {
    setPackets(null);
    icmpService.findAllPackets(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection,
      selectedTaps, perPage, (page-1)*perPage, setPackets);
  }, [organizationId, tenantId, selectedTaps, timeRange, filters, orderColumn, orderDirection, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ICMP_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.ICMP.OVERVIEW} />
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-icmp" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange} />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={ICMP_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Echo Traffic"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <IcmpPacketsChart statistics={statistics}
                                timeRange={timeRange}
                                setTimeRange={applyTimeRange}
                                series={{echo_requests: "Echo Requests", echo_replies: "Echo Replies"}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Error Messages"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <IcmpPacketsChart statistics={statistics}
                                timeRange={timeRange}
                                setTimeRange={applyTimeRange}
                                series={{unreachable: "Destination Unreachable", time_exceeded: "Time Exceeded", redirects: "Redirects"}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Message Types"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <IcmpTypesHistogram timeRange={timeRange} filters={filters} setFilters={applyFilters} revision={revision} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Top Sources"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <IcmpTopAddressesHistogram column="source"
                                        columnTitle="Source Address"
                                        filterField="source_address"
                                        timeRange={timeRange}
                                        filters={filters}
                                        setFilters={applyFilters}
                                        revision={revision} />
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Top Destinations"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <IcmpTopAddressesHistogram column="destination"
                                        columnTitle="Destination Address"
                                        filterField="destination_address"
                                        timeRange={timeRange}
                                        filters={filters}
                                        setFilters={applyFilters}
                                        revision={revision} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="ICMP Traffic"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Echo and other query traffic is aggregated per source, destination and message type within each
                tap report period, so one row can stand for many packets. Error messages are listed per packet
                with the packet that caused them.
              </p>

              <IcmpTrafficTable packets={packets}
                                page={page}
                                setPage={setPage}
                                perPage={perPage}
                                orderColumn={orderColumn}
                                setOrderColumn={(c) => { setPage(1); setOrderColumn(c); }}
                                orderDirection={orderDirection}
                                setOrderDirection={(d) => { setPage(1); setOrderDirection(d); }}
                                setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
