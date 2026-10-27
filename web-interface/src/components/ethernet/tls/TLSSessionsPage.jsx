import React, {useContext, useEffect, useState} from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import {TapContext} from "../../../App";
import Filters from "../../shared/filtering/Filters";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import ApiRoutes from "../../../util/ApiRoutes";
import SectionMenuBar from "../../shared/SectionMenuBar";
import usePageTitle from "../../../util/UsePageTitle";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import {TLS_MENU_ITEMS} from "./TLSMenuItems";
import {TLS_SESSION_FILTER_FIELDS} from "./TLSSessionFilterFields";
import TLSSessionsTable from "./TLSSessionsTable";
import TLSActiveSessionsHistogram from "./TLSActiveSessionsHistogram";
import TLSTopHistogram from "./TLSTopHistogram";
import onSaveFiltersAsMonitor from "../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../misc/RenderConditionally";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";


export default function TLSSessionsPage() {

  usePageTitle("TLS Sessions");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [filters, setFilters] = useUrlFilters(TLS_SESSION_FILTER_FIELDS);
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
            <SectionMenuBar items={TLS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       helpLink="https://go.nzyme.org/ethernet-tls"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={TLS_SESSION_FILTER_FIELDS}
                         monitorType="ETHERNET_TLS_SESSION"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_TLS_SESSION", organizationId, tenantId)} />
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

                  <TLSActiveSessionsHistogram timeRange={timeRange}
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
                  <CardTitleWithControls title="Top Server Names"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSTopHistogram type="server_names"
                                   timeRange={timeRange}
                                   filters={filters}
                                   setFilters={setFilters}
                                   revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Server Ports"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSTopHistogram type="server_ports"
                                   timeRange={timeRange}
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
                  <CardTitleWithControls title="Top Clients"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSTopHistogram type="clients"
                                   timeRange={timeRange}
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

                  <TLSTopHistogram type="servers"
                                   timeRange={timeRange}
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
                  <CardTitleWithControls title="Negotiated Versions"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSTopHistogram type="versions"
                                   timeRange={timeRange}
                                   filters={filters}
                                   setFilters={setFilters}
                                   revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Client Fingerprints (JA4)"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())} />

                  <TLSTopHistogram type="ja4"
                                   timeRange={timeRange}
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

                  <TLSSessionsTable timeRange={timeRange}
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
