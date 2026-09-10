import React, {useContext, useEffect, useState} from 'react';
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {Presets} from "../../../shared/timerange/TimeRange";
import AssetsService from "../../../../services/ethernet/AssetsService";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {ASSETS_MENU_ITEMS} from "../AssetsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import {ARP_FILTER_FIELDS} from "./ARPFilterFields";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import {useLocation} from "react-router-dom";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ARPPacketsTable from "./ARPPacketsTable";
import ARPPacketsChart from "./ARPPacketsChart";
import ARPRequestToReplyRatioChart from "./ARPRequestToReplyRatioChart";
import ARPGratuitousPacketsChart from "./ARPGratuitousPacketsChart";
import ARPRequesterPairsHistogram from "./ARPRequesterPairsHistogram";
import ARPResponderPairsHistogram from "./ARPResponderPairsHistogram";
import usePageTitle from "../../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../../shared/timerange/TimeRangeSelector";

const assetsService = new AssetsService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function ARPPacketsPage() {

  usePageTitle("ARP Packets");

  const [organizationId, tenantId] = useSelectedTenant();

  const urlQuery = useQuery();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24));

  const [packets, setPackets] = useState(null);
  const [statistics, setStatistics] = useState(null);

  const [orderColumn, setOrderColumn] = useState("timestamp");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const perPage = 25;
  const [page, setPage] = useState(1);

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useState(
      queryParametersToFilters(urlQuery.get("filters"), ARP_FILTER_FIELDS)
  );

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setPackets(null);
    setStatistics(null);

    assetsService.getArpStatistics(timeRange, filters, selectedTaps, setStatistics);

    assetsService.findAllArpPackets(
        organizationId,
        tenantId,
        timeRange,
        filters,
        orderColumn,
        orderDirection,
        selectedTaps,
        perPage,
        (page-1)*perPage,
        setPackets
    );
  }, [selectedTaps, filters, organizationId, tenantId, timeRange, orderColumn, orderDirection, page, revision]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ASSETS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.ASSETS.ARP.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="ARP Packets Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       helpLink="https://go.nzyme.org/ethernet-arp" />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={ARP_FILTER_FIELDS} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Total ARP Packets"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <ARPPacketsChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="ARP Request to Reply Ratio"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <ARPRequestToReplyRatioChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Gratuitous ARP Packets"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <ARPGratuitousPacketsChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="ARP Requesters"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <ARPRequesterPairsHistogram organizationId={organizationId}
                                            tenantId={tenantId}
                                            timeRange={timeRange}
                                            filters={filters}
                                            revision={revision} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="ARP Responders"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <ARPResponderPairsHistogram organizationId={organizationId}
                                            tenantId={tenantId}
                                            timeRange={timeRange}
                                            filters={filters}
                                            revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="ARP Packets"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <ARPPacketsTable packets={packets}
                                 timeRange={timeRange}
                                 page={page}
                                 setPage={setPage}
                                 perPage={perPage}
                                 orderColumn={orderColumn}
                                 orderDirection={orderDirection}
                                 setOrderColumn={setOrderColumn}
                                 setOrderDirection={setOrderDirection}
                                 setFilters={setFilters} />

              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}