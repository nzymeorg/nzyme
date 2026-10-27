import React, {useContext, useEffect, useState} from "react";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {Presets} from "../../../shared/timerange/TimeRange";
import SSHSessionsTable from "./SSHSessionsTable";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {TapContext} from "../../../../App";
import {SSH_FILTER_FIELDS} from "./SSHFilterFields";
import Filters from "../../../shared/filtering/Filters";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import {REMOTE_ACCESS_MENU_ITEMS} from "../RemoteAccessMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import usePageTitle from "../../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import SSHActiveSessionsHistogram from "./SSHActiveSessionsHistogram";
import RTSPTopServersHistogram from "../../streams/rtsp/RTSPTopServersHistogram";
import RTSPTopClientsHistogram from "../../streams/rtsp/RTSPTopClientsHistogram";
import SSHTopClientsHistogram from "./SSHTopClientsHistogram";
import SSHTopServersHistogram from "./SSHTopServersHistogram";
import SSHTopClientTypesHistogram from "./SSHTopClientTypesHistogram";
import SSHTopServerTypesHistogram from "./SSHTopServerTypesHistogram";
import onSaveFiltersAsMonitor from "../../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../../misc/RenderConditionally";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";


export default function SSHSessionsPage() {

  usePageTitle("SSH Sessions");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [filters, setFilters] = useUrlFilters(SSH_FILTER_FIELDS);
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
            <SectionMenuBar items={REMOTE_ACCESS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.REMOTE.SSH.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       helpLink="https://go.nzyme.org/ethernet-ssh"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={SSH_FILTER_FIELDS}
                         monitorType="ETHERNET_SSH_SESSION"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_SSH_SESSION", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Active Sessions"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <SSHActiveSessionsHistogram timeRange={timeRange}
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

                  <SSHTopServersHistogram timeRange={timeRange}
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

                  <SSHTopClientsHistogram timeRange={timeRange}
                                          setTimeRange={setTimeRange}
                                          filters={filters}
                                          setFilters={setFilters}
                                          revision={revision} />

                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Server Types"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <SSHTopServerTypesHistogram timeRange={timeRange}
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
                  <CardTitleWithControls title="Top Client Types"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <SSHTopClientTypesHistogram timeRange={timeRange}
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
                  <CardTitleWithControls title="All Sessions"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <SSHSessionsTable timeRange={timeRange}
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