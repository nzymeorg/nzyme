import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {ASSETS_MENU_ITEMS} from "../AssetsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import {Presets} from "../../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import DHCPTransactionsTable from "./DHCPTransactionsTable";
import DHCPFlakyFingerprintAssetsTable from "./DHCPFlakyFingerprintAssetsTable";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import {DHCP_FILTER_FIELDS} from "./DHCPFilterFields";
import Filters from "../../../shared/filtering/Filters";
import AssetsService from "../../../../services/ethernet/AssetsService";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import DHCPTransactionsChart from "./DHCPTransactionsChart";
import usePageTitle from "../../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import onSaveFiltersAsMonitor from "../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../misc/RenderConditionally";

const assetsService = new AssetsService();


export default function DHCPTransactionsPage() {

  usePageTitle("DHCP Transactions");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)

  const [data, setData] = useState(null);

  const [statistics, setStatistics] = useState(null);

  const [orderColumn, setOrderColumn] = useState("initiated_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const perPage = 25;
  const [page, setPage] = useState(1);

  const [flakyAssets, setFlakyAssets] = useState(null);
  const flakyPerPage = 10;
  const [flakyPage, setFlakyPage] = useState(1);

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useUrlFilters(DHCP_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    if (!monitorsReady) {
      return;
    }

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
  }, [selectedTaps, organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, page, revision, monitorsReady]);

  useEffect(() => {
    setFlakyAssets(null);

    assetsService.findDHCPFlakyFingerprintAssets(
        organizationId,
        tenantId,
        timeRange,
        selectedTaps,
        flakyPerPage,
        (flakyPage-1)*flakyPerPage,
        setFlakyAssets
    );
  }, [selectedTaps, organizationId, tenantId, timeRange, flakyPage, revision]);

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
                         fields={DHCP_FILTER_FIELDS}
                         monitorType="ETHERNET_DHCP_TRANSACTION"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_DHCP_TRANSACTION", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
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
        </RenderConditionally>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Assets with Flaky DHCP Fingerprints"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <p className="text-muted">
                  Assets that presented more than one DHCP fingerprint for the same transaction type during the
                  selected time range. Each change triggers a <em>new DHCP fingerprint</em> alert, so these assets
                  are candidates for investigation or for an{' '}
                  <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.INDEX}>exclusion</Link> from that alert type.
                  Filters do not apply to this table.
                </p>

                <DHCPFlakyFingerprintAssetsTable data={flakyAssets}
                                                 page={flakyPage}
                                                 setPage={setFlakyPage}
                                                 perPage={flakyPerPage}
                                                 setFilters={setFilters} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}