import React, {useContext, useEffect, useState} from "react";
import {Presets} from "../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import L4SessionsTable from "./L4SessionsTable";
import {L4_SESSIONS_FILTER_FIELDS, L4_SESSIONS_QUICK_FILTERS} from "./L4SessionFilterFields";
import L4SessionsLeastCommonNonEphemeralDestinationPortsHistogram from "./L4SessionsLeastCommonNonEphemeralDestinationPortsHistogram";
import L4SessionsTopDestinationPortsHistogram from "./L4SessionsTopDestinationPortsHistogram";
import L4SessionsTopTrafficSourceAddressesHistogram from "./L4SessionsTopTrafficSourceAddressesHistogram";
import L4SessionsTopTrafficDestinationAddressesHistogram from "./L4SessionsTopTrafficDestinationAddressesHistogram";
import usePageTitle from "../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import {L4_MENU_ITEMS} from "./L4MenuItems";
import onSaveFiltersAsMonitor from "../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../misc/RenderConditionally";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {TapContext} from "../../../App";
import L4Service from "../../../services/ethernet/L4Service";
import L4SessionsTimelineSessionsChart from "./L4SessionsTimelineSessionsChart";
import L4SessionsTimelineBytesChart from "./L4SessionsTimelineBytesChart";
import L4SessionsDurationHistogram from "./L4SessionsDurationHistogram";
import L4SessionsSourceFanOutHistogram from "./L4SessionsSourceFanOutHistogram";

const l4Service = new L4Service();


export default function L4SessionsPage() {

  usePageTitle("TCP/UDP Sessions");

  const [organizationId, tenantId] = useSelectedTenant();

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useUrlFilters(L4_SESSIONS_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  // One request feeds both timeline charts.
  const [timeline, setTimeline] = useState(null);

  useEffect(() => {
    if (!monitorsReady) {
      return;
    }

    setTimeline(null);
    l4Service.getSessionsTimeline(selectedTaps, filters, timeRange, setTimeline);
  }, [monitorsReady, selectedTaps, filters, timeRange, revision]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={L4_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.L4.SESSIONS} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Session Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={L4_SESSIONS_FILTER_FIELDS}
                         quickFilters={L4_SESSIONS_QUICK_FILTERS}
                         monitorType="ETHERNET_L4_SESSION"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_L4_SESSION", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Sessions"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <p className="help-text">Placed at the time of last activity.</p>

                  <L4SessionsTimelineSessionsChart timeline={timeline}
                                                   timeRange={timeRange}
                                                   setTimeRange={setTimeRange} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Traffic"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <p className="help-text">Placed at the time of last activity.</p>

                  <L4SessionsTimelineBytesChart timeline={timeline}
                                                timeRange={timeRange}
                                                setTimeRange={setTimeRange} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Traffic Source Addresses"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <L4SessionsTopTrafficSourceAddressesHistogram filters={filters}
                                                                setFilters={setFilters}
                                                                timeRange={timeRange}
                                                                revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Traffic Destination Addresses"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <L4SessionsTopTrafficDestinationAddressesHistogram filters={filters}
                                                                     setFilters={setFilters}
                                                                     timeRange={timeRange}
                                                                     revision={revision} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Destination Ports"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <p className="help-text">Sorted by traffic bytes.</p>

                  <L4SessionsTopDestinationPortsHistogram filters={filters}
                                                          setFilters={setFilters}
                                                          timeRange={timeRange}
                                                          revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Least Common Non-Ephemeral Destination Ports"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <p className="help-text">Sorted by session count.</p>

                  <L4SessionsLeastCommonNonEphemeralDestinationPortsHistogram filters={filters}
                                                                              setFilters={setFilters}
                                                                              timeRange={timeRange}
                                                                              revision={revision} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Session Durations"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <p className="help-text">The filter icon pre-fills the bucket boundary.</p>

                  <L4SessionsDurationHistogram filters={filters}
                                               setFilters={setFilters}
                                               timeRange={timeRange}
                                               revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Source Fan-Out"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <p className="help-text">Distinct destination addresses and ports per source address.</p>

                  <L4SessionsSourceFanOutHistogram filters={filters}
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
                  <CardTitleWithControls title="Sessions"
                                         timeRange={timeRange}
                                         setTimeRange={setTimeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <L4SessionsTable filters={filters}
                                   timeRange={timeRange}
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