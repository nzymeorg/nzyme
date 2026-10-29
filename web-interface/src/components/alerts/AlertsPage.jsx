import React, {useState} from "react";
import AlertsTable from "./AlertsTable";

import NumberCard from "../widgets/presentation/NumberCard";
import usePageTitle from "../../util/UsePageTitle";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import Filters from "../shared/filtering/Filters";
import useUrlFilters from "../shared/filtering/useUrlFilters";
import {useUrlTimeRange} from "../shared/timerange/TimeRangeSelector";
import {Presets} from "../shared/timerange/TimeRange";
import {ALERT_FILTER_FIELDS} from "./AlertFilterFields";
import AlertsHistogram from "./AlertsHistogram";
import AlertsTopHistogram from "./AlertsTopHistogram";

function AlertsPage() {

  usePageTitle("Alerts");

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_DAYS_7);
  const [filters, setFilters] = useUrlFilters(ALERT_FILTER_FIELDS);

  const [totals, setTotals] = useState(null);
  const [revision, setRevision] = useState(new Date());

  const refresh = () => setRevision(new Date());

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-10">
            <h1>Alerts</h1>
          </div>

          <div className="col-2">
            <a href="https://go.nzyme.org/detection-alerts" className="btn btn-secondary float-end">Help</a>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={refresh} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={ALERT_FILTER_FIELDS}
                         onMonitorsReady={() => {}} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <NumberCard title="Active Alerts" value={totals ? totals.total_active : null} numberFormat="0,0"
                        fixedAppliedTimeRange={timeRange} />
          </div>

          <div className="col-6">
            <NumberCard title="Total Alerts" value={totals ? totals.total : null} numberFormat="0,0"
                        fixedAppliedTimeRange={timeRange} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Active Alerts Over Time"
                                       timeRange={timeRange}
                                       refreshAction={refresh} />

                <AlertsHistogram type="active"
                                 timeRange={timeRange}
                                 setTimeRange={setTimeRange}
                                 filters={filters}
                                 revision={revision} />
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="New Alerts Over Time"
                                       timeRange={timeRange}
                                       refreshAction={refresh} />

                <AlertsHistogram type="new"
                                 timeRange={timeRange}
                                 setTimeRange={setTimeRange}
                                 filters={filters}
                                 revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Alert Types"
                                       timeRange={timeRange}
                                       refreshAction={refresh} />

                <AlertsTopHistogram aggregation="types"
                                    columnTitle="Detection Type"
                                    filterField="type"
                                    timeRange={timeRange}
                                    filters={filters}
                                    setFilters={setFilters}
                                    revision={revision} />
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Subsystems"
                                       timeRange={timeRange}
                                       refreshAction={refresh} />

                <AlertsTopHistogram aggregation="subsystems"
                                    columnTitle="Subsystem"
                                    filterField="subsystem"
                                    timeRange={timeRange}
                                    filters={filters}
                                    setFilters={setFilters}
                                    revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Affected IP Addresses"
                                       timeRange={timeRange}
                                       refreshAction={refresh} />

                <AlertsTopHistogram aggregation="affected/addresses"
                                    columnTitle="IP Address"
                                    filterField="affected_address"
                                    valueSubField="address"
                                    timeRange={timeRange}
                                    filters={filters}
                                    setFilters={setFilters}
                                    revision={revision} />
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Affected Assets"
                                       timeRange={timeRange}
                                       refreshAction={refresh} />

                <AlertsTopHistogram aggregation="affected/macs"
                                    columnTitle="MAC Address"
                                    filterField="affected_mac"
                                    timeRange={timeRange}
                                    filters={filters}
                                    setFilters={setFilters}
                                    revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Alerts"
                                       timeRange={timeRange}
                                       refreshAction={refresh} />

                <AlertsTable timeRange={timeRange}
                             filters={filters}
                             setFilters={setFilters}
                             revision={revision}
                             onLoaded={setTotals} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default AlertsPage;
