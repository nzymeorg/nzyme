import React, {useContext, useEffect, useState} from "react";
import {RTSP_FILTER_FIELDS} from "./RTSPFilterFields";
import usePageTitle from "../../../../util/UsePageTitle";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import {Presets} from "../../../shared/timerange/TimeRange";
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import {STREAMS_MENU_ITEMS} from "../StreamsMenuItems";
import RTSPStreamsTable from "./RTSPStreamsTable";
import RTSPActiveStreamsHistogram from "./RTSPActiveStreamsHistogram";
import RTSPTopServersHistogram from "./RTSPTopServersHistogram";
import RTSPTopClientsHistogram from "./RTSPTopClientsHistogram";
import onSaveFiltersAsMonitor from "../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../misc/RenderConditionally";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";


export default function RTSPStreamsPage() {

  usePageTitle("RTSP Streams");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [filters, setFilters] = useUrlFilters(RTSP_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={STREAMS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={RTSP_FILTER_FIELDS}
                       monitorType="ETHERNET_RTSP_STREAM"
                       onMonitorsReady={() => setMonitorsReady(true)}
                       onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_RTSP_STREAM", organizationId, tenantId)} />
            </div>
          </div>
        </div>
      </div>

      <RenderConditionally render={monitorsReady}>
        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Active Streams"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <RTSPActiveStreamsHistogram timeRange={timeRange}
                                            setTimeRange={setTimeRange}
                                            filters={filters}
                                            revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Servers"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <RTSPTopServersHistogram timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         filters={filters}
                                         setFilters={setFilters}
                                         revision={revision} />

              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Clients"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <RTSPTopClientsHistogram timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         filters={filters}
                                         setFilters={setFilters}
                                         revision={revision} />

              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All RTSP Streams"
                                       helpLink="https://go.nzyme.org/ethernet-rtsp"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <RTSPStreamsTable timeRange={timeRange}
                                  filters={filters}
                                  setFilters={setFilters}
                                  revision={revision} />

              </div>
            </div>
          </div>
        </div>
      </RenderConditionally>
    </React.Fragment>
  )

}