import React, {useEffect, useState} from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import {ASSETS_MENU_ITEMS} from "./AssetsMenuItems";
import AssetsService from "../../../services/ethernet/AssetsService";
import AssetsTable from "./AssetsTable";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import {ASSET_FILTER_FIELDS} from "./AssetFilterFields";
import Filters from "../../shared/filtering/Filters";
import ActiveAssetsHistogram from "./ActiveAssetsHistogram";
import LatestAssetsHistogram from "./LatestAssetsHistogram";
import DisappearedAssetsHistogram from "./DisappearedAssetsHistogram";
import usePageTitle from "../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import onSaveFiltersAsMonitor from "../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../misc/RenderConditionally";

const assetsService = new AssetsService();


export default function EthernetAssetsPage() {

  usePageTitle("Ethernet Assets");

  const [organizationId, tenantId] = useSelectedTenant();

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);

  const [assets, setAssets] = useState(null);
  const [page, setPage] = useState(1);
  const perPage = 25;

  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useUrlFilters(ASSET_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

  useEffect(() => {
    if (!monitorsReady) {
      return;
    }

    setAssets(null);
    if (organizationId && tenantId) {
      assetsService.findAllAssets(organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, perPage, (page-1)*perPage, setAssets);
    }
  }, [organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, page, revision, monitorsReady]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ASSETS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.ASSETS.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Active Assets"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <ActiveAssetsHistogram timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Asset Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={ASSET_FILTER_FIELDS}
                         monitorType="ETHERNET_ASSET"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_ASSET", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Newest Assets"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <LatestAssetsHistogram filters={filters}
                                         setFilters={setFilters}
                                         timeRange={timeRange}
                                         revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Recently Disappeared Assets"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <DisappearedAssetsHistogram filters={filters}
                                              setFilters={setFilters}
                                              timeRange={timeRange}
                                              revision={revision} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Assets"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <AssetsTable assets={assets}
                               setFilters={setFilters}
                               page={page}
                               setPage={setPage}
                               perPage={perPage}
                               orderColumn={orderColumn}
                               setOrderColumn={setOrderColumn}
                               orderDirection={orderDirection}
                               setOrderDirection={setOrderDirection} />
                </div>
              </div>
            </div>
          </div>
        </RenderConditionally>
      </React.Fragment>
  )


}