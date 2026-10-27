import React, {useContext, useEffect, useState} from "react";
import usePageTitle from "../../../../../util/UsePageTitle";
import {TapContext} from "../../../../../App";
import {useUrlTimeRange} from "../../../../shared/timerange/TimeRangeSelector";
import {Presets} from "../../../../shared/timerange/TimeRange";
import useUrlFilters from "../../../../shared/filtering/useUrlFilters";
import {disableTapSelector, enableTapSelector} from "../../../../misc/TapSelector";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import Filters from "../../../../shared/filtering/Filters";
import {NAT_MENU_ITEMS} from "../../NATMenuItems";
import {STUN_CONNECTIONS_FILTER_FIELDS} from "./STUNConnectionsFilterFields";
import STUNConnectionsTable from "./STUNConnectionsTable";
import STUNConnectionsTopClientsHistogram from "./STUNConnectionsTopClientsHistogram";
import STUNConnectionsTopServersHistogram from "./STUNConnectionsTopServersHistogram";
import STUNConnectionsActiveHistogram from "./STUNConnectionsActiveHistogram";
import onSaveFiltersAsMonitor from "../../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../../misc/RenderConditionally";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";


export default function STUNConnectionsPage() {

  usePageTitle("STUN Connections");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [filters, setFilters] = useUrlFilters(STUN_CONNECTIONS_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

  const [histogram, setHistogram] = useState(null);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setHistogram(null);

    //natService.getTraversalDiscoveriesHistogram(timeRange, filters, selectedTaps, setHistogram);
  }, [selectedTaps, timeRange, filters, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={NAT_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     helpLink="https://go.nzyme.org/stun-connections"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={STUN_CONNECTIONS_FILTER_FIELDS}
                       monitorType="ETHERNET_STUN_CONNECTION"
                       onMonitorsReady={() => setMonitorsReady(true)}
                       onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_STUN_CONNECTION", organizationId, tenantId)} />
            </div>
          </div>
        </div>
      </div>

      <RenderConditionally render={monitorsReady}>
        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Active Connections"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <STUNConnectionsActiveHistogram timeRange={timeRange}
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
                <CardTitleWithControls title="Top Clients"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <STUNConnectionsTopClientsHistogram timeRange={timeRange}
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
                <CardTitleWithControls title="Top Servers"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <STUNConnectionsTopServersHistogram timeRange={timeRange}
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
                <CardTitleWithControls title="All Connections"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <STUNConnectionsTable timeRange={timeRange}
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