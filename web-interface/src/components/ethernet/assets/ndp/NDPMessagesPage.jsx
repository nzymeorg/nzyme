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
import {NDP_FILTER_FIELDS} from "./NDPFilterFields";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import NDPMessagesTable from "./NDPMessagesTable";
import NDPMessagesChart from "./NDPMessagesChart";
import usePageTitle from "../../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import onSaveFiltersAsMonitor from "../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../misc/RenderConditionally";

const ndpService = new NdpService();

export default function NDPMessagesPage() {

  usePageTitle("NDP Messages");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);

  const [messages, setMessages] = useState(null);
  const [statistics, setStatistics] = useState(null);

  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const perPage = 25;
  const [page, setPage] = useState(1);

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useUrlFilters(NDP_FILTER_FIELDS);
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

    setMessages(null);
    setStatistics(null);

    ndpService.getStatistics(timeRange, filters, selectedTaps, setStatistics);

    ndpService.findAllMessages(
        organizationId,
        tenantId,
        timeRange,
        filters,
        orderColumn,
        orderDirection,
        selectedTaps,
        perPage,
        (page-1)*perPage,
        setMessages
    );
  }, [selectedTaps, filters, organizationId, tenantId, timeRange, orderColumn, orderDirection, page, revision, monitorsReady]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ASSETS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.ASSETS.NDP.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="NDP Message Filters"
                                       helpLink="https://go.nzyme.org/ethernet-ndp"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={NDP_FILTER_FIELDS}
                         monitorType="ETHERNET_NDP_MESSAGE"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_NDP_MESSAGE", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="NDP Messages"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <NDPMessagesChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Solicitations and Neighbor Advertisements"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <p className="help-text">Identical messages of one tap report are one row with a packet count.</p>

                  <NDPMessagesTable messages={messages}
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
        </RenderConditionally>
      </React.Fragment>
  )

}
