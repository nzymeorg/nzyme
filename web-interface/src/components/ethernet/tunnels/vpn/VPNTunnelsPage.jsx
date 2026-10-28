import React, {useContext, useEffect, useState} from "react";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {Presets} from "../../../shared/timerange/TimeRange";
import VPNTunnelsTable from "./VPNTunnelsTable";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {TapContext} from "../../../../App";
import {VPN_FILTER_FIELDS} from "./VPNFilterFields";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import Filters from "../../../shared/filtering/Filters";
import {TUNNELS_MENU_ITEMS} from "../TunnelsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import usePageTitle from "../../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import VPNTopServersHistogram from "./VPNTopServersHistogram";
import VPNTopClientsHistogram from "./VPNTopClientsHistogram";
import VPNActiveTunnelsHistogram from "./VPNActiveTunnelsHistogram";
import onSaveFiltersAsMonitor from "../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../misc/RenderConditionally";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import VpnService from "../../../../services/ethernet/VpnService";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";

const vpnService = new VpnService();

const EXCLUDE_TAILSCALE_FILTERS = {
  provider: [{field: "provider", operator: "not_equals", value: "Tailscale"}]
}

export default function VPNTunnelsPage() {

  usePageTitle("VPN Connections");

  const [organizationId, tenantId] = useSelectedTenant();

  const [displaySettings, setDisplaySettings] = useState(null);

  useEffect(() => {
    setDisplaySettings(null);
    vpnService.getDisplaySettings(organizationId, tenantId, setDisplaySettings);
  }, [organizationId, tenantId]);

  if (!displaySettings) {
    return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={TUNNELS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12"><LoadingSpinner /></div>
        </div>
      </React.Fragment>
    )
  }

  // The default filters apply unless the URL carries its own set, which is also how a cleared filter set is kept.
  const defaultFilters = displaySettings.exclude_tailscale_by_default
    ? queryParametersToFilters(JSON.stringify(EXCLUDE_TAILSCALE_FILTERS), VPN_FILTER_FIELDS)
    : null;

  return <VPNTunnelsPageContent organizationId={organizationId}
                                tenantId={tenantId}
                                defaultFilters={defaultFilters} />
}

function VPNTunnelsPageContent({organizationId, tenantId, defaultFilters}) {

  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [filters, setFilters] = useUrlFilters(VPN_FILTER_FIELDS, defaultFilters);
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
                            activeRoute={ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       helpLink="https://go.nzyme.org/ethernet-vpn"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={VPN_FILTER_FIELDS}
                         monitorType="ETHERNET_VPN_TUNNEL"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_VPN_TUNNEL", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Active VPN Connections"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <VPNActiveTunnelsHistogram timeRange={timeRange}
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

                  <VPNTopServersHistogram timeRange={timeRange}
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

                  <VPNTopClientsHistogram timeRange={timeRange}
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
                  <CardTitleWithControls title="All VPN Connections"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <VPNTunnelsTable timeRange={timeRange}
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
