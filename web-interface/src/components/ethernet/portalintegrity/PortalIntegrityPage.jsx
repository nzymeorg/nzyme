import React, {useContext, useEffect, useState} from "react";
import usePageTitle from "../../../util/UsePageTitle";
import {TapContext} from "../../../App";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import {Presets} from "../../shared/timerange/TimeRange";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import {PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS} from "./PortalIntegrityReportsFilterFields";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import PortalIntegrityReportsTable from "./PortalIntegrityReportsTable";
import onSaveFiltersAsMonitor from "../../shared/filtering/monitors/SaveMonitorCallback";
import RenderConditionally from "../../misc/RenderConditionally";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";

export default function PortalIntegrityPage() {

  usePageTitle("Portal Integrity Reports");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [filters, setFilters] = useUrlFilters(PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

  const [histogram, setHistogram] = useState(null);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  return (
    <>
      <div className="row">
        <div className="col-md-12">
          <h1>Portal Integrity Reports</h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     helpLink="https://go.nzyme.org/portal-integrity"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS}
                       monitorType="ETHERNET_PORTAL_INTEGRITY_REPORT"
                       onMonitorsReady={() => setMonitorsReady(true)}
                       onSaveAsMonitor={onSaveFiltersAsMonitor("ETHERNET_PORTAL_INTEGRITY_REPORT", organizationId, tenantId)} />
            </div>
          </div>
        </div>
      </div>

      <RenderConditionally render={monitorsReady}>
        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Reports"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <PortalIntegrityReportsTable timeRange={timeRange}
                                             filters={filters}
                                             setFilters={setFilters}
                                             revision={revision} />
              </div>
            </div>
          </div>
        </div>
      </RenderConditionally>
    </>
  )

}