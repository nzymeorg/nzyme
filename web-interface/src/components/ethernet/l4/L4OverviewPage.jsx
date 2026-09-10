import React, {useContext, useEffect, useState} from "react";
import {Presets} from "../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import {useLocation} from "react-router-dom";
import L4SessionsTable from "./L4SessionsTable";
import {L4_SESSIONS_FILTER_FIELDS} from "./L4SessionFilterFields";
import L4Service from "../../../services/ethernet/L4Service";
import {TapContext} from "../../../App";
import L4SessionsTotalBytesChart from "./L4SessionsTotalBytesChart";
import L4SessionsTotalSessionsChart from "./L4SessionsTotalSessionsChart";
import L4SessionsInternalSessionsChart from "./L4SessionsInternallSessionsChart";
import L4SessionsInternalBytesChart from "./L4SessionsInternalBytesChart";
import L4SessionsNumbers from "./L4SessionsNumbers";
import L4SessionsLeastCommonNonEphemeralDestinationPortsHistogram from "./L4SessionsLeastCommonNonEphemeralDestinationPortsHistogram";
import L4SessionsTopTrafficSourceMacsHistogram from "./L4SessionsTopTrafficSourceMacsHistogram";
import L4SessionsTopTrafficDestinationMacsHistogram from "./L4SessionsTopTrafficDestinationMacsHistogram";
import L4SessionsTopDestinationPortsHistogram from "./L4SessionsTopDestinationPortsHistogram";
import L4SessionsTopTrafficSourceAddressesHistogram from "./L4SessionsTopTrafficSourceAddressesHistogram";
import L4SessionsTopTrafficDestinationAddressesHistogram from "./L4SessionsTopTrafficDestinationAddressesHistogram";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeSelector";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

const l4Service = new L4Service();

export default function L4OverviewPage() {

  usePageTitle("TCP/UDP Overview");

  const urlQuery = useQuery();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [statistics, setStatistics] = useState(null);

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24));
  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useState(
      queryParametersToFilters(urlQuery.get("filters"), L4_SESSIONS_FILTER_FIELDS)
  );

  useEffect(() => {
    setStatistics(null);
    l4Service.getSessionsStatistics(timeRange, selectedTaps, setStatistics);
  }, [selectedTaps, timeRange, revision, filters]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <h1>TCP/UDP</h1>
          </div>
        </div>

        <L4SessionsNumbers statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} filters={filters} />

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Bytes Transferred"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTotalBytesChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} filters={filters} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Internal Bytes Transferred"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsInternalBytesChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} filters={filters} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Sessions/Conversations"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTotalSessionsChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} filters={filters} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Internal Sessions/Conversations"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsInternalSessionsChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} filters={filters} />
              </div>
            </div>
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
                         fields={L4_SESSIONS_FILTER_FIELDS} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Traffic Source MACs/Assets"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTopTrafficSourceMacsHistogram filters={filters}
                                                         setFilters={setFilters}
                                                         timeRange={timeRange}
                                                         revision={revision} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Traffic Destination MACs/Assets"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTopTrafficDestinationMacsHistogram filters={filters}
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
      </React.Fragment>
  )

}