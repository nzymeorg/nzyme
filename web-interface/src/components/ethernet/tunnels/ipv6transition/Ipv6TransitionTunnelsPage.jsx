import React, {useContext, useEffect, useState} from "react";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {Presets} from "../../../shared/timerange/TimeRange";
import Ipv6TransitionTunnelsTable from "./Ipv6TransitionTunnelsTable";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {TapContext} from "../../../../App";
import {IPV6_TRANSITION_FILTER_FIELDS} from "./Ipv6TransitionFilterFields";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import Filters from "../../../shared/filtering/Filters";
import {TUNNELS_MENU_ITEMS} from "../TunnelsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import usePageTitle from "../../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import Ipv6TransitionTopServersHistogram from "./Ipv6TransitionTopServersHistogram";
import Ipv6TransitionTopClientsHistogram from "./Ipv6TransitionTopClientsHistogram";
import Ipv6TransitionActiveTunnelsHistogram from "./Ipv6TransitionActiveTunnelsHistogram";
import onSaveFiltersAsMonitor from "../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../misc/RenderConditionally";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";

export default function Ipv6TransitionTunnelsPage() {

  usePageTitle("IPv6 Transition Tunnels");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [filters, setFilters] = useUrlFilters(IPV6_TRANSITION_FILTER_FIELDS);
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
            <SectionMenuBar items={TUNNELS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.TUNNELS.IPV6_TRANSITION.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       helpLink="https://go.nzyme.org/ethernet-ipv6-transition"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={IPV6_TRANSITION_FILTER_FIELDS}
                         monitorType="ETHERNET_IPV6_TRANSITION_TUNNEL"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_IPV6_TRANSITION_TUNNEL", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Active IPv6 Transition Tunnels"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <Ipv6TransitionActiveTunnelsHistogram timeRange={timeRange}
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

                  <Ipv6TransitionTopServersHistogram timeRange={timeRange}
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

                  <Ipv6TransitionTopClientsHistogram timeRange={timeRange}
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
                  <CardTitleWithControls title="All IPv6 Transition Tunnels"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <Ipv6TransitionTunnelsTable timeRange={timeRange}
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
