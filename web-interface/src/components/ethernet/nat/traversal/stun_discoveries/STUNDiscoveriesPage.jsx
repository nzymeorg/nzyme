import React, {useContext, useEffect, useState} from "react";
import usePageTitle from "../../../../../util/UsePageTitle";
import {TapContext} from "../../../../../App";
import {useUrlTimeRange} from "../../../../shared/timerange/TimeRangeSelector";
import {Presets} from "../../../../shared/timerange/TimeRange";
import useUrlFilters from "../../../../shared/filtering/useUrlFilters";
import {disableTapSelector, enableTapSelector} from "../../../../misc/TapSelector";
import {STUN_DISCOVERY_FILTER_FIELDS} from "./STUNDiscoveriesFilterFields";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import Filters from "../../../../shared/filtering/Filters";
import {NAT_MENU_ITEMS} from "../../NATMenuItems";
import STUNDiscoveriesTable from "./STUNDiscoveriesTable";
import NATService from "../../../../../services/ethernet/NATService";
import STUNDiscoveriesHistogram from "./STUNDiscoveriesHistogram";
import STUNDiscoveriesTopClientsHistogram from "./STUNDiscoveriesTopClientsHistogram";
import STUNDiscoveriesTopServersHistogram from "./STUNDiscoveriesTopServersHistogram";
import onSaveFiltersAsMonitor from "../../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../../misc/RenderConditionally";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";


const natService = new NATService();

export default function STUNDiscoveriesPage() {

  usePageTitle("STUN Discoveries");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [filters, setFilters] = useUrlFilters(STUN_DISCOVERY_FILTER_FIELDS);
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
    if (!monitorsReady) {
      return;
    }

    setHistogram(null);

    natService.getSTUNDiscoveriesHistogram(timeRange, filters, selectedTaps, setHistogram);
  }, [selectedTaps, timeRange, filters, revision, monitorsReady]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={NAT_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_DISCOVERY.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     helpLink="https://go.nzyme.org/stun-discoveries"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={STUN_DISCOVERY_FILTER_FIELDS}
                       monitorType="ETHERNET_STUN_DISCOVERY"
                       onMonitorsReady={() => setMonitorsReady(true)}
                       onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_STUN_DISCOVERY", organizationId, tenantId)} />
            </div>
          </div>
        </div>
      </div>

      <RenderConditionally render={monitorsReady}>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Clients"
                                       fixedTimeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}/>

                <STUNDiscoveriesTopClientsHistogram filters={filters}
                                                    setFilters={setFilters}
                                                    timeRange={timeRange}
                                                    revision={revision} />
              </div>
            </div>
          </div>
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Servers"
                                       fixedTimeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())}/>

                <STUNDiscoveriesTopServersHistogram filters={filters}
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
                <CardTitleWithControls title="All Discoveries"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <STUNDiscoveriesHistogram data={histogram} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Discoveries"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <STUNDiscoveriesTable timeRange={timeRange}
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