import React, {useContext, useEffect, useState} from "react";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {ASSETS_MENU_ITEMS} from "../AssetsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import {Presets} from "../../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import DHCPTransactionsTable from "./DHCPTransactionsTable";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import {DHCP_FILTER_FIELDS} from "./DHCPFilterFields";
import {useLocation} from "react-router-dom";
import Filters from "../../../shared/filtering/Filters";
import AssetsService from "../../../../services/ethernet/AssetsService";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import DHCPTransactionsChart from "./DHCPTransactionsChart";
import usePageTitle from "../../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../../shared/timerange/TimeRangeSelector";

const assetsService = new AssetsService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function DHCPTransactionsPage() {

  usePageTitle("DHCP Transactions");

  const [organizationId, tenantId] = useSelectedTenant();

  const urlQuery = useQuery();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24))

  const [data, setData] = useState(null);

  const [statistics, setStatistics] = useState(null);

  const [orderColumn, setOrderColumn] = useState("initiated_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const perPage = 25;
  const [page, setPage] = useState(1);

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useState(
      queryParametersToFilters(urlQuery.get("filters"), DHCP_FILTER_FIELDS)
  );

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setData(null);
    setStatistics(null);

    assetsService.getDHCPStatistics(timeRange, filters, selectedTaps, setStatistics);

    assetsService.findAllDHCPTransactions(
        organizationId,
        tenantId,
        timeRange,
        orderColumn,
        orderDirection,
        filters,
        selectedTaps,
        perPage,
        (page-1)*perPage,
        setData
    );
  }, [selectedTaps, organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, page, revision]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ASSETS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="DHCP Transactions Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={DHCP_FILTER_FIELDS} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Total DHCP Transactions"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <DHCPTransactionsChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="DHCP Transactions"
                                       refreshAction={() => setRevision(new Date())}
                                       helpLink="https://go.nzyme.org/ethernet-dhcp" />

                <DHCPTransactionsTable data={data}
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