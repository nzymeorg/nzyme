import React, {useContext, useEffect, useState} from 'react';
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import {TIME_MENU_ITEMS} from "../TimeMenuItems";
import {Presets} from "../../../shared/timerange/TimeRange";
import TimeService from "../../../../services/ethernet/TimeService";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import {NTP_FILTER_FIELDS} from "./NTPFilterFields";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import NTPTransactionsTable from "./NTPTransactionsTable";
import Filters from "../../../shared/filtering/Filters";
import NTPTransactionsHistogram from "./NTPTransactionsHistogram";
import NTPClientRequestResponseRatioHistogram from "./NTPClientRequestResponseRatioHistogram";
import NTPTopServersHistogram from "./NTPTopServersHistogram";
import usePageTitle from "../../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import onSaveFiltersAsMonitor from "../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../misc/RenderConditionally";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";

const timeService = new TimeService();


export function NTPOverviewPage() {

  usePageTitle("NTP Transactions");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)

  const [transactionsHistogram, setTransactionsHistogram] = useState(null);

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useUrlFilters(NTP_FILTER_FIELDS);
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

    setTransactionsHistogram(null);

    timeService.getNTPTransactionsHistogram(timeRange, filters, selectedTaps, setTransactionsHistogram);
  }, [selectedTaps, timeRange, filters, revision, monitorsReady]);

  return (
    <>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={TIME_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.TIME.NTP.INDEX}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={NTP_FILTER_FIELDS}
                       monitorType="ETHERNET_NTP_TRANSACTION"
                       onMonitorsReady={() => setMonitorsReady(true)}
                       onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_NTP_TRANSACTION", organizationId, tenantId)} />
            </div>
          </div>
        </div>
      </div>

      <RenderConditionally render={monitorsReady}>
        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Transactions"
                                       fixedTimeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}/>

                <NTPTransactionsHistogram histogram={transactionsHistogram} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Servers"
                                       fixedTimeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}/>

                <NTPTopServersHistogram filters={filters}
                                        setFilters={setFilters}
                                        timeRange={timeRange}
                                        revision={revision} />
              </div>
            </div>
          </div>
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Client Request/Response Ratio"
                                       fixedTimeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}/>

                <NTPClientRequestResponseRatioHistogram filters={filters}
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
                <CardTitleWithControls title="Transactions"
                                       fixedTimeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}/>

                <NTPTransactionsTable filters={filters}
                                      setFilters={setFilters}
                                      timeRange={timeRange}
                                      revision={revision} />
              </div>
            </div>
          </div>
        </div>
      </RenderConditionally>
    </>
  )

}