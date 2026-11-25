import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {Presets} from "../../../shared/timerange/TimeRange";
import NdpService from "../../../../services/ethernet/NdpService";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {ASSETS_MENU_ITEMS} from "../AssetsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import {NDP_ROUTER_ADVERTISEMENT_FILTER_FIELDS} from "./NDPFilterFields";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import NDPRouterAdvertisementsTable from "./NDPRouterAdvertisementsTable";
import usePageTitle from "../../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";

const ndpService = new NdpService();

export default function NDPRouterAdvertisementsPage() {

  usePageTitle("NDP Router Advertisements");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);

  const [routerAdvertisements, setRouterAdvertisements] = useState(null);

  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const perPage = 25;
  const [page, setPage] = useState(1);

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useUrlFilters(NDP_ROUTER_ADVERTISEMENT_FILTER_FIELDS);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setRouterAdvertisements(null);

    ndpService.findAllRouterAdvertisements(
        organizationId,
        tenantId,
        timeRange,
        filters,
        orderColumn,
        orderDirection,
        selectedTaps,
        perPage,
        (page-1)*perPage,
        setRouterAdvertisements
    );
  }, [selectedTaps, filters, organizationId, tenantId, timeRange, orderColumn, orderDirection, page, revision]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ASSETS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.ASSETS.NDP.ROUTER_ADVERTISEMENTS} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Router Advertisement Filters"
                                       helpLink="https://go.nzyme.org/ethernet-ndp-router-advertisements"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={NDP_ROUTER_ADVERTISEMENT_FILTER_FIELDS} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Router Advertisements"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <p className="help-text">
                  Identical advertisements of one tap report are one row with a packet count. An advertisement is
                  recorded with up to 32 prefixes, 32 routes, 16 DNS servers and 16 search domains.
                </p>

                <NDPRouterAdvertisementsTable routerAdvertisements={routerAdvertisements}
                                              page={page}
                                              setPage={setPage}
                                              perPage={perPage}
                                              setFilters={setFilters}
                                              orderColumn={orderColumn}
                                              setOrderColumn={setOrderColumn}
                                              orderDirection={orderDirection}
                                              setOrderDirection={setOrderDirection} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
